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
    pastePhoto(tabId, msg.marker).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
  if (msg && msg.type === "SGM_PHOTO_GROUP") {
    uploadGroup(tabId, msg.marker, msg.images || []).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
  if (msg && msg.type === "SGM_GROUP_IMAGES") {
    dragGroups(tabId, msg.groups || []).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
  if (msg && msg.type === "SGM_SAVE") {
    saveTemp(tabId).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
  if (msg && msg.type === "SGM_LOAD") {
    loadDraft(msg.phone).then(sendResponse)
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e).slice(0, 160) }));
    return true;
  }
});

// ── 확장 리로드/업데이트 시, 열려있는 si0in.kr 탭에 bridge.js 재주입 ──
// 안 하면 옛 bridge.js(context invalidated)라 [네이버에 넣기] 신호가 확장으로 안 감 → 자동삽입 안 됨.
// 재주입하면 새 bridge.js 가 붙어 신호 정상 전달(사장님이 si0in.kr 새로고침 안 해도 됨). 2026-08-18.
chrome.runtime.onInstalled.addListener(reinjectBridge);
function reinjectBridge() {
  try {
    chrome.tabs.query({ url: ["https://si0in.kr/*", "https://*.si0in.kr/*"] }, (tabs) => {
      for (const t of (tabs || [])) {
        if (t && t.id != null) chrome.scripting.executeScript({ target: { tabId: t.id }, files: ["bridge.js"] }).catch(() => {});
      }
    });
  } catch (e) {}
}

// ── 시공막내(si0in.kr)에서 생성 글 불러오기 ── CORS 안전하게 background 에서 fetch.
function bufToB64(buf) {
  const b = new Uint8Array(buf); let s = ""; const chunk = 0x8000;
  for (let i = 0; i < b.length; i += chunk) s += String.fromCharCode.apply(null, b.subarray(i, i + chunk));
  return btoa(s);
}
async function loadDraft(phone) {
  const digits = String(phone || "").replace(/\D/g, "");
  if (digits.length < 9) return { ok: false, error: "내 전화번호를 확인해 주세요." };
  let data;
  try {
    const res = await fetch(`https://api.si0in.kr/api/web/naver-draft?phone=${encodeURIComponent(digits)}`, { headers: { Accept: "application/json" } });
    if (!res.ok) return { ok: false, error: "서버 응답 " + res.status + " (아직 준비 안 됐을 수 있어요)" };
    data = await res.json();
  } catch (e) {
    return { ok: false, error: "서버 연결 실패(준비 중일 수 있어요): " + String((e && e.message) || e).slice(0, 60) };
  }
  if (!data || !data.ok) return { ok: false, error: (data && (data.reason || data.error)) || "불러올 글이 없어요." };
  // 사진 URL → dataURL (background 가 받아서 패널로 전달)
  const photos = [];
  if (Array.isArray(data.photos)) {
    for (const ph of data.photos) {
      if (!ph || !ph.url) continue;
      try {
        const r = await fetch(ph.url);
        if (!r.ok) continue;
        const buf = await r.arrayBuffer();
        const type = r.headers.get("content-type") || "image/jpeg";
        photos.push({ index: ph.index, dataUrl: "data:" + type + ";base64," + bufToB64(buf) });
      } catch (e) { /* 개별 사진 실패는 건너뜀 */ }
    }
  }
  return { ok: true, title: data.title || "", draft: data.draft || "", photos, keywords: Array.isArray(data.keywords) ? data.keywords : [] };
}

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
// 진행상황을 패널(content script)로 실시간 전송 → 사장님이 뭐 하는 중인지 봄 (2026-08-18)
const prog = (tabId, m) => { try { if (tabId) chrome.tabs.sendMessage(tabId, { type: "SGM_PROGRESS", msg: m }); } catch (e) {} };
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


