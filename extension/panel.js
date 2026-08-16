// 시공막내 — 네이버 블로그 넣기 (상주 패널 · content script)
// 팝업 대신 에디터에 상주(판다랭크식). 안 사라짐.
// [자동으로 넣기] → 클립보드에 서식HTML → background(chrome.debugger) 진짜 Ctrl+V + 소제목 툴바 자동.
// [사진 넣기]   → 고른 사진을 PNG 로 클립보드에 담아 → background 가 진짜 Ctrl+V(이미지 붙여넣기).
// format.js 의 toEditorHtml/toPlainText 사용.

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
  host.style.cssText = "position:fixed;left:18px;bottom:18px;z-index:2147483000;";
  const shadow = host.attachShadow({ mode: "open" });
  shadow.innerHTML = `
    <style>
      *{box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,"Malgun Gothic","Apple SD Gothic Neo",sans-serif}
      .wrap{width:322px;background:#fff;border:1px solid #E5E9EE;border-radius:16px;box-shadow:0 8px 30px rgba(16,24,40,.18);overflow:hidden}
      .hd{display:flex;align-items:center;gap:7px;padding:11px 13px;background:#F5F7F9;border-bottom:1px solid #E5E9EE}
      .hd .t{font-size:13.5px;font-weight:850;color:#181D27;flex:1} .hd .t b{color:#03C75A}
      .hd .x{border:0;background:none;font-size:15px;font-weight:800;color:#8B95A1;cursor:pointer;padding:2px 6px;border-radius:7px}
      .hd .x:hover{background:#E9EDF1}
      .body{padding:12px 13px 13px;max-height:76vh;overflow:auto}
      .row{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}
      .lbl{font-size:11.5px;font-weight:800;color:#4E5968}
      .mini{border:1px solid #E5E9EE;background:#fff;border-radius:8px;font-size:10.5px;font-weight:800;color:#4E5968;padding:4px 9px;cursor:pointer}
      .mini:active{transform:scale(.97)}
      textarea{width:100%;height:112px;resize:vertical;border:1px solid #E5E9EE;border-radius:11px;padding:10px 11px;font-size:12.5px;line-height:1.6;color:#181D27;background:#F5F7F9;outline:none}
      textarea:focus{border-color:#03C75A;background:#fff}
      .hint{font-size:10px;color:#8B95A1;margin-top:6px;line-height:1.5} .hint b{color:#4E5968}
      .btn{width:100%;margin-top:10px;border:0;border-radius:12px;background:#03C75A;color:#fff;font-size:14px;font-weight:850;padding:12px;cursor:pointer}
      .btn:active{transform:scale(.98)} .btn:disabled{background:#C9D0D8;cursor:default;transform:none}
      .btn.sub{background:#fff;color:#03C75A;border:1.5px solid #03C75A;font-size:13px;padding:10px}
      .sep{height:1px;background:#EEF1F4;margin:13px -13px}
      .thumbs{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
      .thumb{position:relative;width:52px;height:52px;border-radius:9px;overflow:hidden;border:1px solid #E5E9EE;background:#F5F7F9}
      .thumb img{width:100%;height:100%;object-fit:cover;display:block}
      .thumb .n{position:absolute;left:3px;top:3px;background:#03C75A;color:#fff;font-size:10px;font-weight:850;border-radius:6px;padding:0 5px;line-height:15px}
      .out{font-size:11.5px;font-weight:700;margin-top:8px;min-height:15px;line-height:1.5;color:#4E5968}
      .out.ok{color:#03C75A} .out.err{color:#E03131}
      .foot{font-size:10px;color:#B0B8C1;margin-top:9px;line-height:1.5;text-align:center}
      .pill{display:none;align-items:center;gap:6px;background:#03C75A;color:#fff;border-radius:999px;padding:9px 14px;font-size:12.5px;font-weight:850;cursor:pointer;box-shadow:0 6px 18px rgba(3,199,90,.35)}
      :host(.min) .wrap{display:none} :host(.min) .pill{display:inline-flex}
      input[type=file]{display:none}
    </style>
    <div class="wrap">
      <div class="hd"><span class="t">🧩 시공막내 · <b>네이버 넣기</b></span><button class="x" id="min" title="접기">—</button></div>
      <div class="body">
        <div class="row"><span class="lbl">네이버에 넣을 글</span><button class="mini" id="sample">예시 넣기</button></div>
        <textarea id="text" placeholder="시공막내에서 만든 블로그 글을 여기에 붙여넣으세요."></textarea>
        <div class="hint"><b>##</b> 소제목 · <b>&gt;</b> 인용구 · <b>**굵게**</b> · <b>---</b> 구분선 — 서식 그대로</div>
        <button class="btn" id="go">✨ 자동으로 넣기</button>

        <div class="sep"></div>
        <div class="row"><span class="lbl">사진 (골라서 넣기)</span><button class="mini" id="pick">🖼 사진 고르기</button></div>
        <input type="file" id="file" accept="image/*" multiple>
        <div class="thumbs" id="thumbs"></div>
        <button class="btn sub" id="goPhoto">📷 사진 넣기</button>

        <div class="out" id="out"></div>
        <div class="foot">자동 발행은 안 해요 · 임시저장/발행은 직접</div>
      </div>
    </div>
    <div class="pill" id="pill">🧩 네이버 넣기</div>
  `;
  document.documentElement.appendChild(host);

  const $ = (s) => shadow.querySelector(s);
  const setOut = (m, k) => { const o = $("#out"); o.textContent = m || ""; o.className = "out" + (k ? " " + k : ""); };
  const send = (msg) => new Promise((res) => chrome.runtime.sendMessage(msg, (r) => res(chrome.runtime.lastError ? null : r)));

  let photos = []; // File[]

  $("#sample").addEventListener("click", () => { $("#text").value = SAMPLE; setOut("", ""); });
  $("#min").addEventListener("click", () => host.classList.add("min"));
  $("#pill").addEventListener("click", () => host.classList.remove("min"));
  $("#pick").addEventListener("click", () => $("#file").click());
  $("#file").addEventListener("change", (e) => {
    photos = Array.prototype.slice.call(e.target.files || []);
    const t = $("#thumbs"); t.innerHTML = "";
    photos.forEach((f, i) => {
      const d = document.createElement("div"); d.className = "thumb";
      const img = document.createElement("img"); img.src = URL.createObjectURL(f);
      const n = document.createElement("span"); n.className = "n"; n.textContent = String(i + 1);
      d.appendChild(img); d.appendChild(n); t.appendChild(d);
    });
    setOut(photos.length ? `사진 ${photos.length}장 준비됨` : "", "");
  });

  // ── 글 자동 넣기 ──
  async function doAuto() {
    const draft = $("#text").value.trim();
    if (!draft) { setOut("넣을 글을 먼저 붙여넣어 주세요.", "err"); return; }
    const html = toEditorHtml(draft), plain = toPlainText(draft);
    try {
      await navigator.clipboard.write([new ClipboardItem({ "text/html": new Blob([html], { type: "text/html" }), "text/plain": new Blob([plain], { type: "text/plain" }) })]);
    } catch (e) { try { await navigator.clipboard.writeText(plain); } catch (e2) {} }

    const btn = $("#go"); btn.disabled = true;
    setOut("자동으로 넣는 중… (상단 '디버깅 중' 띠는 잠깐)");
    const resp = await send({ type: "SGM_AUTO", draft });
    btn.disabled = false;
    if (!resp || !resp.ok) { setOut("자동 넣기 실패: " + ((resp && resp.error) || "오류"), "err"); return; }
    const h = resp.headApplied || 0, ht = resp.headTotal || 0;
    const tail = ht ? ` · 소제목 ${h}/${ht}` + (h < ht ? ` (${resp.diag || "실패"} — 알려주세요)` : "") : "";
    setOut("✓ 넣었어요! 본문·굵게·인용구·구분선" + tail, "ok");
  }
  $("#go").addEventListener("click", doAuto);

  // 이미지 → PNG Blob (클립보드는 image/png 가 가장 안전)
  function toPng(file) {
    return new Promise((resolve, reject) => {
      const img = new Image(); const url = URL.createObjectURL(file);
      img.onload = () => {
        try { const c = document.createElement("canvas"); c.width = img.naturalWidth; c.height = img.naturalHeight; c.getContext("2d").drawImage(img, 0, 0); URL.revokeObjectURL(url); c.toBlob((b) => b ? resolve(b) : reject(new Error("png변환실패")), "image/png"); }
        catch (e) { reject(e); }
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("이미지 로드 실패")); };
      img.src = url;
    });
  }

  // ── 사진 넣기 (클립보드 이미지 + 진짜 Ctrl+V) ──
  async function doPhotos() {
    if (!photos.length) { setOut("먼저 [사진 고르기]로 사진을 선택하세요.", "err"); return; }
    const btn = $("#goPhoto"); btn.disabled = true;
    let done = 0;
    for (let i = 0; i < photos.length; i++) {
      setOut(`사진 넣는 중… (${i + 1}/${photos.length})`);
      let png;
      try { png = await toPng(photos[i]); } catch (e) { setOut(`사진 ${i + 1} 변환 실패: ${String(e.message || e).slice(0, 50)}`, "err"); break; }
      try { await navigator.clipboard.write([new ClipboardItem({ "image/png": png })]); }
      catch (e) { setOut(`사진 ${i + 1} 클립보드 실패(브라우저 제한): ${String(e.message || e).slice(0, 50)}`, "err"); break; }
      const resp = await send({ type: "SGM_PHOTO", index: i + 1 });
      if (!resp || !resp.ok) { setOut(`사진 ${i + 1} 넣기 실패: ${(resp && resp.error) || "오류"}`, "err"); break; }
      done++;
      await new Promise((r) => setTimeout(r, 500));
    }
    btn.disabled = false;
    if (done === photos.length) setOut(`✓ 사진 ${done}장 넣었어요!`, "ok");
    else if (done > 0) setOut(`사진 ${done}/${photos.length}장 넣음 — 나머지 실패(알려주세요)`, "err");
  }
  $("#goPhoto").addEventListener("click", doPhotos);

  chrome.runtime.onMessage.addListener((msg) => { if (msg && msg.type === "SGM_TOGGLE") host.classList.toggle("min"); });
})();
