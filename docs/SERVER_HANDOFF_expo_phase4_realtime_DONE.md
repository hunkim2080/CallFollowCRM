# 서버 → 앱 회신 · 박람회 Phase 4 실시간 계약서 (서버 완료)

작성: cowork · 2026-07-22 · **서버 구현·검증 완료(추가143) · 배포 대기**
요청 원본: `docs/SERVER_HANDOFF_expo_phase4_realtime.md` (android) · 확정: EXPO_DECISIONS 확정9.

> 요청한 4가지 전부 서버에 반영. 앱은 아래 API 로 상담사 네이티브 계약서 화면을 배선하면 됩니다.
> 고객 웹(viewer)·주소(다음)·PDF 는 서버가 끝냄 — 앱 작업 없음.

## 1. 실시간 세션 API (폴링 방식)
- `POST /api/expo/contract/live/agent`
  `{session_id, secret, items:[{product_id, qty}], discount, deposit_enabled, deposit_amount}`
  → `{ok, product_total, discount, final_amount}`
  - 상담사 앱이 체크할 때마다(디바운스 권장 300~500ms) 호출. **secret 필요**(session 응답의 secret).
  - 완료된 세션이면 409.
- `POST /api/expo/contract/live/customer`  ← **고객 웹이 호출(앱 무관, 참고용)**
  `{session_id, k, customer_name?, customer_phone?, apartment?, dong_ho?, address?, signature?}` (부분 갱신)
- `GET /api/expo/contract/live/{session_id}?k=` → **합쳐진 현재 상태 (앱·웹 둘 다 1.5초 폴링)**
  ```json
  {"status":"live|finalized","contract_id":null,"catalog":[...],"items":[{product_id,kind,name,unit_price,qty,line}],
   "product_total":350000,"discount":20000,"deposit_enabled":true,"deposit_amount":50000,"final_amount":330000,
   "customer_name":"홍길동","customer_phone":"01055556666","apartment":"동탄에스알골드","dong_ho":"714동 128호",
   "address":"화성시 동탄지성로 11","signature_present":true,"agent_updated_ms":..,"customer_updated_ms":..}
  ```
  - **final_amount 는 서버가 카탈로그 단가로 재계산**(단가 신뢰 유지). signature 원본은 안 내려감 → `signature_present` 로 도착 여부만.
  - k 는 session 의 secret. (앱은 secret 을 k 로 그냥 넘겨도 됨 — 같은 값)
- `POST /api/expo/contract/finalize` `{session_id, secret}` → `{contract_id, final_amount, receiptUrl}`
  - 라이브 상태를 계약서로 굳혀 저장. **기존 submit 대체**(상담사 [완료] 버튼).
  - 이미 완료면 `{already:true, contract_id, receiptUrl}` 반환(중복 안전). 상품 0개면 400.

## 2. 고객 웹페이지 (서버 완료 — 앱 작업 없음)
- `GET /expo/c/{session_id}?k=` → 실시간 viewer.
  - 상품 = **읽기전용**, 1.5초 폴링으로 상담사 선택 반영(초록 라이브 점).
  - 고객 입력: 이름·전화 + **[주소 찾기]=다음(카카오) 우편번호 위젯** → 아파트명·동호수 구조화 + 서명(동의 후 활성).
  - finalize 감지 시 자동으로 영수증(`/expo/r/{cid}`)으로 이동.

## 3. submissions 필드 추가 (앱 네이티브 접수서용)
`GET /api/expo/submissions?room_id=&phone=` 각 item 에 추가됨:
- `apartment`(아파트명), `dong_ho`(동호수), `address`(도로명) — 상세주소 구조화.
- `agent_name`(계약자), `customer_phone_masked`(뒷4자리 서버 마스킹), `products`, `final_amount`, `status` — 기존 유지.
- → 앱이 웹뷰 없이 네이티브로 목록(이름+시공내역+계약자) / 클릭 시 아파트명+동호수 렌더.

## 4. PDF / 공유
- `GET /expo/r/{contract_id}` 영수증에 **[PDF로 저장 / 인쇄] 버튼**(브라우저 인쇄 = OS PDF 저장) + `@media print` A4 최적화 + 계약번호(No.).
- 앱은 안드로이드 공유시트로 `receiptUrl` 공유(카톡 등). 서버측 PDF 파일 생성이 꼭 필요하면 후속으로 추가 가능(현재는 인쇄→PDF).

## 앱이 할 것
1. **상담사 네이티브 계약서 화면**: catalog 체크·수량·[총액할인]·[계약금 on/off] → live/agent POST(디바운스). live GET 1.5초 폴링으로 고객 정보·서명 도착 표시. [완료]=finalize → receiptUrl 공유.
2. **우리 팀 접수서 네이티브**: submissions 로 목록/상세(apartment·dong_ho). receiptUrl 은 공유/PDF 볼 때만.
3. 기존 "계약서 열기 QR" 화면 → 위 상담사 네이티브 화면으로 교체. 방·상품·접수서는 유지.

## 검증 / 하위호환
- TestClient 29항목 ALL OK (live/agent·customer·GET·finalize·재계산 330,000·아파트동호수·마스킹·PDF버튼·완료감지).
- 기존 `POST /api/expo/contract/submit`(Phase1 단방향)도 여전히 동작(하위호환) — 앱 교체 전까지 안 깨짐.
- Python 3.9 `Optional[...]` 유지. 미배포 → `bash server/deploy_phase1.sh`.
