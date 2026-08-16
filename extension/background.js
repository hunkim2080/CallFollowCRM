// 시공막내 — 네이버 블로그 넣기 (service worker)
// 지금(MVP)은 하는 일이 거의 없음. popup ↔ content 직접 통신으로 충분.
// 다음 단계 예약: si0in.kr 웹의 [네이버에 넣기] → 여기(externally) 로 글/사진 받아서
//                활성 네이버 탭 content script 로 relay.

chrome.runtime.onInstalled.addListener(() => {
  console.log("[시공막내] 확장 설치됨");
});

// 웹(si0in.kr)에서 바로 확장을 부르는 통로 (manifest.externally_connectable 필요)
if (chrome.runtime.onMessageExternal) {
  chrome.runtime.onMessageExternal.addListener((msg, sender, sendResponse) => {
    // TODO(2단계): msg = { text, photos[] } → 활성 네이버 탭으로 relay
    console.log("[시공막내] 웹에서 메시지 수신", sender && sender.origin);
    sendResponse({ ok: true, received: true });
    return true;
  });
}
