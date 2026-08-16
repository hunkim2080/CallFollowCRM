// 시공막내 — 네이버 블로그 넣기 (상주 패널 · content script)
// 팝업은 다른 곳 누르면 사라져 내용이 날아감 → 판다랭크처럼 에디터에 '상주하는 패널'로.
// [자동으로 넣기] → 클립보드에 서식HTML 담고 → background(chrome.debugger)가 '진짜 Ctrl+V'.
// format.js 의 toEditorHtml/toPlainText 를 같은 content-script 월드에서 사용.

(function () {
  if (window.__sgmPanelMounted) return;
  window.__sgmPanelMounted = true;

  const SAMPLE = [
    "## 동탄 아파트 화장실 3곳 시공 후기",
    "",
    "경기 동탄의 한 아파트 화장실 시공 후기입니다. 처음엔 바닥만 손보려다 **세 곳 전체**를 새로 하기로 했어요.",
    "",
    "> 부분 보수보다, 세 곳 다 전체로 잡는 게 오래 보기에 훨씬 낫습니다.",
    "",
    "## 시공 과정",
    "",
    "낡은 타일과 줄눈을 걷어내고 방수부터 다시 잡았습니다. 마무리 줄눈은 오염에 강한 제품으로 시공했어요.",
    "",
    "---",
    "",
    "#줄눈시공 #화장실리모델링 #동탄줄눈",
  ].join("\n");

  const host = document.createElement("div");
  host.id = "sgm-panel-host";
  host.style.cssText = "position:fixed;right:18px;bottom:18px;z-index:2147483000;";
  const shadow = host.attachShadow({ mode: "open" });
  shadow.innerHTML = `
    <style>
      *{box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,"Malgun Gothic","Apple SD Gothic Neo",sans-serif}
      .wrap{width:320px;background:#fff;border:1px solid #E5E9EE;border-radius:16px;
        box-shadow:0 8px 30px rgba(16,24,40,.18);overflow:hidden}
      .hd{display:flex;align-items:center;gap:7px;padding:11px 13px;background:#F5F7F9;border-bottom:1px solid #E5E9EE;cursor:default}
      .hd .t{font-size:13.5px;font-weight:850;color:#181D27;flex:1}
      .hd .t b{color:#03C75A}
      .hd .x{border:0;background:none;font-size:15px;font-weight:800;color:#8B95A1;cursor:pointer;padding:2px 6px;border-radius:7px}
      .hd .x:hover{background:#E9EDF1}
      .body{padding:12px 13px 13px}
      .row{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}
      .lbl{font-size:11.5px;font-weight:800;color:#4E5968}
      .mini{border:1px solid #E5E9EE;background:#fff;border-radius:8px;font-size:10.5px;font-weight:800;color:#4E5968;padding:4px 9px;cursor:pointer}
      .mini:active{transform:scale(.97)}
      textarea{width:100%;height:120px;resize:vertical;border:1px solid #E5E9EE;border-radius:11px;padding:10px 11px;
        font-size:12.5px;line-height:1.6;color:#181D27;background:#F5F7F9;outline:none}
      textarea:focus{border-color:#03C75A;background:#fff}
      .hint{font-size:10px;color:#8B95A1;margin-top:6px;line-height:1.5}
      .hint b{color:#4E5968}
      .btn{width:100%;margin-top:10px;border:0;border-radius:12px;background:#03C75A;color:#fff;
        font-size:14px;font-weight:850;padding:12px;cursor:pointer}
      .btn:active{transform:scale(.98)}
      .btn:disabled{background:#C9D0D8;cursor:default;transform:none}
      .out{font-size:11.5px;font-weight:700;margin-top:8px;min-height:15px;line-height:1.5;color:#4E5968}
      .out.ok{color:#03C75A} .out.err{color:#E03131}
      .foot{font-size:10px;color:#B0B8C1;margin-top:9px;line-height:1.5;text-align:center}
      /* 접힘: 작은 알약 */
      .pill{display:none;align-items:center;gap:6px;background:#03C75A;color:#fff;border-radius:999px;
        padding:9px 14px;font-size:12.5px;font-weight:850;cursor:pointer;box-shadow:0 6px 18px rgba(3,199,90,.35)}
      :host(.min) .wrap{display:none}
      :host(.min) .pill{display:inline-flex}
    </style>
    <div class="wrap">
      <div class="hd">
        <span class="t">🧩 시공막내 · <b>네이버 넣기</b></span>
        <button class="x" id="min" title="접기">—</button>
      </div>
      <div class="body">
        <div class="row"><span class="lbl">네이버에 넣을 글</span><button class="mini" id="sample">예시 넣기</button></div>
        <textarea id="text" placeholder="시공막내에서 만든 블로그 글을 여기에 붙여넣으세요."></textarea>
        <div class="hint"><b>##</b> 소제목 · <b>&gt;</b> 인용구 · <b>**굵게**</b> · <b>---</b> 구분선 — 서식 그대로 들어감</div>
        <button class="btn" id="go">✨ 자동으로 넣기</button>
        <div class="out" id="out"></div>
        <div class="foot">자동 발행은 안 해요 · 임시저장/발행은 직접</div>
      </div>
    </div>
    <div class="pill" id="pill">🧩 네이버 넣기</div>
  `;
  document.documentElement.appendChild(host);

  const $ = (s) => shadow.querySelector(s);
  const setOut = (msg, kind) => { const o = $("#out"); o.textContent = msg || ""; o.className = "out" + (kind ? " " + kind : ""); };

  $("#sample").addEventListener("click", () => { $("#text").value = SAMPLE; setOut("", ""); });
  $("#min").addEventListener("click", () => host.classList.add("min"));
  $("#pill").addEventListener("click", () => host.classList.remove("min"));

  async function doAuto() {
    const draft = $("#text").value.trim();
    if (!draft) { setOut("넣을 글을 먼저 붙여넣어 주세요.", "err"); return; }

    const html = toEditorHtml(draft);
    const plain = toPlainText(draft);

    // 1) 진짜 클립보드에 서식 HTML 담기 (패널 클릭 = 사용자 제스처)
    try {
      await navigator.clipboard.write([
        new ClipboardItem({
          "text/html": new Blob([html], { type: "text/html" }),
          "text/plain": new Blob([plain], { type: "text/plain" }),
        }),
      ]);
    } catch (e) {
      try { await navigator.clipboard.writeText(plain); } catch (e2) {}
    }

    // 2) background(chrome.debugger)가 에디터 포커스 + '진짜 Ctrl+V'
    const btn = $("#go");
    btn.disabled = true;
    setOut("자동으로 넣는 중… (잠깐 상단에 '디버깅 중' 띠가 떴다 사라져요)");
    chrome.runtime.sendMessage({ type: "SGM_AUTO" }, (resp) => {
      btn.disabled = false;
      if (chrome.runtime.lastError || !resp || !resp.ok) {
        const msg = (resp && resp.error) || (chrome.runtime.lastError && chrome.runtime.lastError.message) || "알 수 없는 오류";
        setOut("자동 넣기 실패: " + msg, "err");
        return;
      }
      setOut("✓ 넣었어요! 네이버 본문 확인하세요. (소제목·인용구 자동은 다음 단계)", "ok");
    });
  }
  $("#go").addEventListener("click", doAuto);

  // 아이콘 클릭 → 패널 열기/닫기 토글
  chrome.runtime.onMessage.addListener((msg) => {
    if (msg && msg.type === "SGM_TOGGLE") host.classList.toggle("min");
  });
})();
