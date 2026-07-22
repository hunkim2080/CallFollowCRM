# 서버 → 앱 핸드오프 요청 · 박람회 Phase 4 (실시간 계약서)

작성: android · 2026-07-22 오후 · **사장님 재설계 확정** (Phase 1 단방향 → 실시간 양방향)
관련: `docs/SERVER_HANDOFF_expo_phase1.md`(현행) · `docs/EXPO_DECISIONS.md`(확정 9 추가)

> ⚠️ 이건 Phase 1 핸드오프에서 "실시간 양방향 = Phase 4로 미룸(단방향)" 이라 적었던 그 부분을 **지금** 한다는 요청. 계약서 흐름의 핵심이 바뀜.

---

## 왜 (사장님 실사용 방식)
상담사와 고객이 **마주 앉아** 상담한다. 각자 폰만 있고 한 화면을 같이 못 본다.
→ **상담사가 자기 화면(시공막내 앱)에서 상품을 체크하면, 고객 폰(웹)에 실시간으로 똑같이 보인다.** 고객은 **viewer**.
→ 고객이 직접 하는 건 **① 고객정보 입력 ② 서명** 뿐. 상품 선택은 상담사가.

## 역할 (확정 — 사장님 2026-07-22)
| 주체 | 어디서 | 무엇 |
|---|---|---|
| **상담사** | **시공막내 앱(네이티브)** | 상품 체크·수량·총액할인·계약금 on/off (조종) |
| **고객** | **QR 웹페이지** | 상담사 선택을 **실시간 view** + 본인 정보·서명 입력 |
| **동기화** | **서버(중계)** | 앱↔웹 실시간 상태 공유 |

---

## 서버에 요청 (cowork)

### 1. 실시간 세션 상태 (핵심) — 전송방식은 코워크 재량, 폴링 권장
계약 세션에 "라이브 상태"를 두고 앱·웹이 공유. 앱은 이미 폴링 인프라(submissions)를 쓰므로 **폴링이 제일 단순**(WebSocket 은 후속 최적화):

- `POST /api/expo/contract/live/agent` `{session_id, secret, items:[{product_id, qty}], discount, deposit_enabled, deposit_amount}`
  → 상담사 앱이 체크할 때마다(디바운스) 호출. 서버가 라이브 상태 갱신.
- `POST /api/expo/contract/live/customer` `{session_id, k, customer_name?, customer_phone?, apartment?, dong_ho?, signature?}`
  → 고객 웹이 입력할 때 호출.
- `GET /api/expo/contract/live/{session_id}?k=` → **합쳐진 현재 상태**
  `{items, catalog, discount, deposit_enabled, deposit_amount, customer_name, customer_phone, apartment, dong_ho, signature_present, final_amount, status}`
  → **앱·웹 둘 다 1~2초 폴링**해서 상대가 바꾼 걸 반영. (final_amount 는 서버 재계산 = 단가 신뢰 유지)
- `POST /api/expo/contract/finalize` `{session_id, secret}` → 라이브 상태를 계약서로 굳혀 저장 → `{contract_id, receiptUrl}`. (기존 submit 대체)

### 2. 고객 웹페이지 재설계 (`/expo/c/{sid}` → viewer)
- 상품 체크 = **읽기 전용**. 상담사가 체크한 게 실시간 표시(위 GET 폴링).
- 고객이 채우는 칸만: 이름·전화·**주소** + **서명 패드**.
- **주소 = 카카오(다음) 우편번호/지도 방식** — 우리 앱 주소 수집과 동일. 결과를 **아파트명·동호수 구조화** 저장.

### 3. 접수서 API 필드 추가 (앱 네이티브 렌더용)
`GET /api/expo/submissions` 각 item 에 추가:
- `apartment`(아파트명), `dong_ho`(동호수) — 상세주소 구조화.
- `agent_name`(계약자) — 이미 있음. 유지.
- 목적: **앱이 웹뷰 안 열고 네이티브로** 목록/상세를 그림. (receiptUrl 은 PDF·공유용으로만 남김)

### 4. 완료 후 PDF / 카톡공유
- 계약 완료 사본을 **PDF**로 (계약 내용이라 보관용). 고객 웹 + 상담사 앱 양쪽에서 저장·공유.
- 카톡공유 = 앱은 안드로이드 공유시트로 receiptUrl(또는 PDF) 공유 → 앱이 처리. 웹은 웹에서.

---

## 앱(android)이 할 것 (위 API 나오면 착수)
1. **상담사 네이티브 계약서 화면** — catalog 체크리스트·수량·[총액 할인]·[계약금 on/off], 서버에 라이브 POST, GET 폴링으로 고객 정보·서명 도착 표시, [완료]=finalize.
2. **우리 팀 접수서 네이티브** — 목록(이름 + 계약자) / 클릭 시 펼쳐 **아파트명 + 동호수**. receiptUrl 웹뷰 대신 네이티브. (공유·PDF 볼 때만 receiptUrl)
3. **완료 계약서 공유** — 안드로이드 공유시트(카톡 등).

