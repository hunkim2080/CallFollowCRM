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
    "[1] [2]",
    "",
    "> 부분 보수보다, 세 곳 다 전체로 잡는 게 오래 보기에 훨씬 낫습니다.",
    "",
    "## 시공 과정",
    "",
    "낡은 타일과 줄눈을 걷어내고 방수부터 다시 잡았습니다.",
    "",
    "[3] [4] [5]",
    "",
    "마무리 줄눈은 오염에 강한 제품으로 시공했어요.",
    "",
    "[6] [7]",
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
      .btn.save{background:#F0F3F7;color:#4E5968;font-size:13px;padding:10px;margin-top:8px}
      .btn.load{background:#EDF3FF;color:#3182F6;font-size:13px;padding:11px;margin-top:0}
      .titleInput{width:100%;border:1px solid #E5E9EE;border-radius:11px;padding:9px 11px;font-size:13px;font-weight:700;color:#181D27;background:#F5F7F9;outline:none;margin-bottom:9px}
      .titleInput:focus{border-color:#03C75A;background:#fff}
      .sep{height:1px;background:#EEF1F4;margin:13px -13px}
      .thumbs{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
      .thumb{position:relative;width:52px;height:52px;border-radius:9px;overflow:hidden;border:1px solid #E5E9EE;background:#F5F7F9}
      .thumb img{width:100%;height:100%;object-fit:cover;display:block}
      .thumb .n{position:absolute;left:3px;top:3px;background:#03C75A;color:#fff;font-size:10px;font-weight:850;border-radius:6px;padding:0 5px;line-height:15px}
      .out{font-size:11.5px;font-weight:700;margin-top:8px;min-height:15px;line-height:1.5;color:#4E5968}
      .out.ok{color:#03C75A} .out.err{color:#E03131}
      .out.work{color:#B7791F;background:#FBF3E4;border:1px solid #F0E0BC;border-radius:9px;padding:8px 10px;font-weight:850;text-align:center}
      .foot{font-size:10px;color:#B0B8C1;margin-top:9px;line-height:1.5;text-align:center}
      .pill{display:none;align-items:center;gap:6px;background:#03C75A;color:#fff;border-radius:999px;padding:9px 14px;font-size:12.5px;font-weight:850;cursor:pointer;box-shadow:0 6px 18px rgba(3,199,90,.35)}
      :host(.min) .wrap{display:none} :host(.min) .pill{display:inline-flex}
      input[type=file]{display:none}
    </style>
    <div class="wrap">
      <div class="hd"><span class="t">🧩 시공막내 · <b>네이버 넣기</b></span><button class="x" id="min" title="접기">—</button></div>
      <div class="body">
        <button class="btn load" id="loadBtn">⬇ 시공막내에서 생성한 글 불러오기</button>
        <div id="phoneRow" style="display:none;margin-top:7px">
          <input type="text" id="phone" class="titleInput" placeholder="내 전화번호 (한 번만 · 예 01012345678)">
          <button class="mini" id="phoneSave" style="width:100%;padding:7px">저장하고 불러오기</button>
        </div>
        <div class="sep"></div>
        <div class="row" style="margin-bottom:5px"><span class="lbl">제목</span></div>
        <input type="text" id="title" class="titleInput" placeholder="블로그 글 제목 (선택)">
        <div class="row"><span class="lbl">네이버에 넣을 글</span><button class="mini" id="sample">예시 넣기</button></div>
        <textarea id="text" placeholder="시공막내에서 만든 블로그 글을 여기에 붙여넣으세요."></textarea>
        <div class="hint"><b>##</b> 소제목 · <b>&gt;</b> 인용구 · <b>**굵게**</b> · <b>---</b> 구분선 — 서식 그대로</div>
        <button class="btn" id="go">✨ 자동으로 넣기</button>

        <div class="sep"></div>
        <div class="row"><span class="lbl">사진 — 글의 <b style="color:#03C75A">[번호]</b> 자리로</span><button class="mini" id="pick">🖼 사진 고르기</button></div>
        <input type="file" id="file" accept="image/*" multiple>
        <div class="thumbs" id="thumbs"></div>
        <button class="btn sub" id="goPhoto">📷 사진 넣기</button>

        <button class="btn save" id="goSave">💾 임시저장</button>

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

  let photos = []; // [{index, blob, name}]
  function renderThumbs() {
    const t = $("#thumbs"); t.innerHTML = "";
    photos.forEach((p) => {
      const d = document.createElement("div"); d.className = "thumb";
      const img = document.createElement("img"); img.src = URL.createObjectURL(p.blob);
      const n = document.createElement("span"); n.className = "n"; n.textContent = String(p.index);
      d.appendChild(img); d.appendChild(n); t.appendChild(d);
    });
  }

  $("#sample").addEventListener("click", () => { $("#text").value = SAMPLE; setOut("", ""); });
  $("#min").addEventListener("click", () => host.classList.add("min"));
  $("#pill").addEventListener("click", () => host.classList.remove("min"));
  $("#pick").addEventListener("click", () => $("#file").click());
  $("#file").addEventListener("change", (e) => {
    const files = Array.prototype.slice.call(e.target.files || []);
    photos = files.map((f, i) => ({ index: i + 1, blob: f, name: f.name }));
    renderThumbs();
    setOut(photos.length ? `사진 ${photos.length}장 준비됨` : "", "");
  });

  // ── 시공막내 글 불러오기 (서버에서 제목·글·사진 당겨오기) ──
  function getPhone() { return new Promise((res) => { try { chrome.storage.local.get("sgmPhone", (o) => res((o && o.sgmPhone) || "")); } catch (e) { res(""); } }); }
  function savePhone(p) { return new Promise((res) => { try { chrome.storage.local.set({ sgmPhone: p }, () => res()); } catch (e) { res(); } }); }
  const dataUrlToBlob = (u) => fetch(u).then((r) => r.blob());

  async function doLoad() {
    const phone = await getPhone();
    if (!phone) { $("#phoneRow").style.display = "block"; setOut("내 전화번호를 한 번만 입력해 주세요(생성 글 찾기용).", ""); return; }
    setOut("⏳ 불러오는 중…", "work");
    const resp = await send({ type: "SGM_LOAD", phone });
    if (!resp || !resp.ok) { setOut("아직 못 불러와요: " + ((resp && resp.error) || "오류") + " (웹 글만들기 배포 후 돼요)", "err"); return; }
    if (resp.title) $("#title").value = resp.title;
    if (resp.draft) $("#text").value = resp.draft;
    photos = [];
    if (Array.isArray(resp.photos) && resp.photos.length) {
      for (const ph of resp.photos) { try { const b = await dataUrlToBlob(ph.dataUrl); photos.push({ index: ph.index, blob: b, name: "photo" + ph.index }); } catch (e) {} }
      renderThumbs();
    }
    setOut(`✓ 불러왔어요! 제목·글${photos.length ? ` · 사진 ${photos.length}장` : ""} — 확인 후 [자동으로 넣기]`, "ok");
  }
  $("#loadBtn").addEventListener("click", doLoad);
  $("#phoneSave").addEventListener("click", async () => {
    const p = ($("#phone").value || "").replace(/\D/g, "");
    if (p.length < 9) { setOut("전화번호를 확인해 주세요.", "err"); return; }
    await savePhone(p); $("#phoneRow").style.display = "none"; doLoad();
  });

  // ── 글 자동 넣기 ──
  async function doAuto() {
    const draft = $("#text").value.trim();
    if (!draft) { setOut("넣을 글을 먼저 붙여넣어 주세요.", "err"); return; }
    const title = $("#title").value.trim();
    const html = toEditorHtml(draft), plain = toPlainText(draft);
    try {
      await navigator.clipboard.write([new ClipboardItem({ "text/html": new Blob([html], { type: "text/html" }), "text/plain": new Blob([plain], { type: "text/plain" }) })]);
    } catch (e) { try { await navigator.clipboard.writeText(plain); } catch (e2) {} }

    const btn = $("#go"); btn.disabled = true;
    setOut("⏳ 넣는 중 — 끝날 때까지 브라우저를 건드리지 마세요!", "work");
    const resp = await send({ type: "SGM_AUTO", draft, title });
    btn.disabled = false;
    if (!resp || !resp.ok) { setOut("자동 넣기 실패: " + ((resp && resp.error) || "오류"), "err"); return; }
    const h = resp.headApplied || 0, ht = resp.headTotal || 0;
    const head = ht ? ` · 소제목 ${h}/${ht}` + (h < ht ? ` (${resp.diag || "실패"})` : "") : "";
    const titleTxt = resp.title === "ok" ? "제목✓ · " : resp.title === "fail" ? "제목✗(알려주세요) · " : "";
    setOut("✓ " + titleTxt + "본문·굵게·인용구·구분선" + head, "ok");
  }
  $("#go").addEventListener("click", doAuto);

  // 원고에서 자리표 묶음 파싱: 한 줄에 붙은 '[1] [2]' = 한 묶음(나란히), 줄 나뉘면 따로(위아래)
  function parseGroups(draft) {
    const groups = [];
    (draft || "").split("\n").forEach((line) => {
      const runRe = /\[\d+\](?:\s*\[\d+\])*/g; let m;
      while ((m = runRe.exec(line)) !== null) {
        const marker = m[0];
        const indices = (marker.match(/\d+/g) || []).map(Number);
        if (indices.length) groups.push({ marker, indices });
      }
    });
    return groups;
  }
  function loadImg(blob) {
    return new Promise((res, rej) => { const img = new Image(); const url = URL.createObjectURL(blob); img.onload = () => { URL.revokeObjectURL(url); res(img); }; img.onerror = () => { URL.revokeObjectURL(url); rej(new Error("이미지 로드 실패")); }; img.src = url; });
  }
  // 여러 장이면 같은 높이로 맞춰 가로로 이어붙여 한 장 PNG (한 장이면 그대로 PNG)
  async function toGroupPng(blobs, gap) {
    const imgs = await Promise.all(blobs.map(loadImg));
    const targetH = Math.min.apply(null, imgs.map((i) => i.naturalHeight || 1000));
    const g = imgs.length > 1 ? (gap == null ? 8 : gap) : 0;
    const widths = imgs.map((i) => Math.max(1, Math.round(i.naturalWidth * (targetH / (i.naturalHeight || targetH)))));
    const totalW = widths.reduce((a, b) => a + b, 0) + g * (imgs.length - 1);
    const c = document.createElement("canvas"); c.width = totalW; c.height = targetH;
    const ctx = c.getContext("2d"); ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, totalW, targetH);
    let x = 0;
    imgs.forEach((im, idx) => { ctx.drawImage(im, 0, 0, im.naturalWidth, im.naturalHeight, x, 0, widths[idx], targetH); x += widths[idx] + g; });
    return await new Promise((res, rej) => c.toBlob((b) => b ? res(b) : rej(new Error("합치기 실패")), "image/png"));
  }

  // ── 사진 넣기 (클립보드 이미지 + 진짜 Ctrl+V) ──
  function blobToDataUrl(blob) { return new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = () => rej(new Error("읽기 실패")); r.readAsDataURL(blob); }); }

  async function doPhotos() {
    if (!photos.length) { setOut("먼저 사진을 선택하거나 [불러오기] 하세요.", "err"); return; }
    // 한 줄 '[1] [2]'=여러장 묶음(나란히=네이버 업로드 묶기), 단독 '[1]'=한 장(클립보드). 둘 다 개별 이미지=SEO안전.
    const groups = parseGroups($("#text").value);
    const covered = new Set(); groups.forEach((g) => g.indices.forEach((i) => covered.add(i)));
    const leftovers = photos.filter((p) => !covered.has(p.index)).sort((a, b) => a.index - b.index);
    const jobs = groups.map((g) => ({ marker: g.marker, indices: g.indices }))
      .concat(leftovers.map((p) => ({ marker: null, indices: [p.index] })));
    if (!jobs.length) { setOut("사진을 넣을 자리가 없어요.", "err"); return; }

    const btn = $("#goPhoto"); btn.disabled = true;
    let done = 0;
    for (let i = 0; i < jobs.length; i++) {
      const job = jobs[i];
      const blobs = job.indices.map((idx) => (photos.find((p) => p.index === idx) || {}).blob).filter(Boolean);
      if (!blobs.length) continue;
      setOut(`⏳ 사진 ${i + 1}/${jobs.length} 넣는 중 — 건드리지 마세요!`, "work");
      let resp;
      if (blobs.length === 1) {
        let png;
        try { png = await toGroupPng(blobs); } catch (e) { setOut(`사진 ${i + 1} 준비 실패: ${String(e.message || e).slice(0, 40)}`, "err"); break; }
        try { await navigator.clipboard.write([new ClipboardItem({ "image/png": png })]); }
        catch (e) { setOut(`사진 ${i + 1} 클립보드 실패: ${String(e.message || e).slice(0, 40)}`, "err"); break; }
        resp = await send({ type: "SGM_PHOTO", marker: job.marker });
      } else {
        let images;
        try { images = await Promise.all(blobs.map(blobToDataUrl)); } catch (e) { setOut(`사진 ${i + 1} 준비 실패: ${String(e.message || e).slice(0, 40)}`, "err"); break; }
        resp = await send({ type: "SGM_PHOTO_GROUP", marker: job.marker, images });
      }
      if (!resp || !resp.ok) { setOut(`사진 ${i + 1} 실패: ${(resp && resp.error) || "오류"}`, "err"); break; }
      done++;
      await new Promise((r) => setTimeout(r, 700));
    }
    btn.disabled = false;
    if (done === jobs.length) setOut(`✓ 사진 완료! ${jobs.length}묶음 (개별 유지=SEO · 여러장=나란히)`, "ok");
    else if (done > 0) setOut(`${done}/${jobs.length}묶음 넣음 — 나머지 실패(알려주세요)`, "err");
  }
  $("#goPhoto").addEventListener("click", doPhotos);

  // ── 임시저장 (Ctrl+Shift+S) ──
  async function doSave() {
    const btn = $("#goSave"); btn.disabled = true;
    setOut("임시저장 중…");
    const resp = await send({ type: "SGM_SAVE" });
    btn.disabled = false;
    if (!resp || !resp.ok) { setOut("임시저장 실패: " + ((resp && resp.error) || "오류"), "err"); return; }
    setOut("✓ 임시저장 완료! (발행은 직접)", "ok");
  }
  $("#goSave").addEventListener("click", doSave);

  chrome.runtime.onMessage.addListener((msg) => { if (msg && msg.type === "SGM_TOGGLE") host.classList.toggle("min"); });
})();
