# 보안 핸드오프 — 서버 작업 (cowork 담당) · 2026-07-30

> 배경: 사장님이 공폰 + 본인 번호로 로그인 → 협업·잔금이 보임(무인증 IDOR)을 직접 재현.
> 페이블5 5각도 전수 보안감사 실시. **앱↔서버 54경로 중 인증 있는 것은 1개(expo secret)뿐.**
> 앱 쪽 독립 항목은 android 가 이미 처리(아래 §C). **아래 §A·§B 는 서버 영역이라 cowork 담당.**
> 전체 보고서(비공개 아티팩트): https://claude.ai/code/artifact/094f6ba4-db8f-4b28-bbd3-d27c060fdce2
> 근거: `server/main.py` 정적 분석(줄번호 = 감사 시점 main 기준, 배포본과 대조 필요).

---

## §A. 즉시 핫픽스 (인증 한 줄씩 — 오늘 가능)

무인증으로 열려 있는 관리자 데이터. 다른 `/admin/*` 는 이미 `_admin_auth`/`_admin_auth_bearer_from_header` 를 씀 — **같은 패턴만 추가**하면 됨.

| 엔드포인트 | main.py | 새는 것 |
|---|---|---|
| `GET /admin/usage` | 4909 | 실사용 고객 전화 TOP15 + 비용 |
| `GET /admin/diagnostics` | 25422 | (HTML) |
| `GET /admin/diagnostics/data` | 25435 | 신고자 전화·진단본문 |
| `POST /admin/diagnostics/resolve` | 25453 | 무인증 상태 변조 |
| `GET /admin/diagnostics/image/{rid}` | 25465 | 첨부 스크린샷 (경로탈출은 이미 방어됨) |

> ⚠️ 진단함(`/admin/diagnostics*`)은 android 가 2026-07-29 에 만든 것 — **인증을 빠뜨린 건 android 실수**. 사과와 함께 넘김. HTML 페이지는 클라이언트 토큰 모달 유지하되 **data/resolve/image 는 서버측 인증 강제** 필요.

---

## §B. 근본 해결 — 세션 토큰 (핵심, 이거 하나로 대부분 닫힘)

### B-1. verify-code 가 토큰을 발급
- 위치: `POST /api/auth/verify-code` `main.py:20932`
- 현재: 인증번호 맞으면 `status/freeUntilMs/freeDays` 만 반환 — **세션 토큰 없음.**
- 할 일: 검증 성공 시 **phone-bound 세션 토큰**(JWT 또는 서명된 opaque) 발급해 응답에 포함.
  - 페이로드에 `phone`(정규화된 숫자만) + `exp`. 서버 비밀키로 서명.
- **앱이 필요로 하는 계약(cowork ↔ android 합의 필요):**
  1. 응답 JSON 의 토큰 필드명 (예: `sessionToken`)
  2. 토큰 형식/만료 (JWT? 만료 며칠? 갱신 방법 — 재인증 or refresh)
  3. 요청에 실을 헤더 이름 (권장: `Authorization: Bearer <token>`)
  → 정해지면 android 가 저장(EncryptedSharedPreferences)+전 요청 부착 구현.

### B-2. 데이터 엔드포인트가 토큰을 검증 (요청 phone == 토큰 phone + 소유권)
공통 FastAPI `Depends(auth)` 하나 만들어 아래에 전부 적용. `요청의 phone/owner_phone/devicePhone == 토큰의 phone` 강제. customer_phone 계열은 "그 고객이 이 owner 의 고객인지" 소유권까지.

**GET (읽기 IDOR — 확정):**
| 엔드포인트 | main.py | 새는 것 |
|---|---|---|
| `/api/shared/with-me` | 13875 | 계좌·주소·일당·메모 |
| `/api/shared/by-me` | 13973 | 파트너 전화·일정 |
| `/api/shared/owner-events` | 14359 | **계좌번호** |
| `/api/shared/partners` | 14433 | 협업 집계 |
| `/api/shared/history` | 14480 | 협업 내역 |
| `/api/shared/comments` | 14926 | 댓글 (site_id 랜덤이라 난이도↑) |
| `/api/team/members` | 18602 | 팀원 실명·전화 |
| `/api/team/events` | 18710 | 팀 이벤트 |
| `/api/team/photos` | 19040 | 현장 사진 |
| `/api/team/notes` | 19129 | 현장 메모(+read 처리) |
| `/api/quote/submissions` | 17934 | 고객 전화·이름·금액·설문 |
| `/api/intake-form/status` | 16556 | 고객 접수서 |
| `/api/intake-form/list` | 16577 | 접수서 전량 |
| `/api/site-photos` (customer_phone 분기) | 19353 | 현장 사진 |
| `/suggestions/{phone}` | 2694 | 고객 문자 원문 + AI 제안 |
| `/api/customer-persona/{phone}` | 4558 | 고객 AI 프로파일 |
| `/api/labor/history` | 15685 | 일당 이력 |
| `/api/mirror/shares` | 22086 | 미러 신청 목록 |

**POST (쓰기 위조/가로채기):** `/api/push/register`(피해자 번호에 공격자 토큰), `/api/mirror/snapshot`(전 고객 PII+미수금+계좌 위조 주입), `/api/shared/invite·progress·paid…`, `/api/team/*`, `/api/quote/issue`·`/api/intake-form/issue`(남 명의 발급 피싱), `/api/owner-tone/batch-upload`, `/api/*-summary`(비용 소진) 등.

**재사용할 좋은 패턴(이미 서버에 있음):** `_expo_room_member(room_id, phone)` 멤버십 벽 · capability 토큰(`/api/mirror/data/{token}` 토큰+PIN, `/api/labor/account?token=`) · 랜덤 ID 벽(`/api/shared/paid` share_id, member DELETE).

### B-3. 데이터 GET 레이트리밋
- `check_rate_limit` `main.py:1369` 는 지금 **비용성 LLM 엔드포인트에만** 적용. §B-2 데이터 GET 에도 IP+phone 기준 적용 → 번호 대입(enumeration) 대량수집 차단.

### B-4. (낮음) `/docs`·`/openapi.json` 비활성
- `FastAPI(...)` `main.py:2650` 에 `docs_url=None, redoc_url=None, openapi_url=None` (또는 리버스프록시 차단).

---

## §C. 앱 쪽 — android 가 이미 처리한 것 (참고, 서버 작업 아님)
2026-07-30 커밋 예정:
- PII 로그 마스킹 — 문자 본문 로그 제거 + 전화번호 `***1234` (`LogRedact` 유틸). 대상: MmsSentReceiver/MmsDownloadedReceiver/CallStateReceiver/CallAudioSummarizer/SmsSender.
- 디버그 MMS 리시버 `exported=false` (다른 앱의 임의 MMS 발송 트리거 차단).
- 문서 웹뷰(DocWebViewActivity) `si0in.kr` https 만 로드 허용 + 로컬 파일 접근 차단.
- **대기(서버 B-1 뒤):** 로그인 OTP 게이트 + 요청에 토큰 부착. → **B-1 토큰 계약 정해지면 착수.**
- 미착수(별도 계획): 계좌·미러토큰 EncryptedSharedPreferences, Room DB 암호화(마이그레이션 리스크).

---

## 다음 액션 (cowork → android)
1. **§A 핫픽스 먼저** (오늘, 인증 한 줄).
2. **§B-1 토큰 계약**(필드명·헤더·만료) 정해서 SYNC.md 에 회신 → android 가 앱 연결.
3. §B-2 전 엔드포인트 `Depends` 인증 → 배포.
