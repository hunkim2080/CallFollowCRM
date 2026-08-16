// 시공막내 — 네이버 블로그 넣기 (service worker · chrome.debugger 자동엔진)
// 확장은 '진짜 키/클릭'을 content script 로는 못 냄 → chrome.debugger(CDP)로
// 사람과 동일한 신뢰된(trusted) Ctrl+V / 마우스 클릭을 보내 네이버가 인정하게 한다.
// (사장님 publisher.js 의 page.keyboard/mouse 를 확장 안에서 재현)
//
// 흐름: ①에디터 포커스 → ②진짜 Ctrl+V(본문+굵게+인용구+구분선) → ③소제목만 툴바 자동클릭
//   - 소제목은 단축키가 없어 '본문▾' 문단서식 드롭다운 → '소제목' 옵션을 좌표로 진짜 클릭.

// 아이콘 클릭 → 패널 열기/닫기
chrome.action.onClicked.addListener((tab) => {
  if (!tab || !/^https:\/\/blog\.naver\.com\//.test(tab.url || "")) return;
  chrome.tabs.sendMessage(tab.id, { type: "SGM_TOGGLE" }).catch(() => {});
});

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg && msg.type === "SGM_AUTO") {
    const tabId = sender.tab && sender.tab.id;
    autoPaste(tabId, msg.draft || "").then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true; // async
  }
});

