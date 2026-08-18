// 시공막내 — 네이버 블로그 넣기 (상주 패널 · content script)
// 팝업 대신 에디터에 상주(판다랭크식). 안 사라짐.
// [자동으로 넣기] → 클립보드에 서식HTML → background(chrome.debugger) 진짜 Ctrl+V + 소제목 툴바 자동.
// [사진 넣기]   → 고른 사진을 PNG 로 클립보드에 담아 → background 가 진짜 Ctrl+V(이미지 붙여넣기).
// format.js 의 toEditorHtml/toPlainText 사용.

(function () {
  if (window.__sgmPanelMounted) return;

  // 글쓰기 에디터에서만 패널 노출 — 블로그 홈/글읽기 등 다른 페이지에선 안 뜸.
  // (2026-08-17 사장님 지적: "블로그만 들어가면 나오네". 네이버 글쓰기 = ?Redirect=Write 또는 PostWriteForm)
  function isWritePage() {
    try {
      var u = location.href;
      if (/[?&]Redirect=Write/i.test(u) || /PostWriteForm/i.test(u) || /\/postwrite\b/i.test(location.pathname)) return true;
      var mf = document.querySelector("#mainFrame");
      if (mf && /Write|PostWriteForm/i.test(mf.getAttribute("src") || "")) return true;
    } catch (e) {}
    return false;
  }
  if (!isWritePage()) return; // 에디터 아니면 아무것도 mount 안 함

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
      .hd .t{font-size:13.5px;font-weight:850;color:#181D27;flex:1}.hd .t b{color:#03C75A}
      .hd .x{border:0;background:none;font-size:15px;font-weight:800;color:#8B95A1;cursor:pointer;padding:2px 6px;border-radius:7px}
      .hd .x:hover{background:#E9EDF1}
      .body{padding:13px;max-height:76vh;overflow:auto}
      .say{font-size:11px;font-weight:700;color:#4E5968;background:#F5F7F9;border-radius:9px;padding:7px 10px;margin-bottom:10px;line-height:1.5}
      .btn{width:100%;border:0;border-radius:12px;cursor:pointer;font-weight:850}
      .btn:active{transform:scale(.98)}.btn:disabled{background:#C9D0D8;cursor:default;transform:none}
      .hero{background:#03C75A;color:#fff;font-size:15.5px;padding:14px 12px;box-shadow:0 6px 16px rgba(3,199,90,.28)}
      .hero small{display:block;font-size:10.5px;font-weight:700;opacity:.92;margin-top:3px}
      .hero .em{display:inline-block;transition:transform .18s}
      .hero:hover .em{transform:rotate(-14deg)}
      .out{font-size:11.5px;font-weight:700;margin-top:9px;min-height:15px;line-height:1.5;color:#4E5968}
      .out.ok{color:#03C75A}.out.err{color:#E03131}
      .out.work{color:#B7791F;background:#FBF3E4;border:1px solid #F0E0BC;border-radius:9px;padding:8px 10px;font-weight:850;text-align:center}
      .pv{border:1px solid #E5E9EE;border-radius:13px;padding:10px 11px;margin-top:10px}
      .pvHd{display:flex;align-items:center;gap:6px;margin-bottom:8px}
      .pvT{font-size:11.5px;font-weight:850;color:#181D27;flex:1}
      .pvLink{font-size:10px;font-weight:800;color:#3182F6;cursor:pointer;text-decoration:underline}
      .mini{border:1px solid #E5E9EE;background:#fff;border-radius:8px;font-size:10.5px;font-weight:800;color:#4E5968;padding:4px 9px;cursor:pointer}
      .mini:active{transform:scale(.97)}
      .pvRow{display:flex;align-items:center;gap:7px;padding:4px 0;font-size:11.5px}
      .pvRow .k{font-weight:800;color:#8B95A1;flex:none;width:26px}
      .pvRow .v{font-weight:700;color:#181D27;flex:1;min-width:0}
      .pvRow .v.one{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
      .ck{width:16px;height:16px;border-radius:6px;background:#E5E9EE;color:#fff;font-size:10px;font-weight:850;display:inline-flex;align-items:center;justify-content:center;flex:none}
      .pv.loaded .ck{background:#03C75A;animation:ckPop .35s cubic-bezier(.3,1.6,.5,1) backwards}
      .pv.loaded .pvRow:nth-of-type(2) .ck{animation-delay:.08s}
      .pv.loaded .pvRow:nth-of-type(3) .ck{animation-delay:.16s}
      @keyframes ckPop{from{transform:scale(.3);opacity:0}to{transform:scale(1);opacity:1}}
      .thumbs{display:flex;flex-wrap:wrap;gap:5px;margin-top:6px}
      .thumb{position:relative;width:44px;height:44px;border-radius:9px;overflow:hidden;border:1px solid #E5E9EE;background:#F5F7F9}
      .thumb img{width:100%;height:100%;object-fit:cover;display:block}
      .thumb .n{position:absolute;left:3px;top:3px;background:#03C75A;color:#fff;font-size:10px;font-weight:850;border-radius:6px;padding:0 5px;line-height:15px}
      .pvFull{display:none;margin-top:8px;border-top:1px solid #EEF1F4;padding-top:8px;font-size:11.5px;line-height:1.6;color:#4E5968;white-space:pre-wrap;max-height:180px;overflow:auto}
      .pv.open .pvFull{display:block}
      .util{display:flex;gap:6px;margin-top:10px}
      .util .mini{flex:1;padding:7px 0;text-align:center}
      #editBox{display:none;margin-top:10px;border-top:1px dashed #E5E9EE;padding-top:10px}
      .row{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}
      .lbl{font-size:11.5px;font-weight:800;color:#4E5968}
      .titleInput{width:100%;border:1px solid #E5E9EE;border-radius:11px;padding:9px 11px;font-size:13px;font-weight:700;color:#181D27;background:#F5F7F9;outline:none;margin-bottom:9px}
      .titleInput:focus{border-color:#03C75A;background:#fff}
      textarea{width:100%;height:112px;resize:vertical;border:1px solid #E5E9EE;border-radius:11px;padding:10px 11px;font-size:12.5px;line-height:1.6;color:#181D27;background:#F5F7F9;outline:none}
      textarea:focus{border-color:#03C75A;background:#fff}
      .hint{font-size:10px;color:#8B95A1;margin-top:6px;line-height:1.5}.hint b{color:#4E5968}
      .btn.sub{background:#fff;color:#03C75A;border:1.5px solid #03C75A;font-size:13px;padding:10px;margin-top:10px}
      .foot{font-size:10px;color:#B0B8C1;margin-top:9px;line-height:1.5;text-align:center}
      .pill{display:none;align-items:center;gap:6px;background:#03C75A;color:#fff;border-radius:999px;padding:9px 14px;font-size:12.5px;font-weight:850;cursor:pointer;box-shadow:0 6px 18px rgba(3,199,90,.35)}
      :host(.min) .wrap{display:none}:host(.min) .pill{display:inline-flex}
      input[type=file]{display:none}
    </style>
    <div class="wrap">
      <div class="hd"><span class="t">🧑‍🔧 시공막내 · <b>네이버 넣기</b></span><button class="x" id="min" title="접기">—</button></div>
      <div class="body">
        <div class="say" id="say">사장님이 만든 글, 제가 그대로 옮겨 넣을게요!</div>
        <button class="btn hero" id="loadGo"><span class="em">🧑‍🔧</span> 네이버에 넣기<small>제목·글·사진 — 제가 알아서 다 넣어요</small></button>
        <div id="phoneRow" style="display:none;margin-top:8px">
          <input type="text" id="phone" class="titleInput" placeholder="내 전화번호 (한 번만 · 예 01012345678)">
          <button class="mini" id="phoneSave" style="width:100%;padding:7px">저장하고 불러오기</button>
        </div>
        <div class="out" id="out"></div>
        <div class="pv" id="pv">
          <div class="pvHd"><span class="pvT">이게 들어가요</span><a class="pvLink" id="loadOnly">다시 불러오기</a><button class="mini" id="pvToggle">펼쳐보기</button></div>
          <div class="pvRow"><span class="ck">✓</span><span class="k">제목</span><span class="v one" id="pvTitle">확인 중이에요…</span></div>
          <div class="pvRow"><span class="ck">✓</span><span class="k">글</span><span class="v" id="pvMeta">—</span></div>
          <div class="pvRow"><span class="ck">✓</span><span class="k">사진</span><span class="v" id="pvPhoto">—</span></div>
          <div class="thumbs" id="thumbs"></div>
          <div class="pvFull" id="pvFull"></div>
        </div>
        <div class="util">
          <button class="mini" id="editToggle">✏️ 직접 고치기</button>
          <button class="mini" id="goSave">💾 임시저장</button>
        </div>
        <div id="editBox">
          <div class="row" style="margin-bottom:5px"><span class="lbl">제목</span></div>
          <input type="text" id="title" class="titleInput" placeholder="불러오면 자동으로 채워져요">
          <div class="row"><span class="lbl">글</span><button class="mini" id="sample">예시</button></div>
          <textarea id="text" placeholder="불러오면 자동으로 채워져요 (직접 붙여넣어도 돼요)"></textarea>
          <div class="hint"><b>##</b> 소제목 · <b>&gt;</b> 인용구 · <b>**굵게**</b> · <b>---</b> 구분선 · 사진 <b style="color:#03C75A">[번호]</b> 자리</div>
          <div class="row" style="margin-top:11px"><span class="lbl">사진</span><button class="mini" id="pick">직접 고르기</button></div>
          <input type="file" id="file" accept="image/*" multiple>
          <button class="btn sub" id="go">✨ 이 내용으로 넣기</button>
        </div>
        <div class="foot">자동 발행은 안 해요 · 확인하고 발행은 사장님이 직접</div>
      </div>
    </div>
    <div class="pill" id="pill">🧑‍🔧 네이버 넣기</div>
  `;
  document.documentElement.appendChild(host);

  const $ = (s) => shadow.querySelector(s);
  const setOut = (m, k) => { const o = $("#out"); o.textContent = m || ""; o.className = "out" + (k ? " " + k : ""); };
  // 확장 리로드/업데이트 후 옛 content script 가 살아있으면 chrome.runtime 이 무효(context invalidated).
  // → API 호출을 감싸 오류(콘솔·오류탭) 안 나게 하고, 페이지 새로고침 안내.
  const ctxOk = () => { try { return !!(chrome.runtime && chrome.runtime.id); } catch (e) { return false; } };
  const send = (msg) => new Promise((res) => {
    if (!ctxOk()) { setOut("🔄 확장이 업데이트됐어요 — 이 페이지를 새로고침(F5) 후 다시 눌러주세요.", "err"); res(null); return; }
    try { chrome.runtime.sendMessage(msg, (r) => res((chrome.runtime && chrome.runtime.lastError) ? null : r)); } catch (e) { res(null); }
  });

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
  $("#pvToggle").addEventListener("click", () => { const o = $("#pv").classList.toggle("open"); $("#pvToggle").textContent = o ? "접기" : "펼쳐보기"; });
  $("#editToggle").addEventListener("click", () => { const b = $("#editBox"); b.style.display = (b.style.display === "block") ? "none" : "block"; });
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
    if (!phone) { $("#phoneRow").style.display = "block"; setOut("내 전화번호를 한 번만 입력해 주세요(생성 글 찾기용).", ""); return false; }
    setOut("⏳ 불러오는 중…", "work");
    const resp = await send({ type: "SGM_LOAD", phone });
    if (!resp || !resp.ok) { setOut("아직 못 불러와요: " + ((resp && resp.error) || "오류"), "err"); return false; }
    if (resp.title) $("#title").value = resp.title;
    if (resp.draft) $("#text").value = resp.draft;
    photos = [];
    if (Array.isArray(resp.photos) && resp.photos.length) {
      for (const ph of resp.photos) { try { const b = await dataUrlToBlob(ph.dataUrl); photos.push({ index: ph.index, blob: b, name: "photo" + ph.index }); } catch (e) {} }
      renderThumbs();
    }
    // 읽기전용 미리보기 카드 채우기 ('이게 들어가요')
    $("#pvTitle").textContent = resp.title || "(제목 없음)";
    $("#pvMeta").textContent = ((resp.draft || "").split("\n").filter(Boolean).length) + "줄 · 소제목 " + (((resp.draft || "").match(/^## /gm) || []).length) + " · 서식 자동";
    $("#pvPhoto").textContent = photos.length ? (photos.length + "장 · 글 속 [번호] 자리에") : "없음";
    $("#pvFull").textContent = (resp.title ? resp.title + "\n\n" : "") + (resp.draft || "");
    $("#pv").classList.add("loaded");
    setOut(`✓ 불러왔어요! 제목·글${photos.length ? ` · 사진 ${photos.length}장` : ""}`, "ok");
    return true;
  }
  // 바로 넣기 = 불러오기 + 네이버에 넣기 한 방
  async function doLoadAndGo() { const ok = await doLoad(); if (ok) await doAll(); }
  $("#loadGo").addEventListener("click", doLoadAndGo);
  $("#loadOnly").addEventListener("click", doLoad);
  $("#phoneSave").addEventListener("click", async () => {
    const p = ($("#phone").value || "").replace(/\D/g, "");
    if (p.length < 9) { setOut("전화번호를 확인해 주세요.", "err"); return; }
    await savePhone(p); $("#phoneRow").style.display = "none"; doLoad();
  });

  // ── 넣는 중 전체화면 안내 (첫 사용자가 건드려서 깨지는 것 방지) ──
  //   pointer-events:none 라 CDP 진짜클릭/키입력엔 영향 없음(안 깨짐). 크게 보이는 시각 경고 + 진행표시.
  let _busy = null;
  function showBusy(msg) {
    try {
      if (!_busy) {
        _busy = document.createElement("div");
        _busy.style.cssText = "position:fixed;inset:0;z-index:2147483646;pointer-events:none;display:flex;align-items:center;justify-content:center;background:rgba(12,17,26,.34);font-family:-apple-system,BlinkMacSystemFont,'Malgun Gothic',sans-serif";
        _busy.innerHTML = '<div style="background:#fff;border:2px solid #03C75A;border-radius:22px;box-shadow:0 26px 74px rgba(0,0,0,.42);padding:32px 36px;text-align:center;max-width:400px"><div style="font-size:44px;margin-bottom:4px">✨</div><div style="font-size:20px;font-weight:850;color:#03C75A;margin-bottom:10px">🧑‍🔧 시공막내가 넣는 중이에요!</div><div style="font-size:15.5px;font-weight:850;color:#E03131;margin-bottom:6px">🙏 잠깐만요 — 마우스·키보드 건드리지 마세요</div><div id="sgm-busy-msg" style="font-size:13px;font-weight:700;color:#4E5968;line-height:1.6;margin-top:8px;min-height:18px"></div><div style="margin-top:15px;height:7px;background:#EEF1F4;border-radius:4px;overflow:hidden;position:relative"><div style="position:absolute;height:100%;width:40%;background:#03C75A;border-radius:4px;animation:sgmBusy 1.1s ease-in-out infinite"></div></div></div>';
        var stl = document.createElement("style");
        stl.textContent = "@keyframes sgmBusy{0%{left:-40%}100%{left:100%}}";
        _busy.appendChild(stl);
        (document.documentElement || document.body).appendChild(_busy);
      }
      _busy.style.display = "flex";
      var m = _busy.querySelector("#sgm-busy-msg"); if (m) m.textContent = msg || "";
    } catch (e) {}
  }
  function updateBusy(msg) { try { if (_busy && _busy.style.display !== "none") { var m = _busy.querySelector("#sgm-busy-msg"); if (m) m.textContent = msg || ""; } } catch (e) {} }
  function hideBusy() { try { if (_busy) _busy.style.display = "none"; } catch (e) {} }

  // ── 글 + 사진 한번에 넣기 ──
  async function doAll() {
    const draft = $("#text").value.trim();
    const title = $("#title").value.trim();
    if (!draft && !photos.length) { setOut("글이나 사진을 먼저 넣으세요.", "err"); return; }
    const btn = $("#go"); btn.disabled = true;
    setOut("⏳ 시작할게요 — 이 창 건드리지 마세요!", "work");
    showBusy("네이버 에디터 준비 중…");
    try {
      // 1) 글(제목·본문·서식·소제목)
      if (draft) {
        const html = toEditorHtml(draft), plain = toPlainText(draft);
        try { await navigator.clipboard.write([new ClipboardItem({ "text/html": new Blob([html], { type: "text/html" }), "text/plain": new Blob([plain], { type: "text/plain" }) })]); }
        catch (e) { try { await navigator.clipboard.writeText(plain); } catch (e2) {} }
        setOut("⏳ 글 넣는 중 — 건드리지 마세요!", "work");
        const resp = await send({ type: "SGM_AUTO", draft, title });
        if (!resp || !resp.ok) { setOut("글 넣기 실패: " + ((resp && resp.error) || "오류"), "err"); return; }
      }
      // 2) 사진(개별, [n] 자리 · SEO안전)
      let pdone = 0; const ptotal = photos.length;
      if (ptotal) {
        const sorted = photos.slice().sort((a, b) => a.index - b.index);
        for (let i = 0; i < sorted.length; i++) {
          const p = sorted[i];
          const pmsg = `사진 ${i + 1}/${ptotal} 넣는 중…`;
          setOut("⏳ " + pmsg + " — 건드리지 마세요!", "work"); updateBusy("④ " + pmsg);
          let png;
          try { png = await toGroupPng([p.blob]); } catch (e) { setOut(`사진 ${p.index} 준비 실패: ${String(e.message || e).slice(0, 40)}`, "err"); return; }
          try { await navigator.clipboard.write([new ClipboardItem({ "image/png": png })]); }
          catch (e) { setOut(`사진 ${p.index} 클립보드 실패: ${String(e.message || e).slice(0, 40)}`, "err"); return; }
          const r = await send({ type: "SGM_PHOTO", marker: "[" + p.index + "]" });
          if (!r || !r.ok) { setOut(`사진 ${p.index} 실패: ${(r && r.error) || "오류"}`, "err"); return; }
          pdone++;
          await new Promise((rr) => setTimeout(rr, 500));
        }
        if (pdone === ptotal) {  // 여러장 나란히 묶기(실험)
          const mg = parseGroups($("#text").value).filter((g) => g.indices.length > 1);
          if (mg.length) { setOut("⏳ 나란히 묶는 중 — 건드리지 마세요!", "work"); updateBusy("⑤ 사진 나란히 묶는 중…"); await send({ type: "SGM_GROUP_IMAGES", groups: mg.map((g) => g.indices.map((idx) => idx - 1)) }); }
        }
      }
      // 완료 — 패널 + 전체화면 완료 + 뷰어(si0in.kr)로 완료신호(sgmInsertDone)
      const parts = [];
      if (draft) parts.push("글·서식");
      if (ptotal) parts.push(`사진 ${pdone}/${ptotal}`);
      setOut("✓ 다 넣었어요! " + parts.join(" · ") + " — 저 시공막내, 일 좀 하죠? 😎 확인 후 발행만 눌러 주세요", "ok");
      try { chrome.storage.local.set({ sgmInsertDone: { ts: Date.now(), parts: parts.join(" · ") } }); } catch (e) {}
    } finally {
      btn.disabled = false;
      hideBusy();
    }
  }
  $("#go").addEventListener("click", doAll);

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

  try {
    chrome.runtime.onMessage.addListener((msg) => {
      if (!msg) return;
      if (msg.type === "SGM_TOGGLE") host.classList.toggle("min");
      else if (msg.type === "SGM_PROGRESS") { setOut(msg.msg || "", "work"); updateBusy(msg.msg || ""); }  // background 실시간 진행상황
    });
  } catch (e) {}

  // ── 뷰어 [네이버에 넣기]에서 넘어온 자동삽입 예약이면 바로 실행 (완전 원클릭) ──
  //   bridge.js 가 si0in.kr 에서 심어둔 sgmAutoInsert(2분 유효) 를 보고, 패널 뜨자마자 pull+paste.
  try {
    chrome.storage.local.get("sgmAutoInsert", (o) => {
      const ts = o && o.sgmAutoInsert;
      if (ts && Date.now() - ts < 120000) {
        try { chrome.storage.local.remove("sgmAutoInsert"); } catch (e) {}
        host.classList.remove("min");
        setOut("⏳ 시공막내에서 넘어왔어요 — 자동으로 넣는 중…", "work");
        doLoadAndGo();
      } else {
        doLoad();  // 자동삽입 아니면 미리보기만 조용히 채움 (편집 아님)
      }
    });
  } catch (e) { doLoad(); }

  // ── '작성 중인 글이 있습니다' 팝업 자동 닫기 ([취소]=새로 작성) ──
  // content script 는 같은-오리진 iframe(#mainFrame) DOM 에 접근 가능 → 합성 click 으로 닫는다.
  // (2026-08-18 CDP 로 실제 네이버 팝업에 합성 click 먹는 것 검증. 디버거·좌표 불필요.)
  function sgmDismissDraftPopup() {
    try {
      var docs = [document];
      var f = document.querySelector("#mainFrame");
      if (f && f.contentDocument) docs.push(f.contentDocument);
      for (var di = 0; di < docs.length; di++) {
        var doc = docs[di], hasPop = false, els = doc.querySelectorAll("div,p,span,strong,h1,h2,h3");
        for (var i = 0; i < els.length; i++) { var t = els[i].textContent || ""; if (t.indexOf("작성 중") >= 0 && t.length < 90) { hasPop = true; break; } }
        if (!hasPop) continue;
        var btns = doc.querySelectorAll('button,[role="button"],a');
        for (var j = 0; j < btns.length; j++) {
          var b = btns[j], bt = (b.textContent || "").replace(/\s/g, ""), c = (b.className || "") + "";
          if ((bt === "취소" || c.indexOf("cancel") >= 0) && c.indexOf("confirm") < 0) { b.click(); return true; }
        }
      }
    } catch (e) {}
    return false;
  }
  var _sgmPopTries = 0;
  var _sgmPopIv = setInterval(function () { _sgmPopTries++; if (sgmDismissDraftPopup() || _sgmPopTries > 16) clearInterval(_sgmPopIv); }, 400);
})();
