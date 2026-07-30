# 서버 핸드오프 — 협업 현장 "일정 변경" 알림 (android → cowork, 2026-07-16)

## 사장님 요청

> "시공일정을 변경하면 협업을 맺은 사장한테도 '일정이 변경됐습니다. 21일(수)→23일(금)' 이런 식으로 알람 가면 좋겠어."

현재: A(현장 주인)가 시공일정을 바꾸면 **A 로컬만** 갱신됨. 완료·입금·댓글·사진·해제는 협업 사장(B)에게 알림이 가는데
**"일정 변경"만 그 목록에 없음** = B는 옛 날짜를 그대로 들고 있음. 이 통로를 새로 만든다.

## 앱은 이미 다 됨 (이 커밋)

- **A측 전송**: `CustomerDetailViewModel.updateScheduledWorkDate` 에서 날짜가 실제로 바뀌고(취소 아님) 그 고객 현장에
  협업 배정이 있으면 → `SharedSiteRepository.reschedule(...)` 호출.
  - 협업 배정은 `preferences.collabAssignments` 의 `"customerId|phone|name|shareId"` 에서 shareId 를 뽑음(한 고객에 여러 협업이면 전부).
- **B측 수신**: `RingGoFcmService` 가 `type=collab_reschedule` 를 받으면 `NotificationHelper.showCollabReschedule(...)` 로 알림.
  - 소리는 우선 **협업 현장 소식(collab_comment) 채널 재사용** — 전용 "일정 변경" 소리 분리는 사장님 확인 후(아래 §참고).
- **서버 미구현 동안 안전**: A측 `reschedule` 호출은 `Result` 라 404여도 조용히 무시(로컬 일정은 이미 바뀜). **B에게 알림만 안 갈 뿐**, 앱은 정상.

## 서버가 만들 것 — `POST /api/shared/reschedule`

### 요청 (앱이 보내는 것, `SharedSiteRepository.reschedule`)

```json
{
  "share_id": "<현장 공유 id>",
  "owner_phone": "01012345678",        // 숫자만. 요청자 = 현장 주인 A (권한 확인용)
  "scheduled_at_ms": 1750000000000,    // 새 시공일 (startOfDay ms, KST 자정)
  "old_scheduled_at_ms": 1749800000000,// 옛 시공일 (있을 때만; "21일→23일" 표시용)
  "time_label": "오전 8시"              // 있을 때만 (시공 시간, 없으면 생략)
}
```

### 서버 처리

1. **권한 확인**: `share_id` 의 `owner_phone` 이 요청 `owner_phone` 과 일치할 때만 진행(남의 현장 못 바꿈). 아니면 403.
2. **상태 확인**: 그 협업이 **accepted(수락됨)** 일 때만 push. `pending`(상대 미수락)·`declined`·`ended`·`cancelled` 는
   갱신만 하고 **push 안 보냄**(안 그러면 거절한 사람한테도 알림 감). ← 중요.
3. **DB 갱신**: `shared_sites.scheduled_at_ms = scheduled_at_ms` (+ time_label 저장하면 좋음). B의 `with-me` 조회가 새 날짜로 나오게.
4. **FCM push → 협업 사장(B)** (data-only, notification 블록 없이):

```json
{
  "type": "collab_reschedule",
  "share_id": "<share_id>",
  "title": "<현장 표시명>",
  "scheduled_at_ms": "1750000000000",   // 새 (문자열)
  "new_at_ms": "1750000000000",         // (선택) 앱은 new_at_ms 우선, 없으면 scheduled_at_ms 사용
  "old_at_ms": "1749800000000",         // (선택) 있으면 "6/21(수) → 6/23(금)" 로 표시
  "time_label": "오전 8시"               // (선택)
}
```

- 앱은 라벨을 **at_ms 로 직접 포맷**(`M/d(E)` → "6/23(금)")하므로, 서버가 `*_label` 을 안 줘도 됨. at_ms 만 정확히 주면 됨.
- B가 여러 명(한 현장 여러 협업)이면 각 accepted 파트너에게 각각 push.
- 토큰 없는 B(앱 미설치/링크 협업)는 skip(무음). 폴링 안전망은 아래 §선택.

### 응답

```json
{ "ok": true, "notified": 1 }   // notified = push 보낸 파트너 수
```

- 미구현/오류여도 앱은 graceful. 하지만 구현되면 위 형식으로.

## §선택 — 폴링 안전망 (FCM 못 받는 경우)

완료/입금처럼 FCM 이 유실될 수 있음. 기존 `with-me` 응답이 이미 `scheduled_at_ms` 를 주므로 **B 앱이 다음 새로고침 때
새 날짜로 자동 갱신**됨(일정은 안전). 다만 "변경됐어요" **알림**까지 폴링으로 잡으려면 `owner-events` 스타일로
`reschedule` 이벤트를 B가 받아갈 수 있게 해도 됨(필수 아님 — 완료/입금과 동일 판단).

## §참고 — 전용 소리 (사장님 확인 후)

지금 B 알림은 **협업 현장 소식(collab_comment) 소리**를 재사용한다. 사장님이 8가지 협업 상황을 소리로 구분하고
싶어 하셨으니(2026-07-15), 원하시면 나중에 **"일정 변경" 전용 소리 슬롯**(`collab_reschedule`)을 추가한다.
= 앱 작업(NotificationHelper SOUND_SLOTS + 채널 + 소리 파일). 이번엔 재사용으로 두고 사장님 판단 대기.

## 검증

- A 폰: 협업 배정된 현장의 시공일을 바꾼다 → 서버 로그에 `POST /api/shared/reschedule` + shared_sites 날짜 갱신.
- B 폰: "📅 협업 현장 일정 변경 — 'OO현장' 일정이 6/21(수) → 6/23(금) 로 바뀌었어요" 알림. 탭 → 그 협업 현장.
- pending/거절 협업은 push 안 오는지 확인.

## SYNC

- 시작 전: `git pull --rebase` + `tail -100 docs/SYNC.md`
- 끝난 후: SYNC.md append + commit + push.

— android (데스크탑 Claude Code)