## 사장님께 추후 확인 (지금 안 막음)
- 주소 입력 주체 = **고객 웹**(확정: 고객정보=고객이 입력) — 맞으면 앱은 주소 입력 안 만듦.
- 계약금 기본 노출 on/off, PDF 양식.

## 지금 상태 (Phase 1 앱)
- 방 개설/코드/합류·상품카탈로그·우리팀접수서(웹)·팀원 = 앱 완료(0.2.1084, S9 실기검증). 방/상품/접수서 데이터는 그대로 재사용.
- ⚠️ 앱의 "계약서 열기 QR" 화면(고객 제출 폴링)은 **이 재설계로 교체 예정**. 방·상품·접수서는 유지.

---

## 추가 요청 (2026-07-22 오후 2차, 사장님)
1. **특이사항/비고 저장** — 앱이 `live/agent` 에 `note`(string) 를 이미 보냄(현재 서버가 extra 로 무시, 200 OK 확인). 요청:
   - `live/agent` 가 `note` 를 세션 라이브 상태에 저장.
   - `GET live/{sid}` 응답에 `note` 포함(앱·웹 표시).
   - `finalize` 시 `note` 를 계약서에 굳혀 저장 → 고객 웹 viewer + 영수증(`/expo/r`) + `submissions` item 에 `note` 포함.
2. **접수 시각** — 영수증엔 이미 날짜+시각 있음(확인). `submissions` 의 `created_at_ms` 도 있음(앱이 "몇시 몇분" 표시함). 고객 웹 viewer 에도 접수/작성 시각이 보이면 좋음(사장님 "고객한테도 표기").

## 앱 상태 (이 요청분)
- 앱은 비고 입력칸(상담사 화면) + `note` 전송 + 접수서에 접수시각(HH:MM)·현장(아파트명 동호수) 표시 = **완료(미배포)**. note 는 서버가 저장 붙이면 자동으로 살아남(앱 재작업 없음).

---

## 추가 요청 (2026-07-22 오후 3차, 사장님) — 완료 흐름 + 필수항목

### A. 계약 완료(체결) 흐름 = 서버 상태머신 (핵심)
지금은 "상담사가 완료 누름"인데, 바뀐 방식:
1. **고객**이 서명 후 웹에서 **[완료]** 누름 → 서버 `customer_confirmed=true`.
2. 상담사 앱은 `GET live` 의 `customer_confirmed` 로 **"고객이 작성 완료 · 수정사항 없으신가요?"** 배너 표시.(앱 완료)
3. 상담사가 **수정(live/agent 호출)** 하면 → 서버가 `customer_confirmed=false` **로 되돌림**(고객 재확인 필요).
4. 상담사가 **[계약서 보관하기]** = `finalize` → **`customer_confirmed=true` 일 때만 허용**(아니면 409). 성공 시 status=finalized → 앱·웹 둘 다 **"계약이 정상적으로 체결되었어요!"**.

→ **서버 할 일**: `GET live` 에 `customer_confirmed` 추가 · 고객 웹에 [완료] 버튼(→ customer_confirmed=true) · **live/agent 호출 시 customer_confirmed 리셋** · finalize 를 customer_confirmed 게이트. (앱은 `customer_confirmed` 읽어 배너/라벨 이미 반영, 보관 버튼 하드게이트는 서버 확정되면 앱이 `enabled=customer_confirmed` 로 한 줄 추가.)

### B. 계약서 필수 항목 (사장님 "필수")
계약서에 **고객 성함 · 연락처 · 시공주소 · 고객 서명** 이 반드시 담겨야 함.
- 고객 웹: 이 4개 다 채워야 **[완료]** 가능(미입력 시 완료 막기). 앱 상담사 화면은 이 4개 도착 상태를 체크표시로 보여줌(앱 완료).
- 이 값들이 finalize 후 영수증(`/expo/r`) + `submissions` 에 포함돼야 함.

### C. 특이사항/비고 노출
- (2차 요청 재확인) `note` 를 고객 웹 viewer · 영수증 · submissions 에 **표시**. 앱 접수서·상담사 화면은 이미 note 반영.

## 앱 상태 (3차분)
- 상담사 화면: 고객 필수항목(성함·연락처·주소·서명) 체크표시 + "고객 작성완료" 배너(customer_confirmed) + 버튼 "계약서 보관하기" + 성공 "계약이 정상적으로 체결되었어요!" = **완료(미배포)**. 서버가 customer_confirmed·note 붙이면 자동 작동.
