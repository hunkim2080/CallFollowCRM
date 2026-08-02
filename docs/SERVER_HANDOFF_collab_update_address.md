# 서버 핸드오프 — 협업 현장 "주소 변경 전파" · 2026-08-02

> 요청: android → cowork. 사장님 버그신고(2026-08-02): **"협업 신청한 곳의 주소지를 바꾸면 연결된 협업 사장들도 다 바뀌어야 하는데 예전 주소 그대로 존재."**
> 앱은 **이미 호출부까지 배선 완료** — 서버 엔드포인트만 생기면 자동 동작. 없으면(404) 조용히 무시(로컬은 이미 바뀜).

## 배경
- A(현장 주인)가 협업 요청 시 `POST /api/shared/invite` 로 `addr` 를 **한 번** 보냄 → `shared_sites.addr` 에 저장.
- 그 뒤 A 가 고객 상세에서 **주소를 바꿔도** 앱은 로컬(customer.address)만 고침. 서버 `shared_sites.addr` 는 옛날 그대로 → B(협업 사장)는 옛 주소로 길찾기.
- **날짜 변경**은 이미 전파 기능이 있음(`POST /api/shared/reschedule` → `scheduled_at_ms` 갱신 + FCM `type=collab_reschedule`). **주소도 그 형제 엔드포인트가 필요.**

## 요청 엔드포인트 (reschedule 와 같은 패턴)
`POST /api/shared/update-address`

### 요청(JSON)
```json
{
  "share_id": "abc123",
  "owner_phone": "01012345678",
  "addr": "인천 미추홀구 매소홀로 137",
  "customer_label": "미추홀 현장"      // 선택 — 있으면 표시 라벨도 갱신
}
```
- `owner_phone` = 현장 주인(A) 번호 숫자11. (§인증 켜지면 토큰 phone 과 일치 강제 — 기존 규칙대로)
- `addr` = 새 주소(앱이 `AddressExtractor.tidyAddress` 로 정리해서 보냄).
- `customer_label` = 새 표시 라벨("○○ 현장"). 없으면 기존 label 유지.

### 서버가 할 일
1. `share_id` 의 `shared_sites.addr`(+ 있으면 `customer_label`) 갱신. **owner_phone 이 그 share 의 주인일 때만** 허용(아니면 403).
2. 그 share 의 **참여자 B(들)** 에게 FCM push: `type=collab_address_change` — payload 에 `share_id`, 새 `addr`, `title/label`. (reschedule 의 B 대상 로직 그대로 재사용)
3. B 앱이 받은 알림/현장 상세에서 새 주소로 갱신되면 끝.

### 응답
```json
{ "ok": true }
```
- 실패 시 비200 → 앱은 `Result.failure` 로 받고 **조용히 무시**(로컬 주소는 이미 바뀜, 사장님에게 에러 안 띄움). best-effort.

## 앱 쪽 (이미 완료 · 참고)
- `SharedSiteRepository.updateAddress(shareId, ownerPhone, addr, customerLabel)` — `post("$baseUrl/api/shared/update-address", …)`.
- 호출: `CustomerDetailViewModel.updateManualAddress` → 로컬 저장 후 `propagateAddressToCollab` 가 그 고객의 **살아있는 협업 shareId 들**(`preferences.collabAssignments` 5칸 파싱)에 대해 각각 호출.
- 여러 협업자가 같은 현장이면 그 현장의 shareId 들 전부에 전송.

## B 앱(수신) — 후속(선택)
- `type=collab_address_change` FCM 을 B 앱이 처리해 현장 상세 주소를 즉시 갱신하면 완벽. (지금은 B 가 다음 동기화 때 `withMe`/`shared` 재조회로 새 addr 를 받게 되면 충분 — 서버 저장만 되면 최소 동작.)

## 검증 포인트
- A 가 협업 보낸 뒤 주소 변경 → 서버 `shared_sites.addr` 바뀌나 / B 조회 시 새 주소 오나 / (있으면) B 에게 알림 오나.
- owner_phone 이 주인이 아니면 403.
