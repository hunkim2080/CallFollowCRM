// 시공막내 — 네이버 블로그 넣기 (content script)
// 네이버 블로그 글쓰기(스마트에디터 ONE) 페이지/프레임에서 동작.
// all_frames:true 라 상단 프레임 + 에디터 iframe 양쪽에서 실행됨.
// → 에디터를 찾은 프레임만 응답한다(못 찾은 프레임은 조용히 무시).

(function () {
  const TAG = "[시공막내]";
  const log = (...a) => { try { console.log(TAG, ...a); } catch (_) {} };

  // 스마트에디터 편집영역 찾기 (버전 따라 셀렉터가 달라 여러 개 시도)
  function findEditor() {
    const sels = [
      '.se-content [contenteditable="true"]',
      '.se-section-text [contenteditable="true"]',
      '.se-module-text [contenteditable="true"]',
      '.se-main-container [contenteditable="true"]',
      '.se_edit_area [contenteditable="true"]', // 구버전(SE2)
    ];
    for (const s of sels) {
      try { const el = document.querySelector(s); if (el) return el; } catch (_) {}
    }
    // 최후: 화면에서 가장 큰 contenteditable 영역
    let best = null, area = 0;
    document.querySelectorAll('[contenteditable="true"]').forEach((el) => {
      const r = el.getBoundingClientRect();
      const a = r.width * r.height;
      if (a > area) { area = a; best = el; }
    });
    return best;
  }

  // 글을 문단별로 삽입. execCommand 는 deprecated 지만 contenteditable 에선 아직 가장 잘 먹음.
  function insertText(editor, text) {
    try { editor.focus(); } catch (_) {}
    const paras = String(text).replace(/\r/g, "").split(/\n+/);
    let ok = false;
    for (let i = 0; i < paras.length; i++) {
      const line = paras[i];
      try {
        if (line) document.execCommand("insertText", false, line);
        if (i < paras.length - 1) document.execCommand("insertParagraph", false);
        ok = true;
      } catch (e) { log("insert 실패", e); }
    }
    return ok;
  }

  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (!msg || !msg.type) return false;

    // 에디터 있는 프레임인지 확인용
    if (msg.type === "SGM_PING") {
      const ed = findEditor();
      if (!ed) return false;                 // 에디터 없는 프레임 → 응답 안 함
      sendResponse({ ok: true, url: location.href });
      return true;
    }

    if (msg.type === "SGM_INSERT") {
      const ed = findEditor();
      if (!ed) return false;                 // 에디터 없는 프레임 → 무시
      const ok = insertText(ed, msg.text || "");
      sendResponse({ ok, url: location.href });
      return true;
    }

    return false;
  });

  log("로드됨 —", location.href, "| 에디터 발견:", !!findEditor());
})();
