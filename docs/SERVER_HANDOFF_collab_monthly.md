# 서버 핸드오프 — 협업 월별 집계 (협업 기록 화면) · 2026-08-01

> 요청: android → cowork. 사장님 승인 기능(2026-08-01). 목업: `design-preview/collab_record_mockup.html`
> 목적: **기록·세금용** — "협업 사장별로 나랑 얼마나 일했나 + 월별로(7월/8월…)" 보기. **양방향(받은·준) 둘 다.**

## 배경 (지금 있는 것 vs 필요한 것)
- 지금 `GET /api/shared/partners` = **전체 기간 · "나를 부른 사장(with-me)"만** 집계. (SharedSiteRepository.Partner)
- 필요한 것 = **① 월별 필터 ② 양방향(received=내가 받은 / given=내가 준) ③ 현장별 상세(날짜·일당·입금여부).**

## 요청 엔드포인트
`GET /api/shared/monthly?phone=<owner11>&ym=<YYYY-MM>`
- `phone` = 요청 사장 번호(숫자11). (§B-2 인증 시 토큰 phone 과 일치 강제 — 기존 규칙대로)
- `ym` = 볼 달. 생략 시 = 데이터 있는 **가장 최근 달**.

### 응답(JSON)
```json
{
  "ym": "2026-07",
  "available_months": ["2026-08","2026-07","2026-06"],
  "received": {
    "count": 3, "total_wage": 90, "paid_total": 60,
    "partners": [
      {
        "partner_phone": "01011112222", "partner_name": "김철수",
        "count": 2, "total_wage": 60, "paid_total": 30, "last_at_ms": 1720000000000,
        "sites": [
          {"share_id":"abc","at_ms":1719705600000,"title":"강동 천호동 현장","wage":30,"paid":true},
          {"share_id":"def","at_ms":1720483200000,"title":"송파 헬리오시티","wage":30,"paid":false}
        ]
      }
    ]
  },
  "given": {
    "count": 2, "total_wage": 60, "paid_total": 60,
    "partners": [ /* 같은 모양. partner_name = 내가 부른 협업자 상호 */ ]
  }
}
```

### 필드 규칙
- **received** = 내가 협업자(B)로 참여 = **내 수입**. `partner_name` = 나를 부른 **현장주인(A) 상호**(shared_sites.owner_name).
- **given** = 내가 현장주인(A)으로 남을 부름 = **내 지출**. `partner_name` = 내가 부른 **협업자(B) 상호**(shared_sites.partner_name / respond 시 저장된 것).
- `wage` = 만원 단위 (기존 `daily_wage` 그대로). 없으면 0/생략.
- `paid`:
  - received: **A가 입금완료 표시**(`/api/shared/paid`) 됐으면 true.
  - given: **내가 입금완료 표시** 했으면 true.
- 집계 범위(권장, cowork 판단): **수락(accepted)+완료(completed)** 현장. 예정/미완도 넣되 `paid=false`. 거절/취소는 제외.
- `available_months` = 협업(양방향) 데이터가 하나라도 있는 달만, **최신순**. (앱 월 셀렉터가 빈 달 안 넘어가게)
- `ym` 필터 기준 = 각 현장의 `scheduled_at_ms` 가 그 달인 것.

## 앱 쪽 (android) — 병행 진행
- `SharedSiteRepository.monthly(phone, ym)` 추가 → 이 엔드포인트 호출.
- **서버 미구현(404)/실패 시 로컬 폴백**: 기존 `withMe`+`byMe` 결과를 앱에서 월·파트너로 그룹핑(단, `paid`는 로컬에 없어 "—" 처리). → 서버 준비되면 자동으로 정확본으로 승격.
- 화면 = `통계` 탭 → "협업 기록"(목업대로). 서버 없이도 뼈대는 뜸(폴백).

## 다음 액션
- cowork: 위 엔드포인트 구현 + 배포. 필드명/모양 이대로면 앱 수정 없이 바로 붙음. 바꿔야 하면 SYNC 회신.
- android: 앱 화면 먼저 제작(폴백으로 동작) → 서버 나오면 붙임.