// ── CDP 헬퍼 ──
function dbgAttach(target) {
  return new Promise((res, rej) => chrome.debugger.attach(target, "1.3", () =>
    chrome.runtime.lastError ? rej(new Error(chrome.runtime.lastError.message)) : res()));
}
function dbgDetach(target) {
  return new Promise((res) => chrome.debugger.detach(target, () => { void chrome.runtime.lastError; res(); }));
}
function cdp(target, method, params) {
  return new Promise((res, rej) => chrome.debugger.sendCommand(target, method, params || {}, (r) =>
    chrome.runtime.lastError ? rej(new Error(chrome.runtime.lastError.message)) : res(r)));
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function evalVal(target, expression) {
  const r = await cdp(target, "Runtime.evaluate", { expression, returnByValue: true });
  return r && r.result ? r.result.value : null;
}

// ── 편집영역 포커스(상단/‪#mainFrame‬ iframe 동일출처 둘 다 탐색) + 커서 맨 끝 ──
const FOCUS_EXPR = `(function(){
  function findEd(doc){
    try{
      var e = doc.querySelector('.se-main-container [contenteditable="true"]');
      if(e) return e;
      var eds = [].slice.call(doc.querySelectorAll('[contenteditable="true"]'))
        .filter(function(x){ return !x.closest('.se-section-documentTitle'); });
      eds.sort(function(a,b){ return (b.innerText||'').length - (a.innerText||'').length; });
      return eds[0] || null;
    }catch(e){ return null; }
  }
  var ed = findEd(document);
  if(!ed){ var f=document.querySelector('#mainFrame'); if(f&&f.contentDocument) ed=findEd(f.contentDocument); }
  if(!ed) return 'NO_EDITOR';
  try{
    ed.focus();
    var win=ed.ownerDocument.defaultView||window;
    var r=ed.ownerDocument.createRange(); r.selectNodeContents(ed); r.collapse(false);
    var s=win.getSelection(); s.removeAllRanges(); s.addRange(r);
  }catch(e){}
  return 'OK';
})()`;

// iframe 오프셋 + 문서 얻는 prelude (좌표 계산용)
const PRELUDE = `
  var __if=document.querySelector('#mainFrame'); var __off={x:0,y:0}; var __doc=document;
  if(__if){ var __r=__if.getBoundingClientRect(); __off={x:__r.left,y:__r.top}; if(__if.contentDocument) __doc=__if.contentDocument; }
`;

// 특정 소제목 텍스트를 가진 문단의 클릭 좌표(문단 왼쪽 근처 = 커서 두기 좋음)
function PARA_COORD_EXPR(text) {
  return `(function(){ ${PRELUDE}
    var want=${JSON.stringify(String(text).replace(/\s/g, ""))};
    var ps=[].slice.call(__doc.querySelectorAll('.se-text-paragraph'));
    for(var i=0;i<ps.length;i++){
      var t=(ps[i].innerText||'').replace(/\\s/g,'');
      if(t===want){
        try{ ps[i].scrollIntoView({block:'center'}); }catch(e){}
        var r=ps[i].getBoundingClientRect();
        return {x:Math.round(__off.x+r.left+Math.min(35,r.width/2)), y:Math.round(__off.y+r.top+r.height/2)};
      }
    }
    return null;
  })()`;
}

// 문단서식('본문▾') 드롭다운 버튼 좌표
const PARAFMT_BTN_EXPR = `(function(){ ${PRELUDE}
  function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}
  var btns=[].slice.call(__doc.querySelectorAll('button'));
  var cand=null;
  for(var i=0;i<btns.length;i++){ var b=btns[i]; if(!vis(b))continue; var al=(b.getAttribute('aria-label')||''); if(al.indexOf('문단 서식')>=0){cand=b;break;} }
  if(!cand){ for(var j=0;j<btns.length;j++){ var b2=btns[j]; if(!vis(b2))continue; var tx=(b2.textContent||'').replace(/\\s/g,''); if((tx==='본문'||tx==='소제목'||tx==='대제목')&&tx.length<=4){cand=b2;break;} } }
  if(!cand) return null;
  var r=cand.getBoundingClientRect();
  return {x:Math.round(__off.x+r.left+r.width/2), y:Math.round(__off.y+r.top+r.height/2)};
})()`;

// 열린 드롭다운에서 '소제목' 옵션 좌표
const HEAD_OPTION_EXPR = `(function(){ ${PRELUDE}
  function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}
  var sels=['.se-toolbar-option-text-button','.se-toolbar-option-label','[role="option"]','button','li','span'];
  for(var s=0;s<sels.length;s++){
    var els=[].slice.call(__doc.querySelectorAll(sels[s]));
    for(var i=0;i<els.length;i++){ var e=els[i]; if(!vis(e))continue; var tx=(e.textContent||'').replace(/\\s/g,''); if(tx==='소제목'){ var r=e.getBoundingClientRect(); return {x:Math.round(__off.x+r.left+r.width/2), y:Math.round(__off.y+r.top+r.height/2)}; } }
  }
  return null;
})()`;

// ── 입력 이벤트 ──
async function pressCtrlV(target) {
  await cdp(target, "Input.dispatchKeyEvent", { type: "rawKeyDown", modifiers: 2, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyDown", modifiers: 2, key: "v", code: "KeyV", windowsVirtualKeyCode: 86, nativeVirtualKeyCode: 86 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 2, key: "v", code: "KeyV", windowsVirtualKeyCode: 86, nativeVirtualKeyCode: 86 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 0, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
}
async function pressEsc(target) {
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27, nativeVirtualKeyCode: 27 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27, nativeVirtualKeyCode: 27 });
}
async function clickAt(target, x, y) {
  await cdp(target, "Input.dispatchMouseEvent", { type: "mouseMoved", x, y, button: "none" });
  await cdp(target, "Input.dispatchMouseEvent", { type: "mousePressed", x, y, button: "left", clickCount: 1 });
  await cdp(target, "Input.dispatchMouseEvent", { type: "mouseReleased", x, y, button: "left", clickCount: 1 });
}

// 원고에서 소제목 텍스트(## …) 추출
function extractHeadings(draft) {
  return String(draft).replace(/\r\n/g, "\n").split("\n")
    .map((l) => { const m = l.match(/^\s*#{1,3}\s+(.*)$/); return m ? m[1].replace(/\*\*(.+?)\*\*/g, "$1").trim() : null; })
    .filter(Boolean);
}

// 소제목 한 개: 문단 클릭(커서) → 문단서식 드롭다운 → '소제목'
async function applyOneHeading(target, text) {
  const pc = await evalVal(target, PARA_COORD_EXPR(text));
  if (!pc) return false;
  await clickAt(target, pc.x, pc.y);
  await sleep(260);
  const dc = await evalVal(target, PARAFMT_BTN_EXPR);
  if (!dc) return false;
  await clickAt(target, dc.x, dc.y);
  await sleep(420);
  const oc = await evalVal(target, HEAD_OPTION_EXPR);
  if (!oc) { await pressEsc(target); return false; }
  await clickAt(target, oc.x, oc.y);
  await sleep(340);
  return true;
}

async function autoPaste(tabId, draft) {
  if (!tabId) return { ok: false, error: "탭을 못 찾았어요" };
  const target = { tabId };
  try {
    await dbgAttach(target);
  } catch (e) {
    const m = String(e.message || e);
    return { ok: false, error: (m.includes("Another debugger") || m.includes("attached"))
      ? "개발자도구(F12)가 열려 있으면 닫고 다시 시도해 주세요." : ("디버거 연결 실패: " + m.slice(0, 100)) };
  }
  try {
    const focus = await evalVal(target, FOCUS_EXPR);
    if (focus === "NO_EDITOR") return { ok: false, error: "글쓰기 본문 편집영역을 못 찾았어요(글쓰기 화면인지 확인)." };
    await sleep(120);
    await pressCtrlV(target);
    await sleep(900); // 붙여넣기 반영 대기

    // 소제목 자동 적용 (실패해도 붙여넣기는 성공 처리)
    let headApplied = 0, headTotal = 0;
    try {
      const heads = extractHeadings(draft);
      headTotal = heads.length;
      for (const h of heads) {
        const ok = await applyOneHeading(target, h);
        if (ok) headApplied++;
        await sleep(250);
      }
    } catch (e) { /* 무시 — 본문은 이미 들어감 */ }

    return { ok: true, headApplied, headTotal };
  } finally {
    await dbgDetach(target);
  }
}

chrome.runtime.onInstalled.addListener(() => console.log("[시공막내] 확장 설치/갱신됨"));

// (예약) si0in.kr 웹 → 확장 직접 연결
if (chrome.runtime.onMessageExternal) {
  chrome.runtime.onMessageExternal.addListener((msg, sender, sendResponse) => {
    console.log("[시공막내] 웹에서 메시지", sender && sender.origin);
    sendResponse({ ok: true, received: true });
    return true;
  });
}
