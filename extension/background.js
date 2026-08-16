// 시공막내 — 네이버 블로그 넣기 (service worker · chrome.debugger 자동엔진)
// 확장은 '진짜 키 입력'을 content script 로는 못 함 → chrome.debugger(CDP)로
// 사람과 동일한 신뢰된(trusted) Ctrl+V / 단축키를 보내 네이버가 인정하게 한다.
// (사장님 publisher.js 의 page.keyboard.press 를 확장 안에서 재현)

// 아이콘 클릭 → 패널 열기/닫기
chrome.action.onClicked.addListener((tab) => {
  if (!tab || !/^https:\/\/blog\.naver\.com\//.test(tab.url || "")) return;
  chrome.tabs.sendMessage(tab.id, { type: "SGM_TOGGLE" }).catch(() => {});
});

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg && msg.type === "SGM_AUTO") {
    const tabId = sender.tab && sender.tab.id;
    autoPaste(tabId).then(sendResponse).catch((e) => sendResponse({ ok: false, error: String(e && e.message || e).slice(0, 160) }));
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

// 에디터 편집영역을 찾아 포커스 + 커서를 맨 끝에 둔다(상단 프레임/‪#mainFrame‬ iframe 둘 다 탐색, 동일 출처).
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
  if(!ed){ var f = document.querySelector('#mainFrame'); if(f && f.contentDocument) ed = findEd(f.contentDocument); }
  if(!ed) return 'NO_EDITOR';
  try{
    ed.focus();
    var win = ed.ownerDocument.defaultView || window;
    var r = ed.ownerDocument.createRange();
    r.selectNodeContents(ed); r.collapse(false);
    var s = win.getSelection(); s.removeAllRanges(); s.addRange(r);
  }catch(e){}
  return 'OK';
})()`;

// 신뢰된 Ctrl+V (Windows). modifiers: Ctrl=2.
async function pressCtrlV(target) {
  await cdp(target, "Input.dispatchKeyEvent", { type: "rawKeyDown", modifiers: 2, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyDown", modifiers: 2, key: "v", code: "KeyV", windowsVirtualKeyCode: 86, nativeVirtualKeyCode: 86 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 2, key: "v", code: "KeyV", windowsVirtualKeyCode: 86, nativeVirtualKeyCode: 86 });
  await cdp(target, "Input.dispatchKeyEvent", { type: "keyUp", modifiers: 0, key: "Control", code: "ControlLeft", windowsVirtualKeyCode: 17, nativeVirtualKeyCode: 17 });
}

async function autoPaste(tabId) {
  if (!tabId) return { ok: false, error: "탭을 못 찾았어요" };
  const target = { tabId };
  try {
    await dbgAttach(target);
  } catch (e) {
    const m = String(e.message || e);
    // 흔한 원인: 개발자도구(F12)가 열려 있으면 디버거가 이미 붙어 있어 실패
    return { ok: false, error: m.includes("Another debugger") || m.includes("attached")
      ? "개발자도구(F12)가 열려 있으면 닫고 다시 시도해 주세요." : ("디버거 연결 실패: " + m.slice(0, 100)) };
  }
  try {
    const ev = await cdp(target, "Runtime.evaluate", { expression: FOCUS_EXPR, returnByValue: true });
    const val = ev && ev.result && ev.result.value;
    if (val === "NO_EDITOR") return { ok: false, error: "글쓰기 본문 편집영역을 못 찾았어요(글쓰기 화면인지 확인)." };
    await sleep(120);
    await pressCtrlV(target);
    await sleep(600); // 붙여넣기 반영 대기
    return { ok: true };
  } finally {
    await dbgDetach(target);
  }
}

chrome.runtime.onInstalled.addListener(() => console.log("[시공막내] 확장 설치/갱신됨 v0.3.0"));

// (예약) si0in.kr 웹 → 확장 직접 연결
if (chrome.runtime.onMessageExternal) {
  chrome.runtime.onMessageExternal.addListener((msg, sender, sendResponse) => {
    console.log("[시공막내] 웹에서 메시지", sender && sender.origin);
    sendResponse({ ok: true, received: true });
    return true;
  });
}
