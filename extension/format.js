"use strict";
/**
 * 서식 마커 → 스마트에디터가 먹는 HTML 변환 (네이버 자동화 노하우 format.js 이식).
 *  - "## 소제목"  → <h3>
 *  - "> 문장"     → <blockquote>
 *  - "**강조**"   → <b>
 *  - "---"        → <hr>
 *  - 빈 줄        → 문단 구분
 * 진짜 Ctrl+V(신뢰된 붙여넣기)로 넣으면 에디터가 이 HTML 을 제 구조로 변환 → 서식 유지.
 * (합성 paste/innerText 는 에디터가 거부 → 반드시 실제 클립보드 + 실제 붙여넣기)
 */

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// 굵게 마커 → <b>
function boldEscape(seg) {
  return escapeHtml(seg).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>");
}

// URL 은 <a> 링크로, 굵게는 <b> 로
function inlineHtml(s) {
  const urlRe = /(https?:\/\/[^\s<>()]+)/g;
  let html = "";
  let last = 0;
  let m;
  while ((m = urlRe.exec(s)) !== null) {
    let url = m[1];
    const trail = (url.match(/[.,!?)\]}'"]+$/) || [""])[0];
    if (trail) url = url.slice(0, url.length - trail.length);
    html += boldEscape(s.slice(last, m.index));
    const eu = escapeHtml(url);
    html += '<a href="' + eu + '">' + eu + "</a>";
    last = m.index + url.length;
  }
  html += boldEscape(s.slice(last));
  return html;
}

/** 원고(마커 텍스트) → 스마트에디터 붙여넣기용 HTML */
function toEditorHtml(draft) {
  const blocks = String(draft).replace(/\r\n/g, "\n").split(/\n{2,}/);
  const html = [];
  for (const block of blocks) {
    const lines = block.split("\n").filter((l) => l.trim() !== "");
    if (lines.length === 0) continue;

    // 블록 전체가 인용구
    if (lines.every((l) => /^\s*>/.test(l))) {
      const inner = lines.map((l) => inlineHtml(l.replace(/^\s*>\s?/, ""))).join("<br>");
      html.push("<blockquote>" + inner + "</blockquote>");
      continue;
    }
    for (const line of lines) {
      if (/^\s*-{3,}\s*$/.test(line)) {
        html.push("<hr>");
      } else if (/^\s*#{1,3}\s+/.test(line)) {
        html.push("<h3>" + inlineHtml(line.replace(/^\s*#{1,3}\s+/, "")) + "</h3>");
      } else if (/^\s*>/.test(line)) {
        html.push("<blockquote>" + inlineHtml(line.replace(/^\s*>\s?/, "")) + "</blockquote>");
      } else {
        html.push("<p>" + inlineHtml(line) + "</p>");
      }
    }
    html.push("<p><br></p>"); // 문단 사이 빈 줄
  }
  return html.join("");
}

/** 마커 제거한 순수 텍스트 (text/plain flavor + 글자수) */
function toPlainText(draft) {
  return String(draft)
    .split("\n")
    .filter((line) => !/^\s*-{3,}\s*$/.test(line))
    .map((line) => {
      let l = line;
      l = l.replace(/^\s*#{1,3}\s+/, "");
      l = l.replace(/^\s*>\s?/, "");
      l = l.replace(/\*\*(.+?)\*\*/g, "$1");
      return l;
    })
    .join("\n")
    .trim();
}
