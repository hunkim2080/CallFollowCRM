# SERVER HANDOFF — 녹음(m4a) → 통화요약 (무료 STT)

작성: 2026-06-08 · 안드로이드 Claude → 맥미니 Claude
관련: [[reference_adot]], `app/.../ai/CallSummaryServerRepository.kt`, `app/.../recording/`

## 왜 (배경)

에이닷 **"통화 내용 텍스트 저장"이 유료**로 확인됨. **녹음 파일(m4a) 공유는 무료.**
→ 10만 보급을 위해 **무료 녹음을 받아, 받아쓰기(STT)를 우리 맥미니에서 무료로** 처리하기로 사장님 결정(2026-06-08).
→ 유료 STT API 는 금지. **로컬 Whisper(whisper.cpp 등) = API 비용 0, 전기값만.**

기존 텍스트(txt) 경로(`/api/call-summary`)는 그대로 둠 — 에이닷 유료 쓰는 사람용 fallback.

## 맥미니가 만들 것 — 새 endpoint

`POST /api/call-audio-summary`  (multipart/form-data)

요청 필드:
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `file` | file (audio/m4a, mp3, wav) | ✅ | 통화 녹음 원본 |
| `phone` | str | ✅ | 통화 상대 번호(숫자만) |
| `started_at_ms` | int | ✅ | 통화 시작 epoch ms (캐시 키) |
| `direction` | str | | "incoming"/"outgoing" (기본 incoming) |
| `duration_sec` | int | | 통화 길이(초) |
| `customer_name` | str | | 있으면 요약 품질↑ |
| `customer_memo` | str | | |
| `owner_tone_samples` | str[] (JSON) | | 후속문자 말투 학습용 (최대 10) |

처리:
1. 업로드된 오디오 → **로컬 Whisper 로 한국어 받아쓰기** → transcript(text).
2. 그 transcript 를 **기존 `/api/call-summary` 와 똑같은 Haiku 요약 로직**에 태움 → one_line / bullets / 후속문자.
3. **캐시**: `(phone, "call-audio-summary", started_at_ms)` → 같은 통화 재호출 시 Whisper·LLM 둘 다 0원.
   (앱도 중복 방지하지만, 서버 캐시가 있으면 재업로드돼도 안전.)

응답 JSON — **기존 `/api/call-summary` 와 동일 + `transcript` 추가**:
```json
{
  "one_line": "화장실 바닥·현관·세탁실 시공 견적 및 현장 방문 일정 조율",
  "bullets": ["📍 ...", "💰 ...", "📅 ...", "✅ ..."],
  "suggested_followup_sms": "고객님, 통화 내용 정리드립니다 ...",
  "transcript": "나 00:00 ...\n상대방 00:01 ..."
}
```
- `transcript` 는 앱이 CallSummary.transcriptText / rawText 로 저장(나중에 다시보기용).
- 실패(받아쓰기 실패 등) 시 기존처럼 graceful — 의미 있는 에러 코드/메시지.

타임아웃 참고: 받아쓰기는 통화 길이에 비례(3~4분 통화 ≈ 수~수십 초). 앱은 read timeout 을 길게(예 120s) 잡을 예정 — **서버도 동기 응답이면 오래 걸려도 200 으로 끝까지** 주거나, 비동기면 폴링 방식 제안 주세요(택1, SYNC 에 회신).

## 앱(안드로이드)이 할 것 — 서버 완료 후

- m4a 인입은 이미 됨: `RecordingShareHandler`(녹음 공유 수신) + `AdotFolderScanner`(Music/TPhoneCallRecords 자동 스캔, 폴더연결 UI 만 다시 붙이면 됨).
- 신규: 인입된 m4a 를 위 endpoint 로 업로드 → 응답을 **CallSummary 로 저장**(오늘 만든 txt 경로의 `AdotSummaryImporter` 저장부 재사용).
- 표시는 **오늘 완성됨** — 채팅 통화카드 "AI 요약됨"(불릿 + "이 통화 내용으로 후속 문자 쓰기"), `ChatViewModel.callSummaries`. 음성 경로도 같은 화면에 그대로 뜸.

## 회신 부탁 (SYNC.md 에)

1. 동기/비동기 중 어느 방식으로 줄지.
2. endpoint 경로·필드 이름 이대로 OK 인지(다르면 확정안).
3. Whisper 모델/예상 처리시간(통화 1분당 대략).
