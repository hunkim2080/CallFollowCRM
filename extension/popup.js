// 시공막내 — 네이버 블로그 넣기 (popup)
// scripting.executeScript(allFrames) 로 네이버 모든 프레임에 주입 → 프레임별 결과 취합.
// 성공하면 ✓, 실패하면 프레임 진단을 팝업에 띄워 원인 파악.

const $ = (s) => document.querySelector(s);

function isNaverEditor(url) {
  return /^https:\/\/blog\.naver\.com\//.test(url || "");
}

async function getActiveTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

function setOut(msg, kind) {
  const out = $("#out");
  out.textContent = msg;
  out.className = "out" + (kind ? " " + kind : "");
}

function showDiag(text) {
  $("#diagWrap").style.display = "block";
  $("#diag").textContent = text;
}
function hideDiag() {
  $("#diagWrap").style.display = "none";
}

// ── 네이버 프레임 안에서 실행될 함수 (executeScript 로 주입; 클로저 못 씀, args 로만) ──
function sgmInject(payload) {
  const text = (payload && payload.text) || "";
  const rect = (el) => { const r = el.getBoundingClientRect(); return { w: Math.round(r.width), h: Math.round(r.height) }; };
  const out = { url: location.href, ce: 0, ceInfo: [], iframes: document.querySelectorAll("iframe").length, inserted: false, method: null, editorClass: null };

  const ces = Array.prototype.slice.call(document.querySelectorAll('[contenteditable="true"],[contenteditable=""]'));
  out.ce = ces.length;
  out.ceInfo = ces.slice(0, 6).map(function (el) { const d = rect(el); return { cls: String(el.className || "").slice(0, 60), tag: el.tagName, w: d.w, h: d.h }; });

  // 편집영역 = 화면에서 가장 큰 contenteditable (본문). 제목은 작음.
  function pick() {
    let best = null, area = 0;
    for (let j = 0; j < ces.length; j++) { const d = rect(ces[j]); const a = d.w * d.h; if (a > area && d.h > 40) { area = a; best = ces[j]; } }
    return best;
  }
  const ed = pick();
  if (!ed) return out;
  out.editorClass = String(ed.className || "").slice(0, 60);

  function placeCaret(node) {
    try {
      const sel = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(node);
      range.collapse(false); // 맨 끝
      sel.removeAllRanges();
      sel.addRange(range);
    } catch (e) {}
  }

  const before = ed.textContent || "";

  // 방법1: execCommand insertText (+ 커서 강제)
  try {
    ed.focus(); placeCaret(ed);
    const paras = String(text).replace(/\r/g, "").split(/\n+/);
    for (let i = 0; i < paras.length; i++) {
      if (paras[i]) document.execCommand("insertText", false, paras[i]);
      if (i < paras.length - 1) document.execCommand("insertParagraph", false);
    }
    if ((ed.textContent || "") !== before) { out.inserted = true; out.method = "execCommand"; return out; }
  } catch (e) { out.err1 = String(e).slice(0, 120); }

  // 방법2: beforeinput(insertText) 이벤트
  try {
    ed.focus(); placeCaret(ed);
    const ev = new InputEvent("beforeinput", { inputType: "insertText", data: text, bubbles: true, cancelable: true });
    ed.dispatchEvent(ev);
    if ((ed.textContent || "") !== before) { out.inserted = true; out.method = "beforeinput"; return out; }
  } catch (e) { out.err2 = String(e).slice(0, 120); }

  // 방법3: insertHTML (줄바꿈 <br>)
  try {
    ed.focus(); placeCaret(ed);
    const html = String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/\n/g, "<br>");
    document.execCommand("insertHTML", false, html);
    if ((ed.textContent || "") !== before) { out.inserted = true; out.method = "insertHTML"; return out; }
  } catch (e) { out.err3 = String(e).slice(0, 120); }

  return out;
}

async function run() {
  const text = $("#text").value.trim();
  if (!text) { setOut("넣을 글을 먼저 붙여넣어 주세요.", "err"); return; }

  const tab = await getActiveTab();
  if (!isNaverEditor(tab && tab.url)) {
    setOut("네이버 블로그 글쓰기 창(blog.naver.com)을 먼저 열어주세요.", "err");
    return;
  }

  const btn = $("#btn");
  btn.disabled = true;
  setOut("넣는 중…");
  hideDiag();

  let results;
  try {
    results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: sgmInject,
      args: [{ text }],
    });
  } catch (e) {
    btn.disabled = false;
    setOut("실행 오류: " + String(e).slice(0, 90), "err");
    return;
  }
  btn.disabled = false;

  const frames = (results || []).map((r) => r && r.result).filter(Boolean);
  const ok = frames.find((f) => f.inserted);
  if (ok) {
    setOut("✓ 넣었어요! (" + ok.method + ") 네이버 창에서 확인하세요.", "ok");
    hideDiag();
    return;
  }

  // 실패 → 진단 표시 (사장님이 복사해서 클로드에게)
  const lines = frames.map((f) => {
    const tail = (f.url || "").split("/").pop().slice(0, 28);
    const cand = (f.ceInfo || []).map((c) => c.cls + "(" + c.w + "x" + c.h + ")").join(" · ") || "-";
    const errs = [f.err1, f.err2, f.err3].filter(Boolean).join(" / ");
    return "[" + tail + "] ce=" + f.ce + " iframe=" + f.iframes + " editor=" + (f.editorClass || "없음") +
      "\n  후보: " + cand + (errs ? "\n  err: " + errs : "");
  });
  const diag = "=== 시공막내 확장 진단 ===\n" + (lines.join("\n") || "프레임 응답 없음(에디터 프레임 미주입)");
  setOut("아직 안 들어갔어요. 아래 [진단 복사] 눌러서 저에게 붙여주세요 👇", "err");
  showDiag(diag);
}

async function copyDiag() {
  try {
    await navigator.clipboard.writeText($("#diag").textContent || "");
    $("#copy").textContent = "복사됨 ✓";
    setTimeout(() => { $("#copy").textContent = "진단 복사"; }, 1500);
  } catch (e) {
    // 클립보드 실패 시 선택
    const r = document.createRange(); r.selectNodeContents($("#diag"));
    const s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
  }
}

document.addEventListener("DOMContentLoaded", async () => {
  const tab = await getActiveTab();
  const chip = $("#chip");
  if (isNaverEditor(tab && tab.url)) {
    chip.textContent = "● 네이버 블로그 탭 감지됨";
    chip.className = "chip ok";
  } else {
    chip.textContent = "○ 네이버 블로그 글쓰기 탭을 열어주세요";
    chip.className = "chip off";
  }
  $("#btn").addEventListener("click", run);
  $("#copy").addEventListener("click", copyDiag);
});
