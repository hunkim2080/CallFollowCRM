// 시공막내 — si0in.kr 다리 (content script)
// 뷰어(웹)와 확장을 잇는 얇은 다리. 두 가지 역할:
//  1) 확장 설치 표시 — <html data-sgm-ext="1"> 를 심어 뷰어가 "설치됨"을 감지.
//  2) [네이버에 넣기] 신호 중계 — 뷰어가 window.postMessage({__sgm:'naver-insert'}) 하면
//     chrome.storage.local 에 예약 플래그를 남김 → 네이버 글쓰기 페이지의 panel.js 가
//     그 플래그를 보고 '바로 넣기'를 자동 실행(완전 원클릭).
// content script 는 페이지와 DOM 은 공유하되 JS 세계는 격리 → 페이지는 이 파일 코드를 못 봄(안전).
(function () {
  function mark() { try { document.documentElement.setAttribute("data-sgm-ext", "1"); } catch (e) {} }
  mark();
  // 문서가 늦게 준비돼도 마커 유지
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mark, { once: true });
  }
  window.addEventListener("message", function (ev) {
    if (ev.source !== window || !ev.data || typeof ev.data !== "object") return;
    var kind = ev.data.__sgm;
    if (kind === "naver-insert") {
      // 뷰어가 '네이버에 넣기' 누름 → 예약(2분 안에 네이버 글쓰기 뜨면 자동 실행)
      var upd = { sgmAutoInsert: Date.now() };
      // 로그인된 '검증 번호'가 실려오면 그걸 저장(사장님이 직접 타이핑 안 함 → 오타·허위 원천 차단)
      var digits = String((ev.data && ev.data.phone) || "").replace(/\D/g, "");
      if (digits.length >= 9) upd.sgmPhone = digits;
      try { chrome.storage.local.set(upd); } catch (e) {}
    } else if (kind === "ping") {
      // 뷰어가 설치 여부 물음 → 마커 재확인 + 응답
      mark();
      try { window.postMessage({ __sgm: "pong" }, "*"); } catch (e) {}
    }
  });
  // 확장(네이버 탭)이 삽입 완료하면 sgmInsertDone 저장 → 뷰어로 완료신호 중계(친근한 완료 멘트용). 2026-08-18.
  try {
    chrome.storage.onChanged.addListener(function (changes, area) {
      if (area === "local" && changes.sgmInsertDone && changes.sgmInsertDone.newValue) {
        var d = changes.sgmInsertDone.newValue || {};
        try { window.postMessage({ __sgm: "insert-done", parts: d.parts || "" }, "*"); } catch (e) {}
      }
    });
  } catch (e) {}
})();
