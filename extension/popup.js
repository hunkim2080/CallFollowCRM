// 시공막내 — 네이버 블로그 넣기 (popup)
// 핵심: 네이버는 합성 붙여넣기를 거부하므로, "진짜 클립보드"에 서식 HTML 을 담고
//       사용자가 "진짜 Ctrl+V" 로 붙여넣는다(신뢰된 이벤트라 서식 그대로 들어감).
// format.js 의 toEditorHtml / toPlainText 사용.

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
  out.textContent = msg || "";
  out.className = "out" + (kind ? " " + kind : "");
}

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

// ── 진짜 클립보드에 HTML+텍스트 두 flavor 로 담기 ──
async function copyForNaver() {
  const draft = $("#text").value.trim();
  if (!draft) { setOut("넣을 글을 먼저 붙여넣어 주세요.", "err"); return; }

  const html = toEditorHtml(draft);
  const plain = toPlainText(draft);
  const btn = $("#btn");
  btn.disabled = true;
  try {
    const item = new ClipboardItem({
      "text/html": new Blob([html], { type: "text/html" }),
      "text/plain": new Blob([plain], { type: "text/plain" }),
    });
    await navigator.clipboard.write([item]);
    $("#paste").style.display = "block";
    setOut("", "");
  } catch (e) {
    // 혹시 write 실패 시 최소한 텍스트라도
    try {
      await navigator.clipboard.writeText(plain);
      $("#paste").style.display = "block";
      setOut("서식 없이 텍스트만 복사됐어요(브라우저 제한). Ctrl+V 하세요.", "err");
    } catch (e2) {
      setOut("복사 실패: " + String(e2).slice(0, 80), "err");
    }
  } finally {
    btn.disabled = false;
  }
}

// ── 실험: 합성 paste 자동 시도 (네이버가 거부하면 실패 표시) ──
// 네이버 프레임 안에서 실행 (executeScript). 검증된 셀렉터 사용.
function sgmAutoPaste(payload) {
  const html = payload.html, plain = payload.plain;
  const out = { url: location.href, found: false, changed: false };
  let ed = document.querySelector('.se-main-container [contenteditable="true"]');
  if (!ed) {
    const eds = Array.prototype.slice.call(document.querySelectorAll('[contenteditable="true"]'))
      .filter((e) => !e.closest(".se-section-documentTitle"));
    eds.sort((a, b) => (b.innerText || "").length - (a.innerText || "").length);
    ed = eds[0] || document.querySelector(".se-content, .se-container, .se-main-container");
  }
  if (!ed) return out;
  out.found = true;
  const before = (ed.innerText || "");
  try {
    ed.focus();
    const dt = new DataTransfer();
    dt.setData("text/html", html);
    dt.setData("text/plain", plain);
    ed.dispatchEvent(new ClipboardEvent("paste", { clipboardData: dt, bubbles: true, cancelable: true }));
  } catch (e) { out.err = String(e).slice(0, 100); }
  out.changed = (ed.innerText || "") !== before;
  return out;
}

async function tryAuto() {
  const draft = $("#text").value.trim();
  if (!draft) { setOut("먼저 글을 넣어주세요.", "err"); return; }
  const tab = await getActiveTab();
  if (!isNaverEditor(tab && tab.url)) {
    setOut("네이버 블로그 글쓰기 창을 먼저 열어주세요.", "err");
    return;
  }
  setOut("자동 시도 중…");
  const html = toEditorHtml(draft);
  const plain = toPlainText(draft);
  let results;
  try {
    results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: sgmAutoPaste,
      args: [{ html, plain }],
    });
  } catch (e) {
    setOut("자동 시도 오류: " + String(e).slice(0, 80), "err");
    return;
  }
  const frames = (results || []).map((r) => r && r.result).filter(Boolean);
  const changed = frames.find((f) => f.changed);
  if (changed) {
    setOut("✓ 자동으로 들어갔어요! 네이버 창에서 확인하세요.", "ok");
  } else {
    const found = frames.find((f) => f.found);
    setOut(found
      ? "자동은 네이버가 막았어요(정상). 위 [네이버용으로 복사] 후 본문에서 Ctrl+V 하세요."
      : "에디터를 못 찾았어요. 글쓰기 본문을 한 번 클릭한 뒤 다시 시도해 주세요.", "err");
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
  $("#btn").addEventListener("click", copyForNaver);
  $("#autoBtn").addEventListener("click", tryAuto);
  $("#sample").addEventListener("click", () => { $("#text").value = SAMPLE; $("#paste").style.display = "none"; setOut("", ""); });
});
