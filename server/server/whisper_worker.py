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


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: whisper_worker.py <audio_path>", file=sys.stderr)
        return 2
    audio_path = sys.argv[1]
    if not os.path.exists(audio_path):
        print(f"audio file not found: {audio_path}", file=sys.stderr)
        return 2

    try:
        from faster_whisper import WhisperModel
    except ImportError as e:
        print(f"faster-whisper 미설치: {e}", file=sys.stderr)
        return 3

    try:
        model = WhisperModel(
            "base",
            device="cpu",
            compute_type="default",
            cpu_threads=1,
            num_workers=1,
        )
        segments, _info = model.transcribe(
            audio_path,
            language="ko",
            beam_size=1,
            vad_filter=False,
            condition_on_previous_text=False,
        )
        # 세그먼트별 시각(start)+텍스트를 JSON 으로 출력 (탭재생 start_ms 용).
        # main.py 가 JSON 파싱; 실패 시 flat 텍스트로 폴백(하위호환).
        import json as _json
        segs_out = []
        for seg in segments:
            t = (seg.text or "").strip()
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
