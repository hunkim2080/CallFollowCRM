# ANDROID HANDOFF — 앱 회원가입 (SMS 인증번호) · 2026-07-03

> 서버측 완료 (추가86, cowork). 앱이 이 두 endpoint 만 붙이면 가입 flow 완성.
> 배경: Play Store 공개 → 불특정 다수 다운로드 → 폰번호 인증 가입.
> 사장님 결정 사항: 답장 방식 대신 **인증번호 방식** + **선착 자동 등업 (cap) + 대기열**.

---

## Flow (사용자 관점)

1. 첫 실행 → 전화번호 입력 → [인증번호 받기]
2. 문자 도착: `[시공막내] 인증번호 [382910] 를 입력해주세요. (5분 이내)`
3. 6자리 입력 → 서버 검증 → 결과 3갈래:
   - **enrolled** — 🎉 "가입 완료! 2개월 무료로 시작해요" → 온보딩 진입
   - **member** — 기존 회원 (재설치/재인증) → 바로 홈
   - **waitlisted** — "지금은 대기열이에요. 자리가 나면 문자로 알려드릴게요" → 대기 화면
4. 이후 앱 진입 게이트는 기존 `/api/beta/check` 그대로 (whitelist 등록됐으니 통과).

## Endpoints

### ① POST /api/auth/request-code
```json
req:  { "phone": "010-1234-5678" }     // 하이픈 있어도 됨
res:  { "ok": true, "expiresInSec": 300 }
```
에러 (detail 문구 그대로 토스트 권장):
- 400 전화번호 형식 오류
- 429 "잠시 후 다시 요청해주세요 (1분 간격)" — 재발송 버튼 60초 카운트다운 권장
- 429 "오늘 이 번호의 인증 요청 한도를 넘었어요 (하루 5회)"
- 503 문자 발송 설정 안 됨 (서버 env 미설정 — 배포 초기에만 발생 가능)
- 502 발송 실패 (SOLAPI 장애)

### ② POST /api/auth/verify-code
```json
req:  { "phone": "01012345678", "code": "382910" }
res (3갈래):
  { "ok": true, "status": "enrolled",  "freeUntilMs": 1788000000000, "freeDays": 60 }
  { "ok": true, "status": "member",    "freeUntilMs": 1788000000000 }   // null 가능 (옛 베타)
  { "ok": true, "status": "waitlisted" }
```
에러: 400 "인증번호가 틀렸어요" / 400 만료 / 429 "시도 횟수 초과" (5회) → 재요청 유도.

## 앱 구현 메모

- **SMS Retriever API 권장** — READ_SMS 권한 없이 인증문자 자동 입력 (Play 심사에 유리).
  단 우리 앱은 이미 READ_SMS 있으니 자동 감지 fallback 도 쉬움. 어느 쪽이든 수동 입력은 항상 가능하게.
- `freeUntilMs` 저장해두면 앱 내 "무료 D-xx" 배너 가능 (서버 멤버 관리에도 동일 표시 있음).
- waitlisted 화면: 재확인 버튼 = verify 재호출 말고 `/api/beta/check` 폴링 (등업되면 통과됨).
- 베타 사이트(si0in.kr) 신청자도 같은 흐름으로 합류 — 서버가 알아서 분기.

## 서버 정책 (참고)

- 자동 등업 cap = env `AUTO_ENROLL_CAP` (기본 100). 초과분은 beta_signups 대기열 (source='app/auto-signup') → 사장님이 멤버 관리에서 등업.
- 무료 기간 = env `FREE_TRIAL_DAYS` (기본 60일). 등업/자동가입 시각 + 60일 = free_until_ms.
  ⚠️ 만료 시 동작(잠금 범위)은 아직 미정 — 사장님 결정 대기. 지금은 추적/표시만.
- 어뷰징 방파제: 번호당 5회/일, 60초 간격, 전체 500회/일, 검증 5회 실패 시 코드 폐기.
- AI 사용 한도 (기존): 전체 2500회/일, 폰당 200회/일 (env 조절 가능).

## 사장님 액션 (서버 활성화 조건)

plist EnvironmentVariables 에 SOLAPI 3개 추가 후 재시작:
```
SOLAPI_API_KEY / SOLAPI_API_SECRET / SOLAPI_SENDER (사전 등록된 발신번호)
```
없으면 request-code 가 503 (앱은 "준비 중" 안내).

— cowork (2026-07-03)
