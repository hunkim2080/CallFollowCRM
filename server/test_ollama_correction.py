#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
통화 받아쓰기(STT) 보정 — 로컬 Ollama(qwen2.5:7b) 품질 테스트.
android 핸드오프(2026-08-14 21:40): 유료(Sonnet) 대신 공짜 Ollama 먼저 테스트.

실행(맥미니, Ollama 켜져 있어야 함):
    python3 server/test_ollama_correction.py
결과 → _ollama_correction_out.txt (cowork 에게 "보정 결과 봐줘" 하세요)

실제 통화로 보려면:
    _ollama_test_input.txt 에 통화 transcript 를 넣고( "----" 로 여러 개 구분 ) 다시 실행.
    파일 없으면 아래 내장 샘플(시공 통화 흉내)로 돌아감.
아무것도 안 바꾸는 읽기 전용 테스트.
"""
import json
import os
import time
import urllib.request

OLLAMA = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.environ.get("OLLAMA_CORRECT_MODEL", "qwen2.5:7b")
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
IN_FILE = os.path.join(REPO, "_ollama_test_input.txt")
OUT_FILE = os.path.join(REPO, "_ollama_correction_out.txt")

PROMPT = (
    "너는 한국어 통화 받아쓰기(STT) 결과를 다듬는 교정기야.\n"
    "아래 통화 텍스트의 오타·띄어쓰기·'들리는 대로' 적힌 부분만 자연스러운 한국어로 고쳐줘.\n"
    "규칙: (1) 뜻을 절대 바꾸지 마. (2) 없는 내용을 지어내지 마. "
    "(3) 요약·화자구분·해설 하지 말고 문장만 다듬어. (4) 고친 문장만 출력.\n\n"
    "통화 텍스트:\n{body}\n\n다듬은 텍스트:"
)

SAMPLES = [
    "여보세요 예 사장님 저기 지난번에 화장실 줄눈 문의 드렸던 사람인데요 "
    "그 이십사평 이고 화장실 두갠데 견적 좀 다시 부탁 드릴려구요 "
    "네 다음주 수요일 오전에 가능 하실까요",
    "아 예 도배 하려구 하는데 방 세개랑 거실 이구요 "
    "평수는 삼십사평 정도 되구 실크 로 하면 얼마 정도 나올까요 "
    "지금 집이 비어 있어서 아무때나 시공 가능 합니다",
    "사장님 저번에 견적 받은 데요 계약금 십만원 보냈는데 확인 좀 부탁 드려요 "
    "잔금은 시공 끝나고 드리면 되죠 네네 감사 합니다",
]


def correct(text):
    body = json.dumps({
        "model": MODEL,
        "prompt": PROMPT.format(body=text.strip()),
        "stream": False,
        "options": {"temperature": 0},
    }).encode("utf-8")
    req = urllib.request.Request(
        OLLAMA + "/api/generate", data=body,
        headers={"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as resp:
        out = json.loads(resp.read().decode("utf-8"))
    return (out.get("response") or "").strip(), time.time() - t0


def load_inputs():
    if os.path.exists(IN_FILE):
        raw = open(IN_FILE, encoding="utf-8").read()
        parts = [p.strip() for p in raw.split("----") if p.strip()]
        if parts:
            return parts, "실제 입력(_ollama_test_input.txt)"
    return SAMPLES, "내장 샘플(시공 통화 흉내)"


def main():
    inputs, src = load_inputs()
    lines = []
    lines.append("===== Ollama 통화보정 품질 테스트 · %s =====" % time.strftime("%Y-%m-%d %H:%M:%S"))
    lines.append("모델=%s  서버=%s  입력=%s  건수=%d" % (MODEL, OLLAMA, src, len(inputs)))
    # ollama 살아있는지
    try:
        with urllib.request.urlopen(OLLAMA + "/api/tags", timeout=5) as r:
            tags = json.loads(r.read().decode("utf-8"))
        names = [m.get("name", "") for m in tags.get("models", [])]
        lines.append("설치된 모델: " + ", ".join(names))
        if not any(MODEL.split(":")[0] in n for n in names):
            lines.append("⚠️ %s 안 보임 → `ollama pull %s` 필요할 수 있음" % (MODEL, MODEL))
    except Exception as e:
        lines.append("⚠️ Ollama 응답 없음(%s). `ollama serve` 켜졌는지 확인." % e)
        open(OUT_FILE, "w", encoding="utf-8").write("\n".join(lines) + "\n")
        print("완료(실패) →", OUT_FILE)
        return
    lines.append("")
    for i, txt in enumerate(inputs, 1):
        lines.append("──────── [%d] ────────" % i)
        lines.append("BEFORE: " + txt.strip())
        try:
            fixed, dt = correct(txt)
            lines.append("AFTER : " + fixed)
            lines.append("(%.1f초)" % dt)
        except Exception as e:
            lines.append("AFTER : (실패: %s)" % e)
        lines.append("")
    lines.append("판단: AFTER 가 뜻 안 바뀌고 자연스러우면 채택(공짜). 지어내거나 뜻 틀어지면 Gemini Flash 로.")
    open(OUT_FILE, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("완료 →", OUT_FILE, "  (cowork 에게 '보정 결과 봐줘' 하세요)")


if __name__ == "__main__":
    main()
