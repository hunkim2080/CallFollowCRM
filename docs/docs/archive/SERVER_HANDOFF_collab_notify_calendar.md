# SERVER HANDOFF — 협업 캘린더 점(#7) 검증 + 협업 진행 알림(#8)

작성: 2026-06-09 · 안드로이드 Claude → 맥미니 Claude
배경: 사장님 실사용 보고 2건 (협업 현장).
- #7 "협업 승인했는데 캘린더에 점이 2개 안 찍힘"
- #8 "다른 사장이 출발/도착을 눌렀는데 알림이 하나도 안 옴"

---

## #7 — 캘린더 협업 보라점 (앱측 완료, 서버는 "확인만")

**앱측 이미 구현(이번 커밋):** 일정 탭 캘린더가 `GET /api/shared/with-me?phone={내 bizPhone}` 를 불러
`status=="accepted"` + `scheduled_at_ms>0` 인 현장의 날짜에 **보라점**(내 시공 점과 별개, 한 칸에 점 2개)을 찍음.

**서버가 보장해야 할 것 (이미 되어 있으면 OK):**
- `/api/shared/with-me` 응답의 각 site 에 **`status`**(수락된 건 `"accepted"`) 와 **`scheduled_at_ms`**(자정 기준 epoch ms 권장) 가 들어 있어야 함.
- 즉, B가 수락(`/api/shared/respond accept=true`)한 뒤 with-me 에 그 현장이 `status:"accepted"` 로 나와야 점이 찍힘.
- (현재 SharedSiteRepository.parseSites 가 `status`, `scheduled_at_ms` 를 읽음. 빠지면 점 안 찍힘 → **이 두 필드만 확인 부탁**.)

검증: B 폰에서 협업 수락 → 일정 탭 → 그 시공일에 보라점 뜨면 OK.

---

## #8 — 협업 진행(출발/도착/완료) 알림 (서버 endpoint 필요 → 그 뒤 앱 폴러)

**현재 왜 안 오나:** B가 `/api/shared/progress`(departed/arrived/completed) 를 보내도,
**A(현장 주인)가 그 이벤트를 받아갈 통로가 없음.** 앱엔 협업용 폴러가 없고(팀은 TeamEventCenter 가 폴링),
서버엔 "주인이 볼 협업 이벤트" 조회 endpoint 가 없음.

### 서버가 만들 것 (맥미니)
1. **이벤트 적재:** `/api/shared/progress` 처리 시, 그 share 의 **owner_phone** 앞으로 진행 이벤트 1건 적재
   (share_id, step, 발생시각, partner 이름/표시명, 현장 title). 완료(`completed`) 시 계좌 payload 도 포함.
2. **주인용 조회 endpoint (팀 events 와 같은 스타일):**
   ```
   GET /api/shared/owner-events?phone={A_bizPhone}&since_ms={마지막 본 시각}&limit=50
   → { "events": [
        { "event_id": "...", "share_id": "...", "title": "강동 천호동 현장",
          "partner_name": "박사장", "step": "departed|arrived|completed",
          "at_ms": 1733700000000,
          "account": { "bank": "...", "account_no": "...", "holder": "..." }  // completed 일 때만
        }, ... ] }
   ```
   - `phone` = 주인(A) bizPhone. since_ms 이후 것만. 최신순.
   - 고객 전화번호는 **절대 포함 금지**(협업 벽 — title/표시명만).
   - 참고: 입금완료(`/api/shared/paid`)는 반대로 **B**에게 가는 알림 → B용 events 에도 같은 패턴이면 좋음
     (지금 with-me 로 상태만 보임. 알림까지 원하면 `with-me` 응답에 `paid_at_ms` 가 이미 있으니 앱이 폴링으로 감지 가능).

### 앱이 만들 것 (안드로이드 — endpoint 나오면 착수)
- `CollabEventCenter`(TeamEventCenter 패턴): 앱 포그라운드/주기적으로 `owner-events` 폴링 →
  새 이벤트면 로컬 알림 "**박사장님이 [강동 천호동 현장] 출발했어요**" (도착/완료도). 완료면 계좌도 본문에.
- 상담함 배너로도 노출(팀 출발 배너와 동일 슬롯 재사용 검토).
- **지금은 endpoint 가 없어 앱 폴러 미착수** — `owner-events` 스펙 확정/구현되면 SYNC 회신 주세요. 그때 앱 붙입니다.

### 대안
- FCM 푸시가 서버에 이미 있으면 폴링 대신 푸시로 즉시 알림 가능. 단 현재 앱은 팀도 폴링이라, **폴링 endpoint 가 가장 빠른 경로**.

---

## 요약 (맥미니 할 일)
1. **#7**: `with-me` 응답에 `status`+`scheduled_at_ms` 있는지 확인(보통 이미 있음). 없으면 추가.
2. **#8**: `/api/shared/progress` → owner 이벤트 적재 + **`GET /api/shared/owner-events`** 신설. 위 JSON 스키마. → SYNC 회신.
