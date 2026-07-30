# SERVER HANDOFF — 협업 현장 (사장 ↔ 사장 공유) Phase 1

작성: 2026-06-08 · 안드로이드 Claude → 맥미니 Claude
기획: `docs/SPEC_shared_sites_owner_to_owner.md` (§9 Phase1, §10 완료→입금)
앱 클라이언트: `app/.../ai/SharedSiteRepository.kt` (이미 구현 — 이 명세대로 호출함)

## 한 줄
한 현장을 두 RING-GO **앱 사장**이 같이 보는 기능. 팀 API(`/api/team/*`)와 같은 스타일/저장소 **재사용** + 아래 신규 endpoint.
**벽:** 공유는 그 현장 1건만. 고객 전화번호·상대 다른 고객·매출은 절대 응답에 포함 금지.

## 식별
- 사장 = `bizPhone`(숫자만). A=현장 주인(고객 보유), B=협업 사장(초대받음).
- 팀과 차이: B는 **앱 사장**(웹뷰 아님). 같은 번호가 가입 사장이면 인앱, 아니면 기존 팀 웹링크로 분기.

---

## 신규 endpoint (앱이 호출하는 그대로)

### 1) `GET /api/owner/exists?phone={B}`
상대가 가입 사장인지 → 인앱/링크 분기.
```json
{ "registered": true, "name": "박지훈" }
```

### 2) `POST /api/shared/invite`
A가 상대 사장에게 현장 공유 요청. (고객 전화번호는 **받지도 저장도 안 함**)
```json
// req
{ "owner_phone":"010A", "partner_phone":"010B", "title":"강동 천호동 현장",
  "addr":"강동구 천호동 …", "scheduled_at_ms":1781000000000,
  "work_summary":"욕실 줄눈 2곳", "memo":"현관 비번 1234#", "customer_label":"강동 서사장님 현장" }
// resp
{ "share_id":"sh_abc", "route":"inapp", "url":null, "sms_draft":null }
// 미가입(B 앱 없음): { "share_id":"sh_abc","route":"link","url":"https://…/shared/{token}","sms_draft":"OO 사장님이 …" }
```
- inapp 이면 B 앱에 푸시(아래 §푸시) + `with-me` 에 status="pending" 으로 등장.

### 3) `GET /api/shared/with-me?phone={B}&since_ms=0&limit=50`
B(협업자)가 공유받은 현장 목록. **이게 B측 화면의 핵심.**
```json
{ "sites": [
  { "share_id":"sh_abc", "owner_phone":"010A", "owner_name":"강동 서사장님",
    "title":"강동 천호동 현장", "addr":"강동구 천호동 …", "scheduled_at_ms":1781000000000,
    "time_label":"09:00", "work_summary":"욕실 줄눈 2곳", "memo":"현관 비번 1234#",
    "status":"accepted", "progress":"arrived", "created_at_ms":1780900000000 }
] }
```
- `status`: `pending`|`accepted`|`declined`. `progress`: `assigned`|`departed`|`arrived`|`completed`.
- **고객 전화번호/대화/상대 다른 고객 절대 포함 금지.**

### 4) `POST /api/shared/respond`
B 수락/거절. `{ "share_id":"sh_abc", "partner_phone":"010B", "accept":true }` → 200. (A에게 알림)

### 5) `POST /api/shared/progress`
B가 출발/도착/완료. 팀 events(departed/arrived/completed) 재사용 가능.
```json
{ "share_id":"sh_abc", "partner_phone":"010B", "step":"completed",
  "payload": { "bank":"국민은행", "account_no":"123456-01-789012", "holder":"박지훈" } }
```
→ 200. **step=completed 면 payload 의 계좌를 A에게 푸시**(§10). (departed/arrived 는 payload 없음)

### 6) `POST /api/shared/paid`
A가 입금완료 표시 → B에게 "입금됐어요" 알림. `{ "share_id":"sh_abc", "owner_phone":"010A" }` → 200.

---

## 재사용 (신규 아님, 그대로)
- **현장 사진/메모:** 기존 `/api/site-photos`, `/api/team/notes`, `/api/team/note/reply` 를 share_id(또는 owner+partner) 기준으로 권한만 확장. (B는 자기가 협업 중인 현장만 접근, 그 외 403)
- **진행 이벤트 저장:** team_member_events 테이블 재사용 권장.

## 푸시 (TeamEventCenter / NotificationHelper.showTeamEvent 재사용)
- A→B 초대: "OO 사장님이 [현장] 협업 요청" (수락/거절)
- B→A 완료: "[현장] 완료됐어요! OO 사장님께 입금 — 국민은행 123-456-789 (박지훈)" ← **계좌 포함**
- A→B 입금: "입금됐어요 — [현장]"
- 진행(출발/도착)은 기존 팀 알림과 동일 형식.

## 벽 보장 (필수 검증)
- B 인증 = 자기 bizPhone + 그 share_id 권한만. 다른 share/현장/고객 요청은 **403**.
- 어떤 응답에도 고객 phone, 고객 대화, 상대의 다른 고객/매출 넣지 말 것.

## 회신 부탁 (SYNC.md)
1. endpoint 경로·필드 이대로 OK? (다르면 확정안 → 앱 SharedSiteRepository 맞춤)
2. invite 의 inapp/link 분기 방식(가입 사장 디렉터리 = bizPhone 등록부) 어떻게 할지.
3. progress=completed 의 계좌 payload → A 푸시까지 한 번에 처리 가능한지.

## 앱 상태 (이미 된 것)
- `SharedSiteRepository` (위 호출 전부 구현), B측 **협업 현장 화면**(목록/상세/진행 stepper/완료 알리기/벽 안내, 더보기 진입) — 서버 붙으면 바로 동작. 지금은 with-me 빈 목록이라 "공유받은 현장 없음" 표시.
- **입금 계좌 등록** = 더보기 → 견적서·사업자 정보 (은행/계좌/예금주, AppPreferences). 완료 시 이 값을 progress payload 로 보냄.
- **미구현(서버 후 앱 작업):** A측 "협업 현장으로 공유" 버튼+시트(고객카드), 캘린더 보라점, A측 완료/계좌 수신 카드+입금완료 버튼. (DECISIONS 참고)
