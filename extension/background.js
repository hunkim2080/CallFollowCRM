// 시공막내 — 네이버 블로그 넣기 (service worker · chrome.debugger 자동엔진)
// 확장은 '진짜 키/클릭'을 content script 로는 못 냄 → chrome.debugger(CDP)로
// 사람과 동일한 신뢰된(trusted) Ctrl+V / 마우스 클릭을 보내 네이버가 인정하게 한다.
//
// 흐름: ①에디터 포커스 → ②진짜 Ctrl+V(본문+굵게+인용구+구분선) → ③소제목만 툴바 자동클릭
//   - 소제목은 단축키가 없어 '본문▾ 문단 서식' 드롭다운 → '소제목' 옵션을 좌표로 진짜 클릭.
//   - 문단서식 버튼 실제 텍스트 = "본문문단 서식 변경" (publisher.js 확인).

chrome.action.onClicked.addListener((tab) => {
  if (!tab || !/^https:\/\/blog\.naver\.com\//.test(tab.url || "")) return;
  chrome.tabs.sendMessage(tab.id, { type: "SGM_TOGGLE" }).catch(() => {});
});

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  const tabId = sender.tab && sender.tab.id;
  if (msg && msg.type === "SGM_AUTO") {
    autoPaste(tabId, msg.draft || "", msg.title || "").then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
  if (msg && msg.type === "SGM_PHOTO") {
    pastePhoto(tabId).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
  if (msg && msg.type === "SGM_SAVE") {
    saveTemp(tabId).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
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

// 편집영역 포커스 + 커서 맨 끝
const FOCUS_EXPR = `(function(){
  function findEd(doc){
    try{
      var e=doc.querySelector('.se-main-container [contenteditable="true"]'); if(e) return e;
      var eds=[].slice.call(doc.querySelectorAll('[contenteditable="true"]')).filter(function(x){return !x.closest('.se-section-documentTitle');});
      eds.sort(function(a,b){return (b.innerText||'').length-(a.innerText||'').length;}); return eds[0]||null;
    }catch(e){return null;}
  }
  var ed=findEd(document);
  if(!ed){ var f=document.querySelector('#mainFrame'); if(f&&f.contentDocument) ed=findEd(f.contentDocument); }
  if(!ed) return 'NO_EDITOR';
  try{ ed.focus(); var win=ed.ownerDocument.defaultView||window; var r=ed.ownerDocument.createRange(); r.selectNodeContents(ed); r.collapse(false); var s=win.getSelection(); s.removeAllRanges(); s.addRange(r); }catch(e){}
  return 'OK';
})()`;


// iframe 오프셋 + 문서 얻기 (좌표 계산)
const PRELUDE = `
  var __if=document.querySelector('#mainFrame'); var __off={x:0,y:0}; var __doc=document;
  if(__if){ var __r=__if.getBoundingClientRect(); __off={x:__r.left,y:__r.top}; if(__if.contentDocument) __doc=__if.contentDocument; }
`;

// 제목란 클릭 좌표 (진짜 클릭으로 활성화해야 입력됨)
const TITLE_COORD_EXPR = `(function(){ ${PRELUDE}
  function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}
  var sec=__doc.querySelector('.se-section-documentTitle, .se-documentTitle, .se-title, [class*="documentTitle" i], [class*="section-title" i]');
  var el=null;
  if(sec){ el=sec.querySelector('[contenteditable]')||sec; }
  if(!el){ el=__doc.querySelector('.se-title-text [contenteditable], [contenteditable][aria-label*="제목"]'); }
  if(!el||!vis(el)) return null;
  try{ el.scrollIntoView({block:'center'}); }catch(e){}
  var r=el.getBoundingClientRect();
  return {x:Math.round(__off.x+r.left+Math.min(60,Math.max(20,r.width/2))), y:Math.round(__off.y+r.top+r.height/2)};
})()`;

// 소제목 텍스트를 가진 문단의 클릭 좌표
function PARA_COORD_EXPR(text) {
  return `(function(){ ${PRELUDE}
    var want=${JSON.stringify(String(text).replace(/\s/g, ""))};
    function coord(el){ try{el.scrollIntoView({block:'center'});}catch(e){} var r=el.getBoundingClientRect(); return {x:Math.round(__off.x+r.left+Math.min(35,r.width/2)), y:Math.round(__off.y+r.top+r.height/2)}; }
    var ps=[].slice.call(__doc.querySelectorAll('.se-text-paragraph'));
    for(var i=0;i<ps.length;i++){ if((ps[i].innerText||'').replace(/\\s/g,'')===want) return coord(ps[i]); }
    // 폴백: 편집영역 내 innerText 가 정확히 일치하는 '가장 작은' 요소
    var scope=__doc.querySelector('.se-main-container, .se-content, .se-container')||__doc.body;
    var all=[].slice.call(scope.querySelectorAll('*')); var best=null, bestLen=1e9;
    for(var j=0;j<all.length;j++){ var t=(all[j].innerText||'').replace(/\\s/g,''); if(t===want){ var len=(all[j].textContent||'').length; if(len<bestLen){bestLen=len;best=all[j];} } }
    if(best) return coord(best);
    return null;
  })()`;
}

// 문단서식('본문문단 서식 변경') 드롭다운 버튼 좌표
const PARAFMT_BTN_EXPR = `(function(){ ${PRELUDE}
  function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}
  var btns=[].slice.call(__doc.querySelectorAll('button, [role="button"]'));
  var cand=null;
  for(var i=0;i<btns.length;i++){ var b=btns[i]; if(!vis(b))continue;
    var al=(b.getAttribute('aria-label')||'').replace(/\\s/g,''); var tx=(b.textContent||'').replace(/\\s/g,'');
    if(al.indexOf('문단서식')>=0 || tx.indexOf('문단서식')>=0){ cand=b; break; } }
  if(!cand){ for(var j=0;j<btns.length;j++){ var b2=btns[j]; if(!vis(b2))continue; var t2=(b2.textContent||'').replace(/\\s/g,''); if(t2.indexOf('본문')===0 && t2.length<=8){cand=b2;break;} } }
  if(!cand) return null;
  var r=cand.getBoundingClientRect();
  return {x:Math.round(__off.x+r.left+r.width/2), y:Math.round(__off.y+r.top+r.height/2)};
})()`;

// 열린 드롭다운의 '소제목' 옵션 좌표
const HEAD_OPTION_EXPR = `(function(){ ${PRELUDE}
  function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}
  var sels=['.se-toolbar-option-text-button','.se-toolbar-option-label','[role="option"]','button','li','a','span'];
  for(var s=0;s<sels.length;s++){ var els=[].slice.call(__doc.querySelectorAll(sels[s]));
    for(var i=0;i<els.length;i++){ var e=els[i]; if(!vis(e))continue; var tx=(e.textContent||'').replace(/\\s/g,'');
      if(tx==='소제목'||(tx.indexOf('소제목')>=0&&tx.length<=6)){ var r=e.getBoundingClientRect(); return {x:Math.round(__off.x+r.left+r.width/2), y:Math.round(__off.y+r.top+r.height/2)}; } } }
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

function extractHeadings(draft) {
  return String(draft).replace(/\r\n/g, "\n").split("\n")
    .map((l) => { const m = l.match(/^\s*#{1,3}\s+(.*)$/); return m ? m[1].replace(/\*\*(.+?)\*\*/g, "$1").trim() : null; })
    .filter(Boolean);
}

// 소제목 한 개: 문단 클릭 → 문단서식 드롭다운 → '소제목'. 실패 단계 코드 반환.
async function applyOneHeading(target, text) {
  const pc = await evalVal(target, PARA_COORD_EXPR(text));
  if (!pc) return "no_para";
  await clickAt(target, pc.x, pc.y);
  await sleep(300);
  const dc = await evalVal(target, PARAFMT_BTN_EXPR);
  if (!dc) return "no_btn";
  await clickAt(target, dc.x, dc.y);
  await sleep(520);
  const oc = await evalVal(target, HEAD_OPTION_EXPR);
  if (!oc) { await pressEsc(target); return "no_opt"; }
  await clickAt(target, oc.x, oc.y);
  await sleep(360);
  return "ok";
}

async function autoPaste(tabId, draft, title) {
  if (!tabId) return { ok: false, error: "탭을 못 찾았어요" };
  const target = { tabId };
  try {
    await dbgAttach(target);
  } catch (e) {
    const m = String(e.message || e);
    return { ok: false, error: (m.includes("Another debugger") || m.includes("attached"))
      ? "개발자도구(F12)가 열려 있으면 닫고 다시 시도해 주세요." : ("디버거 연결 실패: " + m.slice(0, 100)) };
  }
  let titleOk = false;
  try {
    // 제목 먼저 (있으면): 진짜 클릭으로 활성화 후 입력
    if (title) {
      const tc = await evalVal(target, TITLE_COORD_EXPR);
      if (tc) {
        await clickAt(target, tc.x, tc.y);
        await sleep(240);
        await cdp(target, "Input.insertText", { text: title });
        await sleep(220);
        titleOk = true;
      }
    }
    const focus = await evalVal(target, FOCUS_EXPR);
    if (focus === "NO_EDITOR") return { ok: false, error: "글쓰기 본문 편집영역을 못 찾았어요." };
    await sleep(120);
    await pressCtrlV(target);
    await sleep(950);

    let headApplied = 0, headTotal = 0, diag = "";
    try {
      const heads = extractHeadings(draft);
      headTotal = heads.length;
      for (let i = 0; i < heads.length; i++) {
        const st = await applyOneHeading(target, heads[i]);
        if (st === "ok") headApplied++;
        else if (!diag) diag = st + "「" + heads[i].slice(0, 8) + "」";
        await sleep(250);
      }
    } catch (e) { if (!diag) diag = "err:" + String(e.message || e).slice(0, 40); }

    return { ok: true, headApplied, headTotal, diag, title: title ? (titleOk ? "ok" : "fail") : "" };
  } finally {
    await dbgDetach(target);
  }
}

// ── 사진 넣기: 클립보드의 이미지(패널이 담아둠)를 진짜 Ctrl+V 로 붙여넣기 ──
// 1차 컷: 편집영역 맨 끝에 붙여넣음(위치 [1][2] 매칭은 다음 컷).
async function pastePhoto(tabId) {
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
    if (focus === "NO_EDITOR") return { ok: false, error: "글쓰기 본문 편집영역을 못 찾았어요." };
    await sleep(120);
    await pressCtrlV(target);   // 클립보드 이미지 붙여넣기
    await sleep(1600);          // 업로드 반영 대기
    return { ok: true };
  } finally {
    await dbgDetach(target);
  }
}

// ── 임시저장 (Ctrl+Shift+S) ── 발행 아님. 편집영역 포커스 후 단축키.
async function pressCtrlShiftS(target) {
  await cdp(target, "Input.dispatchKeyEvent", { type: "rawKeyDown", modifiers: 2, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "rawKeyDown", modifiers: 10, key: "Shift", code: "ShiftLeft", windowsVirtualKeyCode: 16, nativeVirtualKeyCode: 16 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyDown", modifiers: 10, key: "s", code: "KeyS", windowsVirtualKeyCode: 83, nativeVirtualKeyCode: 83 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 10, key: "s", code: "KeyS", windowsVirtualKeyCode: 83, nativeVirtualKeyCode: 83 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 2, key: "Shift", code: "ShiftLeft", windowsVirtualKeyCode: 16, nativeVirtualKeyCode: 16 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 0, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
}
async function saveTemp(tabId) {
  if (!tabId) return { ok: false, error: "탭을 못 찾았어요" };
  const target = { tabId };
  try { await dbgAttach(target); }
  catch (e) {
    const m = String(e.message || e);
    return { ok: false, error: (m.includes("Another debugger") || m.includes("attached"))
      ? "개발자도구(F12)가 열려 있으면 닫고 다시 시도해 주세요." : ("디버거 연결 실패: " + m.slice(0, 100)) };
  }
  try {
    const focus = await evalVal(target, FOCUS_EXPR); // 편집영역에 포커스 둬야 단축키가 먹음
    if (focus === "NO_EDITOR") return { ok: false, error: "글쓰기 화면을 못 찾았어요." };
    await sleep(120);
    await pressCtrlShiftS(target);
    await sleep(800);
    return { ok: true };
  } finally { await dbgDetach(target); }
}

chrome.runtime.onInstalled.addListener(() => console.log("[시공막내] 확장 설치/갱신됨"));

if (chrome.runtime.onMessageExternal) {
  chrome.runtime.onMessageExternal.addListener((msg, sender, sendResponse) => {
    console.log("[시공막내] 웹에서 메시지", sender && sender.origin);
    sendResponse({ ok: true, received: true });
    return true;
  });
}
