# SERVER HANDOFF — `/api/call-audio-summary` 가 HTTP 502 (통화녹음 요약 안 됨)

작성: 2026-06-10 · 안드로이드 Claude → 맥미니 Claude
배경: 사장님 "에이닷 통화녹음 공유가 갑자기 안 됨" 실사용 신고. 앱/폰 쪽은 정상, **서버 엔드포인트가 502**.

## 증상 (확정)
에이닷 "녹음 파일 공유" → 앱이 `POST https://api.si0in.kr/api/call-audio-summary` 호출 → **HTTP 502** → 요약/통화카드 안 뜸.

실기기 로그(2026-06-10):
```
W CallAudioSum: server summarize failed: 01090278250_20260610145756.m4a  bytes=559934
W CallAudioSum: java.io.IOException: HTTP 502
W CallAudioSum: server summarize failed: 01033147768_20260610142311.m4a  bytes=9502550
W CallAudioSum: java.io.IOException: HTTP 502
```
- 0.56MB, 9.5MB 둘 다 502 → **파일 크기 문제 아님**(둘 다 실패).

## PC 에서 클라이언트 무관하게 재현 (폰/네트워크 배제)
```
GET  https://api.si0in.kr/            -> 200   (서버 살아있음)
POST https://api.si0in.kr/api/call-audio-summary  (빈 바디)     -> 422  (FastAPI 검증 정상 = 요청이 앱 코드까지 도달)
POST https://api.si0in.kr/api/call-audio-summary  (file+phone+started_at_ms+direction+duration_sec, 작은 더미) -> 502, 3.4s, body "error code: 502"
```
- body `error code: 502` = **Cloudflare** 가 원본(맥미니)에서 502/연결끊김을 받음.
- 검증(422)은 통과 후 **핸들러 진입 뒤 ~3초 만에 502** → **call-audio-summary 핸들러 실행 중 워커가 죽거나 원본이 연결을 끊는다**는 뜻.

## 추정 원인 (맥미니가 확인할 것)
1. **로컬 Whisper STT 단계 크래시/행** — 모델 미로딩, STT 바이너리/프로세스 다운, OOM(특히 9.5MB), 예외 미처리 → uvicorn 워커 사망 → Cloudflare 502.
2. uvicorn/launchd `com.detailline.ringgo-server` 워커가 이 엔드포인트에서 죽고 자동 재시작 중일 수 있음.
3. (가능성 낮음) Cloudflare ↔ 원본 사이 업로드 타임아웃 — 단 3초 502 라 타임아웃보다 **즉시 크래시** 쪽.

## 맥미니 체크리스트
- [ ] uvicorn/FastAPI 로그에서 15:11~15:12(KST) `/api/call-audio-summary` 트레이스백 확인.
- [ ] 로컬 Whisper STT 서비스/프로세스 살아있나? 모델 파일 경로/로딩 OK?
- [ ] 작은 더미 m4a 로 직접 호출해 핸들러 내부 어디서 죽는지(STT? LLM?) 격리.
- [ ] (참고) Cloudflare/nginx origin 에러 로그: upstream closed connection vs timeout.
- [ ] 고쳐지면 SYNC 회신 — 사장님이 같은 녹음 다시 공유해 검증.

## 앱 쪽 상태 (참고)
- 공유 수신/저장/업로드 모두 정상. 녹음은 로컬에 저장됨(요약만 실패).
- 실패 시 사장님께 "통화 요약 서버 오류 — 녹음은 저장됐어요. 잠시 후 다시 시도" 토스트 노출하도록 개선함(이전엔 무반응이라 "안됨"으로 보였음).
- 실패 원인 로깅 추가: `CallAudioSummarizer` 가 예외(HTTP 코드)까지 logcat 에 남김.