// '작성 중인 글이 있습니다' 팝업 → [취소](새로 작성) 버튼 좌표. 없으면 null. (top 문서·iframe 둘 다 탐색)
const DRAFT_POPUP_CANCEL_EXPR = `(function(){
  function scan(doc, offx, offy){
    try{
      var vis=function(e){var r=e.getBoundingClientRect();return r.width>4&&r.height>4;};
      var has=false, els=[].slice.call(doc.querySelectorAll('div,p,span,strong,h1,h2,h3'));
      for(var i=0;i<els.length;i++){ var t=(els[i].textContent||''); if(t.indexOf('작성 중')>=0 && t.length<90 && vis(els[i])){ has=true; break; } }
      if(!has) return null;
      var btns=[].slice.call(doc.querySelectorAll('button,[role="button"],a'));
      for(var j=0;j<btns.length;j++){ var b=btns[j]; if(!vis(b))continue; var bt=(b.textContent||'').replace(/\\s/g,''); if(bt==='취소'||bt==='아니오'||bt==='새로작성'){ var r=b.getBoundingClientRect(); return {x:Math.round(offx+r.left+r.width/2), y:Math.round(offy+r.top+r.height/2)}; } }
    }catch(e){}
    return null;
  }
  var top=scan(document,0,0); if(top) return top;
  var f=document.querySelector('#mainFrame'); if(f&&f.contentDocument){ var rr=f.getBoundingClientRect(); var inn=scan(f.contentDocument,rr.left,rr.top); if(inn) return inn; }
  return null;
})()`;

