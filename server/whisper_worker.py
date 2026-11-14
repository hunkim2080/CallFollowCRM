#!/usr/bin/env python
"""§26 fix 502 #3 (2026-06-10) — STT subprocess worker.

main.py 가 uvicorn async 안에서 faster-whisper 직접 호출 시 SIGSEGV 로 워커 죽음
(ctranslate2 / numpy / OMP 충돌 추정, traceback 없이 워커 강제 종료).
→ STT 만 별도 Python subprocess 에서 실행. 자식이 죽어도 main.py worker 는 살아있음.

사용: python whisper_worker.py <audio_path>
출력: transcript (stdout, 한 줄씩 segment)
종료코드: 0 = OK / 1 = STT 실패
"""
import os
import sys
import traceback

# 멀티스레드 충돌 회피 (model import 전 설정 필수)
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")


def collapse_runaway(text: str) -> str:
    """무음·노이즈 구간에서 Whisper 가 같은 토막을 수백 번 반복하는 폭주("네 네 네 …")를 접는다.

    한 세그먼트 **안**의 반복 루프는 VAD·condition_on_previous_text=False 로 못 막는다
    (그 둘은 세그먼트 **사이** 루프만 막음). 2026-11-12 사장님 신고: 전문 첫 구간이 '네' 수백 번.

    규칙: 길이 1~3 토막이 **4번 이상 연속** 반복되면 **3번만** 남긴다(뜻 있는 반복은 보존).
    순수 문자열 처리라 faster-whisper 버전과 무관하게 안전(파라미터 추가로 STT 자체가 깨질 위험 없음).
    """
    toks = (text or "").split()
    n = len(toks)
    if n < 8:               # 짧은 세그먼트는 손대지 않는다(오탐 방지)
        return text
    out = []
    i = 0
    while i < n:
        best_w, best_reps = 1, 1
        for w in (1, 2, 3):
            if i + w > n:
                break
            unit = toks[i:i + w]
            reps, j = 1, i + w
            while j + w <= n and toks[j:j + w] == unit:
                reps += 1
                j += w
            if reps >= 4 and reps > best_reps:
                best_w, best_reps = w, reps
        if best_reps >= 4:
            out.extend(toks[i:i + best_w] * 3)   # 폭주 → 3번만 남김
            i += best_w * best_reps
        else:
            out.append(toks[i])
            i += 1
    return " ".join(out)


def _is_hangul(ch: str) -> bool:
    o = ord(ch)
    return (0xAC00 <= o <= 0xD7A3) or (0x1100 <= o <= 0x11FF) or (0x3130 <= o <= 0x318F)


def _ascii_latin(ch: str) -> bool:
    return ("A" <= ch <= "Z") or ("a" <= ch <= "z")


def strip_foreign_hallucination(text: str) -> str:
    """무음·노이즈 구간에서 Whisper 가 한국어 울타리(language="ko")를 넘어 뱉는 외국어 환각을 걸러낸다.

    실제 신고(2026-11-14 사장님): 대화가 없던 통화 전문에 '先生 / immigrant / Pode' 가 섞여 나옴.
    한국어로 고정된 전문에 **한자·외톨이 영어 단어**가 나오면 거의 100% 받아쓰기 환각이다.

    규칙(순수 문자열 처리 — collapse_runaway 와 같은 자리, faster-whisper 버전 무관·STT 안 깨짐):
      ① 글자 단위: 한글도 ASCII 영문도 아닌 **다른 나라 글자**(한자·가나·키릴 등)는 지운다.
         ('에先生' 처럼 한글에 붙어 나와도 한자만 떨어낸다 → '에')
      ② 토막 단위: **순수 영문 단어**(길이 2+·숫자 없음)는 통째로 뺀다 (immigrant·Pode).
         숫자가 섞인 토막(10cm·A4)·기호 섞인 토막(A/S)은 **남긴다** — 치수·용어 보존.
    """
    if not text:
        return text
    # ① 글자 단위 — 비한글·비ASCII 글자(한자 등) 제거
    cleaned = "".join(
        ch for ch in text
        if not (ch.isalpha() and not _is_hangul(ch) and not _ascii_latin(ch))
    )
    # ② 토막 단위 — 순수 영문 단어(길이 2+) 제거
    out = []
    for tok in cleaned.split():
        if len(tok) >= 2 and all(_ascii_latin(c) for c in tok):
            continue
        out.append(tok)
    return " ".join(out)


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: whisper_worker.py <audio_path>", file=sys.stderr)
        return 2
    audio_path = sys.argv[1]
    vocab = sys.argv[2] if len(sys.argv) > 2 else ""   # 사장님 품목 단어 힌트(Whisper initial_prompt, 2026-10-31 사장님)
    if not os.path.exists(audio_path):
        print(f"audio file not found: {audio_path}", file=sys.stderr)
        return 2

    try:
        from faster_whisper import WhisperModel
    except ImportError as e:
        print(f"faster-whisper 미설치: {e}", file=sys.stderr)
        return 3

    try:
        # base → small: 한국어 정확도 대폭↑ (뭉개짐 감소). CPU라 통화당 몇 초 더 걸리지만 배경 처리라 OK. (2026-09-03 사장님)
        model = WhisperModel(
            "small",
            device="cpu",
            compute_type="default",
            cpu_threads=1,
            num_workers=1,
        )
        # VAD(무음 감지) ON — 조용한 구간을 건너뛰어 Whisper 무음 환각("네네네" 반복) 제거. (2026-09-03 사장님)
        #   beam_size 1→5 정확도↑(뭉개짐 감소). condition_on_previous_text=False 유지(반복 루프 방지).
        segments, _info = model.transcribe(
            audio_path,
            language="ko",
            beam_size=5,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=500),
            condition_on_previous_text=False,
            initial_prompt=(vocab or None),
        )
        # 세그먼트별 시각(start)+텍스트를 JSON 으로 출력 (탭재생 start_ms 용).
        # main.py 가 JSON 파싱; 실패 시 flat 텍스트로 폴백(하위호환).
        import json as _json
        segs_out = []
        for seg in segments:
            t = collapse_runaway((seg.text or "").strip())   # 반복 폭주("네 네 네 …") 접기 (2026-11-12)
            t = strip_foreign_hallucination(t)               # 외국어 환각(先生·immigrant·Pode) 걸러내기 (2026-11-14)
            if t:
                segs_out.append({
                    "start": round(float(getattr(seg, "start", 0) or 0), 2),
                    "text": t,
                })
        try:
            print(_json.dumps(segs_out, ensure_ascii=False), flush=True)
        except Exception:
            print("\n".join(s["text"] for s in segs_out).strip(), flush=True)
        return 0
    except Exception as e:
        print(f"STT 실패: {type(e).__name__}: {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
