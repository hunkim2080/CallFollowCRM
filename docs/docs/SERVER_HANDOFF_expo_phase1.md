# 서버 → 앱 핸드오프 · 박람회 Phase 1 (종이 없애기)

작성: cowork · 2026-07-22 · **서버 구현·검증 완료(추가142) · 배포 대기**
확정 스펙: `docs/EXPO_DECISIONS.md` (사장님 확정 8건). 설계: `docs/PLAN_expo_team.md`.

> Phase 1 = **종이 계약서 없애기**. 상담원 폰에 QR → 고객폰 웹 계약서 → 제출 →
> 상담원 앱이 결과 수신 + [계약서 보기]. **실시간 양방향 체크는 Phase 4로 미룸(단방향).**
> 격리 원칙: 박람회 데이터는 `expo_*` 에만. 기존 정산/고객/submissions 와 **안 섞음**.

## 흐름 한눈에
1. 방장이 방 개설 → **초대 코드(6자리)** 받음 → 팀원에게 알려줌.
2. 방장이 **상품·서비스 목록 등록**(단가 포함). ← 이게 있어야 계약서를 열 수 있음.
3. 팀원(상담원)이 코드로 방 합류.
4. 상담원이 [계약서 열기] → **QR** 화면 → 고객이 자기 폰으로 찍음.
5. 고객 폰 웹: 상품 체크·수량 → 총액 할인 → (현장) 계약금 on/off → 고객정보 → **개인정보 동의 + 서명** → 제출.
6. 제출되면 상담원 앱이 폴링으로 결과 수신, 고객·상담원 모두 **계약서 사본** 열람.
7. 팀 누구나 **"우리 팀 접수서 목록"**(뒷4자리 마스킹) 실시간 확인.

---

## API (전부 구현·TestClient 검증 완료)

### 방 (Room)
- `POST /api/expo/room/create` `{owner_phone, name, owner_name?}` → `{room_id, code, name}`
  - 방장 자신이 owner 멤버로 자동 등록.
- `POST /api/expo/room/join` `{code, phone, name}` → `{room_id, name, role}` (role=owner|member)
  - 없는 코드 404 / 종료된 방 410.
- `GET /api/expo/rooms?phone=` → `{rooms:[{room_id, role, name, code?, memberCount, productCount, contractCount, closed}]}`
  - **code 는 방장(role=owner)에게만 내려감.** 팀원은 null.
- `GET /api/expo/room/{room_id}?phone=` → `{name, myRole, members:[{name,role,phone}], catalog:[...]}`
  - 멤버 아니면 403. **phone 은 방장에게만 원본, 팀원에겐 마스킹(010-1234-****).**

### 상품 카탈로그 (방장만)
- `POST /api/expo/products/set` `{room_id, owner_phone, products:[{kind:'product'|'service', name, unit_price}]}`
  → `{count, catalog}`. **통째로 교체(덮어쓰기)**. 서비스는 unit_price 0 가능.
  - 방장 아니면 403.
- `GET /api/expo/products?room_id=` → `{catalog:[{product_id, kind, name, unit_price, sort}]}` (공개)

### 계약서 세션 + 제출
- `POST /api/expo/contract/session` `{room_id, agent_phone}` → `{session_id, secret, url, qrUrl}`
  - **앱은 `qrUrl` 을 QR 로 그림.** 고객이 찍으면 계약서 페이지 열림.
  - 상담원이 방 멤버 아니면 403 / **카탈로그 없으면 409**(방장이 상품 먼저 등록해야 함).
  - 세션 유효 2시간.
- `GET /expo/c/{session_id}?k={secret}` → **고객폰 계약서 웹페이지(HTML)**. 서버가 카탈로그를 박아 렌더. 앱 작업 없음.
- `POST /api/expo/contract/submit`
  ```json
  {"session_id":"ecs_..","secret":"..","customer_name":"홍길동","customer_phone":"01055556666",
   "address":"..","items":[{"product_id":12,"qty":2}],"discount":20000,
   "deposit_enabled":true,"deposit_amount":50000,
   "signature":"data:image/png;base64,..","consent":{"privacy":true}}
  ```
  → `{contract_id, final_amount, receiptUrl}`
  - **금액은 서버가 카탈로그 단가로 재계산**(클라이언트 금액 불신). `final = Σ(단가×수량) − discount`.
  - 이미 제출된 세션 재제출 409 / 만료 410. (이 페이지는 고객이 씀 — 앱은 결과만 받음)
- `GET /expo/r/{contract_id}` → **계약서 사본(HTML, 읽기전용)** — 내역·금액·서명·동의·시각. 고객·상담원 공용.

### 팀 접수서 목록
- `GET /api/expo/submissions?room_id=&phone=` (방 멤버만)
  → `{count, totalAmount, items:[{contract_id, customer_name, customer_phone_masked, products, final_amount, status, agent_name, assigned_name?, created_at_ms}]}`
  - **전화번호는 서버가 뒷4자리를 지워서 내려줌**(원본 phone 은 앱에 안 감 = 유출 경로 차단). 앱은 그대로 표시만.

---

## 앱 UI 제안 (박람회 모드 = ExpoScreen, 이미 골격 있음)
- **방장**: [방 만들기] → 코드 크게 표시 + 공유. [상품/서비스 준비] 화면(목록·단가 입력 → products/set). [팀원] 목록.
- **상담원(팀원 포함)**: [방 합류](코드 입력). [계약서 열기] → `session` → **qrUrl 로 QR 크게** → 고객이 찍음 → 앱은 submissions 폴링(또는 세션 contract_id 확인)으로 "제출됨" 감지 → [계약서 보기](receiptUrl).
- **공용**: [우리 팀 접수서] = submissions 목록(뒷4자리 마스킹, 상품·금액·상담원). "중간중간 확인"용.

## Phase 2/3 대비 (지금 만들지 마세요, 스키마만 준비됨)
- `expo_contracts.status`: `submitted → assigned(분배) → scheduled(일정) → done(완료)`.
- `assigned_phone / scheduled_at_ms / done_at_ms` 컬럼 이미 있음.
- 진행률 2눈금(확정8): 일정등록률 = (scheduled+done)/assigned, 시공완료율 = done/assigned → Phase 3에서 계산.

## 주의
- Python 3.9 서버 — 서버 타입 `Optional[...]` 유지(앱 무관).
- phone 은 서버가 `_norm_phone` 으로 정규화(하이픈 무관).
- 서명 이미지는 dataURL(png)로 계약서에 저장. 200KB 상한.