// 툴바 '저장'(임시저장) 버튼 좌표 — 단축키보다 확실. (top 문서·iframe 둘 다)
const SAVE_BTN_EXPR = `(function(){
  function scan(doc, offx, offy){
    try{
      var vis=function(e){var r=e.getBoundingClientRect();return r.width>4&&r.height>4;};
      var btns=[].slice.call(doc.querySelectorAll('button,[role="button"],a'));
      for(var j=0;j<btns.length;j++){ var b=btns[j]; if(!vis(b))continue; var bt=(b.textContent||'').replace(/\\s/g,''); if(bt==='저장'||bt==='임시저장'){ var r=b.getBoundingClientRect(); return {x:Math.round(offx+r.left+r.width/2), y:Math.round(offy+r.top+r.height/2)}; } }
    }catch(e){}
    return null;
  }
  var top=scan(document,0,0); if(top) return top;
  var f=document.querySelector('#mainFrame'); if(f&&f.contentDocument){ var rr=f.getBoundingClientRect(); var inn=scan(f.contentDocument,rr.left,rr.top); if(inn) return inn; }
  return null;
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

// 본문에서 자리표 묶음(예 '[1] [2]')을 찾아 그 오른쪽 끝 클릭좌표 + 길이 반환
function POSITION_MARKER_EXPR(markerStr) {
  return `(function(){ ${PRELUDE}
    var mk=${JSON.stringify(String(markerStr))};
    var scope=__doc.querySelector('.se-main-container, .se-content, .se-container')||__doc.body;
    var w=__doc.createTreeWalker(scope, NodeFilter.SHOW_TEXT, null, false); var node;
    while(node=w.nextNode()){
      var i=node.nodeValue.indexOf(mk);
      if(i!==-1){
        try{ if(node.parentElement) node.parentElement.scrollIntoView({block:'center'}); }catch(e){}
        var range=__doc.createRange(); range.setStart(node,i); range.setEnd(node,i+mk.length);
        var rects=range.getClientRects();
        if(rects.length){ var last=rects[rects.length-1]; return {x:Math.round(__off.x+last.right-1), y:Math.round(__off.y+last.top+last.height/2), len:mk.length}; }
      }
    }
    return null;
  })()`;
}

// ── 입력 이벤트 ──
async function pressBackspace(target) {
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyDown", key: "Backspace", code: "Backspace", windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", key: "Backspace", code: "Backspace", windowsVirtualKeyCode: 8, nativeVirtualKeyCode: 8 });
}
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
    prog(tabId, "① 에디터 연결됨 · 팝업 확인 중…");
    // 0) 팝업 폴백 닫기 (content script 가 먼저 닫지만 혹시 남았으면 좌표로도)
    try { const _pc = await evalVal(target, DRAFT_POPUP_CANCEL_EXPR); if (_pc) { await clickAt(target, _pc.x, _pc.y); await sleep(550); } } catch (e) {}
    // 1) 본문 먼저 (fresh 상태라 FOCUS_EXPR 로 잘 들어감)
    prog(tabId, "② 제목·본문 붙이는 중…");
    const focus = await evalVal(target, FOCUS_EXPR);
    if (focus === "NO_EDITOR") return { ok: false, error: "글쓰기 본문 편집영역을 못 찾았어요." };
    await sleep(120);
    await pressCtrlV(target);
    await sleep(950);

    // 2) 소제목 (본문 안 문단 클릭 → 문단서식 → 소제목)
    let headApplied = 0, headTotal = 0, diag = "";
    try {
      const heads = extractHeadings(draft);
      headTotal = heads.length;
      for (let i = 0; i < heads.length; i++) {
        prog(tabId, "③ 소제목 " + (i + 1) + "/" + heads.length + " 만드는 중…");
        const st = await applyOneHeading(target, heads[i]);
        if (st === "ok") headApplied++;
        else if (!diag) diag = st + "「" + heads[i].slice(0, 8) + "」";
        await sleep(250);
      }
    } catch (e) { if (!diag) diag = "err:" + String(e.message || e).slice(0, 40); }

    // 3) 제목 마지막 (진짜 클릭이 포커스를 제목으로 옮김 → 본문 오염 방지)
    if (title) {
      const tc = await evalVal(target, TITLE_COORD_EXPR);
      if (tc) {
        await clickAt(target, tc.x, tc.y);
        await sleep(260);
        await cdp(target, "Input.insertText", { text: title });
        await sleep(220);
        titleOk = true;
      }
    }

    return { ok: true, headApplied, headTotal, diag, title: title ? (titleOk ? "ok" : "fail") : "" };
  } finally {
    await dbgDetach(target);
  }
}

// ── 사진 넣기: 클립보드의 이미지(패널이 담아둠, 나란히면 이미 합쳐진 한 장)를 진짜 Ctrl+V ──
// marker(예 '[1] [2]') 자리표 묶음을 찾아 그 자리에 (묶음 지우고) 붙여넣음. 없으면 맨 끝(fallback).
async function pastePhoto(tabId, marker) {
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
    let placed = false;
    if (marker) {
      const pos = await evalVal(target, POSITION_MARKER_EXPR(marker));
      if (pos) {
        await clickAt(target, pos.x, pos.y);      // 자리표 묶음 오른쪽 끝에 커서
        await sleep(220);
        for (let k = 0; k < pos.len; k++) { await pressBackspace(target); await sleep(40); } // '[1] [2]' 통째로 지움
        await sleep(150);
        placed = true;
      }
    }
    if (!placed) {
      const focus = await evalVal(target, FOCUS_EXPR);  // 자리표 없으면 맨 끝
      if (focus === "NO_EDITOR") return { ok: false, error: "글쓰기 본문 편집영역을 못 찾았어요." };
      await sleep(120);
    }
    await pressCtrlV(target);   // 그 자리에 (합친) 이미지 붙여넣기
    await sleep(1600);          // 업로드 반영 대기
    return { ok: true, placed };
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
    await sleep(150);
    // 1순위: 툴바 '저장'(임시저장) 버튼 진짜 클릭 — 단축키보다 확실 (사장님 버그: 임시저장 안 먹음)
    const _sb = await evalVal(target, SAVE_BTN_EXPR);
    if (_sb) { await clickAt(target, _sb.x, _sb.y); await sleep(900); return { ok: true }; }
    // 폴백: Ctrl+Shift+S
    await pressCtrlShiftS(target);
    await sleep(900);
    return { ok: true };
  } finally { await dbgDetach(target); }
}

// ── 여러 장 나란히(네이버 native 묶기): 파일로 저장 → 업로드창에 한 번에 꽂기 ──
// SEO 위해 사진은 개별 유지 + 네이버가 콜라주로 나란히 배치.
let stageSeq = 0;
function stageFile(dataUrl, name) {
  return new Promise((resolve) => {
    try {
      chrome.downloads.download({ url: dataUrl, filename: "sgm_naver/" + name, conflictAction: "overwrite", saveAs: false }, (id) => {
        if (chrome.runtime.lastError || id == null) { resolve(null); return; }
        function onChanged(delta) {
          if (delta.id !== id || !delta.state) return;
          if (delta.state.current === "complete") {
            chrome.downloads.onChanged.removeListener(onChanged);
            chrome.downloads.search({ id }, (items) => resolve(items && items[0] ? { path: items[0].filename, id } : null));
          } else if (delta.state.current === "interrupted") {
            chrome.downloads.onChanged.removeListener(onChanged); resolve(null);
          }
        }
        chrome.downloads.onChanged.addListener(onChanged);
      });
    } catch (e) { resolve(null); }
  });
}
// 업로드 끝난 임시 파일 정리 — 디스크에서 삭제 + 다운로드 목록에서도 제거(지저분함 방지)
function cleanupDownloads(staged) {
  (staged || []).forEach((s) => {
    if (!s || s.id == null) return;
    try { chrome.downloads.removeFile(s.id, () => { void chrome.runtime.lastError; }); } catch (e) {}
    try { chrome.downloads.erase({ id: s.id }, () => { void chrome.runtime.lastError; }); } catch (e) {}
  });
}
function waitFileChooser(tabId, ms) {
  return new Promise((resolve) => {
    let done = false;
    function handler(source, method, params) {
      if (done || !source || source.tabId !== tabId || method !== "Page.fileChooserOpened") return;
      done = true; chrome.debugger.onEvent.removeListener(handler); resolve(params);
    }
    chrome.debugger.onEvent.addListener(handler);
    setTimeout(() => { if (!done) { done = true; chrome.debugger.onEvent.removeListener(handler); resolve(null); } }, ms || 6000);
  });
}
// 사진첨부 단축키 Ctrl+Alt+I (modifiers: Ctrl2+Alt1=3)
async function pressPhotoShortcut(target) {
  await cdp(target, "Input.dispatchKeyEvent", { type: "rawKeyDown", modifiers: 2, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "rawKeyDown", modifiers: 3, key: "Alt", code: "AltLeft", windowsVirtualKeyCode: 18, nativeVirtualKeyCode: 18 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyDown", modifiers: 3, key: "i", code: "KeyI", windowsVirtualKeyCode: 73, nativeVirtualKeyCode: 73 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 3, key: "i", code: "KeyI", windowsVirtualKeyCode: 73, nativeVirtualKeyCode: 73 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 2, key: "Alt", code: "AltLeft", windowsVirtualKeyCode: 18, nativeVirtualKeyCode: 18 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 0, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
}

async function uploadGroup(tabId, marker, images) {
  if (!tabId) return { ok: false, error: "탭을 못 찾았어요" };
  if (!images.length) return { ok: false, error: "사진이 없어요" };
  // 1) 사진들을 파일로 잠깐 저장(경로 얻기 — 업로드 후 자동 삭제)
  const staged = [];
  for (let i = 0; i < images.length; i++) {
    const s = await stageFile(images[i], "g" + (++stageSeq) + "_" + i + ".png");
    if (s) staged.push(s);
  }
  if (!staged.length) return { ok: false, error: "사진 파일 저장 실패(다운로드 권한?)" };
  const paths = staged.map((s) => s.path);

  const target = { tabId };
  try { await dbgAttach(target); }
  catch (e) {
    const m = String(e.message || e);
    return { ok: false, error: (m.includes("Another debugger") || m.includes("attached"))
      ? "개발자도구(F12)가 열려 있으면 닫고 다시 시도해 주세요." : ("디버거 연결 실패: " + m.slice(0, 100)) };
  }
  try {
    await cdp(target, "Page.enable");
    // 2) 자리표 위치로 커서
    let placed = false;
    if (marker) {
      const pos = await evalVal(target, POSITION_MARKER_EXPR(marker));
      if (pos) { await clickAt(target, pos.x, pos.y); await sleep(220); for (let k = 0; k < pos.len; k++) { await pressBackspace(target); await sleep(40); } await sleep(150); placed = true; }
    }
    if (!placed) { await evalVal(target, FOCUS_EXPR); await sleep(120); }
    // 3) 업로드창 가로채기 + 사진첨부(Ctrl+Alt+I) → 파일 여러 개 한 번에
    await cdp(target, "Page.setInterceptFileChooserDialog", { enabled: true });
    const chooserP = waitFileChooser(tabId, 6000);
    await pressPhotoShortcut(target);
    const ev = await chooserP;
    if (!ev || ev.backendNodeId == null) {
      await cdp(target, "Page.setInterceptFileChooserDialog", { enabled: false }).catch(() => {});
      return { ok: false, error: "사진 업로드창을 못 잡았어요(Ctrl+Alt+I 방식). 방식 조정 필요." };
    }
    await cdp(target, "DOM.setFileInputFiles", { files: paths, backendNodeId: ev.backendNodeId });
    await sleep(3000); // 업로드+콜라주 반영
    await cdp(target, "Page.setInterceptFileChooserDialog", { enabled: false }).catch(() => {});
    return { ok: true, placed, count: paths.length };
  } finally {
    await dbgDetach(target);
    cleanupDownloads(staged); // 임시 파일/다운로드기록 정리
  }
}

// ── 나란히: 이미 들어간 이미지를 드래그해 네이버 se-imageStrip(콜라주)로 묶기 ──
// CDP 실측 확정 레시피: 스크롤 컨테이너(.se-content __se-scroll-target)로 앵커를 위로 올려
//   앵커·무버 둘 다 보이게 → 무버 중앙 잡아 → 앵커 오른쪽 가장자리 안쪽(-30px)으로 드래그.
//   (앵커=바로 앞 이미지 g[t-1], 무버=g[t] → 스트립이 오른쪽으로 계속 확장)
function SCROLL_PAIR_EXPR(ai, bj) {
  return `(function(){ ${PRELUDE}
    var imgs=__doc.querySelectorAll('.se-image-resource');
    if(!imgs.length) imgs=__doc.querySelectorAll('figure.se-image img, .se-module-image img, .se-main-container img');
    var A=imgs[${ai}], B=imgs[${bj}];
    if(!A||!B) return null;
    try{ A.scrollIntoView({block:'start'}); }catch(e){}
    var sc=__doc.querySelector('[class*=se-scroll-target]')||__doc.querySelector('.se-content');
    if(sc){ sc.scrollTop -= 50; }
    var vh=(window.innerHeight)||1000;
    var ra=A.getBoundingClientRect(), rb=B.getBoundingClientRect();
    // 🔑 무버 잡기점은 '보이는 윗부분'(중앙은 화면 밖일 수 있어 헛손질). 드롭점도 화면 안으로 clamp.
    var bGrabY=Math.min(Math.round(__off.y+rb.top+70), vh-45);
    var aDropY=Math.min(Math.max(Math.round(__off.y+ra.top+ra.height/2), 70), vh-45);
    return { aRight:Math.round(__off.x+ra.right), aCy:aDropY, bCx:Math.round(__off.x+rb.left+rb.width/2), bCy:bGrabY };
  })()`;
}
async function dragImageBeside(target, c) {
  const dropX = c.aRight - 30, dropY = c.aCy;
  await cdp(target, "Input.dispatchMouseEvent", { type: "mouseMoved", x: c.bCx, y: c.bCy });
  await cdp(target, "Input.dispatchMouseEvent", { type: "mousePressed", x: c.bCx, y: c.bCy, button: "left", clickCount: 1 });
  await sleep(150);
  const steps = 22;
  for (let s = 1; s <= steps; s++) {
    await cdp(target, "Input.dispatchMouseEvent", { type: "mouseMoved", x: Math.round(c.bCx + (dropX - c.bCx) * s / steps), y: Math.round(c.bCy + (dropY - c.bCy) * s / steps), button: "left" });
    await sleep(40);
  }
  await sleep(160);
  await cdp(target, "Input.dispatchMouseEvent", { type: "mouseReleased", x: dropX, y: dropY, button: "left", clickCount: 1 });
}
async function dragGroups(tabId, groups) {
  if (!tabId) return { ok: false, error: "탭을 못 찾았어요" };
  const target = { tabId };
  try { await dbgAttach(target); }
  catch (e) {
    const m = String(e.message || e);
    return { ok: false, error: (m.includes("Another debugger") || m.includes("attached"))
      ? "개발자도구(F12)가 열려 있으면 닫고 다시 시도해 주세요." : ("디버거 연결 실패: " + m.slice(0, 100)) };
  }
  let dragged = 0;
  try {
    for (const g of groups) {
      if (!g || g.length < 2) continue;
      for (let t = 1; t < g.length; t++) {
        const c = await evalVal(target, SCROLL_PAIR_EXPR(g[t - 1], g[t])); // 앞 이미지 옆으로
        if (!c) continue;
        await sleep(250);
        await dragImageBeside(target, c);
        await sleep(850);
        dragged++;
      }
    }
    return { ok: true, dragged };
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
