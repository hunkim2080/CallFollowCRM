# ANDROID 핸드오프 — 유료전환 D-14 사전 동의 화면

작성: cowork · 2026-07-10 · 서버(추가117 B2) 완료, 앱 UI 배선 필요.

## 왜
전상법 §13조의2 + 약관 §8: 무료 체험이 유료로 자동 전환되기 전 **최소 14일 전에** 대금·결제주기·해지조건을 고지하고 **동의**를 받아야 함. 동의 없으면 결제 연동 후에도 자동결제 금지.

## 서버가 주는 것 (이미 배포 대상)
- `GET /api/conversion/status?phone=` → `{ freeUntilMs, daysLeft, noticeDue, consented, consentedPlan }`
  - `noticeDue=true` 이면 앱이 D-14 유료전환 안내+동의 화면을 노출.
  - `consented=true` 면 이미 동의(또는 거부) — 재노출 안 함(설정에서 변경만).
- `POST /api/conversion/consent` `{ phone, plan: "standard_50k"|"premium_100k", agreed: true|false }`
  - 동의/거부 영수증 기록. 거부(agreed=false)면 plan 없이 호출.

## 앱이 할 것
1. 앱 진입/홈에서 `conversion/status` 폴링 → `noticeDue && !consented` 면 안내 모달.
2. 모달 내용(필수 고지): 전환 예정일(freeUntilMs), **요금제·금액(월 5만/10만, 부가세 별도)**, 결제 주기(월), **해지 방법**, "동의 시 만료일에 유료로 전환됩니다".
3. [동의하고 계속] → `conversion/consent {plan, agreed:true}` / [지금은 안 할게요] → `{agreed:false}` (또는 닫기).
4. 402 free_expired(게이팅 ON 시) upsell 모달과 연계 — 미동의로 만료되면 기능 잠금 + 요금제 선택 유도.

## 주의
- 실제 결제(토스)는 별도(체크리스트 C). 이 화면은 "동의 수집"까지만.
- 문구 최종은 약관 §8 / 전문가 확정본 기준. plan 값 = standard_50k / premium_100k 고정.
