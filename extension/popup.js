// 시공막내 — 네이버 블로그 넣기 (popup)
// 활성 탭이 네이버 블로그면, 붙여넣은 글을 content script 로 보내 에디터에 삽입.

const $ = (s) => document.querySelector(s);

function isNaverEditor(url) {
  return /^https:\/\/blog\.naver\.com\//.test(url || "");
}

async function getActiveTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

async function refreshStatus() {
  const tab = await getActiveTab();
  const chip = $("#chip");
  if (isNaverEditor(tab && tab.url)) {
    chip.textContent = "● 네이버 블로그 탭 감지됨";
    chip.className = "chip ok";
  } else {
    chip.textContent = "○ 네이버 블로그 글쓰기 탭을 열어주세요";
    chip.className = "chip off";
  }
}

function setOut(msg, kind) {
  const out = $("#out");
  out.textContent = msg;
  out.className = "out" + (kind ? " " + kind : "");
}

async function doInsert() {
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

  chrome.tabs.sendMessage(tab.id, { type: "SGM_INSERT", text }, (resp) => {
    btn.disabled = false;
    if (chrome.runtime.lastError || !resp || !resp.ok) {
      setOut("에디터를 못 찾았어요. 본문을 한 번 클릭한 뒤 다시 시도해 주세요.", "err");
      return;
    }
    setOut("✓ 넣었어요! 네이버 창에서 확인하세요.", "ok");
  });
}

document.addEventListener("DOMContentLoaded", () => {
  refreshStatus();
  $("#btn").addEventListener("click", doInsert);
});
