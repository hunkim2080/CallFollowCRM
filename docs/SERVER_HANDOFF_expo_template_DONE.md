# 서버 → 앱 회신 · 박람회 계약서 템플릿 방식 (추가151)

작성: cowork · 2026-07-24 · **구현·검증 완료 · 배포 대기**
설계: `docs/EXPO_TEMPLATE_DESIGN.md` (android+사장님). 템플릿=서버 데이터.

## 1. 템플릿 정의 (앱이 이걸로 체크리스트 렌더)
- `GET /api/expo/template/{id}` → `{template:{id,name,header_fields,sections[],price_groups[],totals[]}}`. 현재 `julnun` 하나. 없으면 404.
  - sections: `{key,type:'matrix'|'checklist',title, (matrix: materials[],items[]) (checklist: items[])}`.
  - julnun: matrix(줄눈 13항목 × 재질[폴리우레아·케라폭시]) + checklist(실리콘 5) + checklist(청소 12).
  - price_groups: [{key:julnun,fields:[total,deposit,balance]},{key:cleaning,...}], totals:[grand_total,payer].
- `GET /api/expo/templates` → `[{id,name}]`.

## 2. room.template_id
- `POST /api/expo/room/create` 에 `template_id`(기본 `julnun`). `room/{id}`·live GET 의 `room_info.template_id` 로 노출.
- **템플릿 방은 상품 카탈로그(products/set) 불필요** — 세션 발급이 카탈로그 없이도 열림(줄눈은 항목=템플릿).

## 3. 선택 저장 (상담사=체크+가격, 고객=입주일)
- `POST /api/expo/contract/live/agent` 에 `template`(dict) 추가:
  ```json
  {"session_id","secret","template":{
     "julnun":[{"item":"현관바닥","material":"폴리우레아"}, ...],
     "silicone":["욕조테두리", ...], "cleaning":["바닥기계", ...],
     "prices":{"julnun":{"total","deposit","balance"},"cleaning":{...},"grand_total","payer"}}}
  ```
  → 서버 저장 + `final_amount = prices.grand_total`(없으면 줄눈+청소 total 합).
- `POST /api/expo/contract/live/customer` 에 `move_in_date`(입주일) 추가.
- `GET /api/expo/contract/live/{sid}` 응답: `template_id`, `template`(선택결과), `move_in_date`, `final_amount`(=grand_total).
- `POST /api/expo/contract/finalize`: 템플릿 모드면 선택+grand_total 로 저장(자유상품 "선택없음 400" 스킵). 필수 4항목(성함·연락처·동호수·서명)·완료확정 게이트는 동일.

## 4. 영수증 / submissions
- `GET /expo/r/{cid}`: 템플릿 계약이면 **구조화 렌더**(줄눈 항목·재질칩 / 실리콘·청소 체크 / 가격표 시공·예약·잔금·총금액·입금자 / 입주일 / 업체정보 / 약관). 자유상품이면 기존 계약내역 카드.
- `GET /api/expo/submissions` item 에 `template_id` 추가. 템플릿이면 `products`="줄눈 시공 표준 · N개 항목", `final_amount`=grand_total.

## 하위호환
- 기존 자유상품 방/계약 그대로 동작(검증됨). 신규는 template_id=julnun 기본.
- 새 업종 템플릿 = 서버 `_EXPO_TEMPLATES` 에 추가하면 앱 업뎃 없이 적용.

## 검증
- TestClient 14항목 ALL OK (템플릿 정의·room·live/agent grand_total·move_in·finalize·영수증 구조화·submissions·자유상품 하위호환).
- 미배포: bash server/deploy_phase1.sh
