# 설계 — 오프라인에서 서버로 보내는 것들의 「우체통」(Outbox) · 2026-10-04

> 종류: **설계 문서** (코드 없음). 작성: 안드로이드 Claude(설계 검토). 결정권: 사장님.
> 읽는 순서: §0(비유) → §1(지금 뭐가 사라지나) → §9(단계) 만 읽어도 결정할 수 있다. 나머지는 만드는 사람용.

---

## 0. 한 줄 요약 + 비유 (사장님용)

**지금 앱은 「내 폰에 적는 것」은 절대 안 잃는다. 그런데 「상대 사장님께 보내는 말」은 지하철에서 하면 그냥 공중에 흩어진다.**

### 비유 ① — 편지를 우체통에 넣느냐, 창밖으로 던지느냐
- 지금: 사장님이 "현장 주소 바뀌었어요" 라고 **창밖으로 소리친다.** 밖에 사람이 있으면(인터넷이 되면) 듣고, 없으면 **아무도 못 듣고 끝**이다. 사장님은 말했다고 믿는다.
- 바꾸면: 그 말을 **편지로 써서 우체통에 넣는다.** 집배원(앱)이 **길이 뚫리는 순간** 가져간다. 사장님은 "지금은 못 보내요, 연결되면 보내요" 라는 말을 듣는다. 며칠째 못 보내면 **"이 편지 아직 못 갔어요"** 라고 다시 말해준다.

### 비유 ② — 같은 편지가 두 번 가면
- 집배원이 편지를 전했는데 **"받았다"는 도장을 못 받고** 돌아온 경우가 있다(보냈는데 응답이 끊김). 그럼 다시 보낸다 → **상대는 같은 편지를 두 번 받는다.** "완료했어요" 알림이 두 번 울린다.
- 그래서 **편지마다 일련번호**를 적는다. 상대(서버)가 "이 번호 벌써 받았어요" 하고 **한 번만** 반영한다. 이게 「멱등성(idempotency)」이다.

### 결론 3줄
1. **우체통은 하나다.** 지금 사진 올리기가 쓰는 방식(안 올린 걸 모아뒀다 다시 올림)을 **모든 종류**가 같이 쓰게 넓힌다. 사진용 따로·협업용 따로 만들지 않는다(§12).
2. **1단계는 협업 4가지** — 일정 변경·주소 변경·협업 해제/취소 + 기존 사진 올리기를 그 위로 옮긴다. 가장 아프게 터지는 것부터.
3. **서버에 두 가지를 더한다** — ① `/api/shared/update-address` (앱이 **2026-08-02 부터 두 달째 404 에 대고 보내고 있었다**) ② 편지 일련번호(`X-Op-Key`)를 기억하는 작은 표 하나.

---

## 1. 지금 실제로 사라지는 것 (전수 조사)

조사 범위: `app/` 의 서버 POST/DELETE 호출 전부(43곳) + `server/main.py` 의 해당 핸들러.
판단 기준 네 가지 — ① **사장님이 적거나 누른 것인가** ② **순서가 중요한가** ③ **두 번 보내도 서버가 한 번만 반영하나(멱등)** ④ **오프라인에 날아가면 실제 피해**.

### 1-A. 🔴 날아가면 **남이 피해 보는 것** — 우체통에 넣는다

| # | 보내는 것 | 호출 자리 | ① 사장님 것 | ② 순서 | ③ 서버 멱등 | ④ 날아가면 | 단계 |
|---|---|---|---|---|---|---|---|
| 1 | 협업 **일정 변경** `POST /api/shared/reschedule` | `CustomerDetailViewModel.notifyCollabReschedule` (`runCatching` 으로 덮음, 말 안 함) | ✅ 날짜 | **최신 것만** 의미 있음 | ✅ `UPDATE scheduled_at_ms` | **B 사장이 옛 날짜에 현장에 간다** | **1** |
| 2 | 협업 **주소 변경** `POST /api/shared/update-address` | `CustomerDetailViewModel.propagateAddressToCollab` (`runCatching`) | ✅ 주소 | 최신 것만 | ✅ (만들면 UPDATE) | **B 가 옛 주소로 간다** — 그리고 **서버에 이 길이 아예 없다**(아래 §1-D) | **1 + 서버** |
| 3 | 협업 **해제(주인 A)** `POST /api/shared/end` | `CustomerDetailScreen.kt:829` (`runCatching`, **로컬은 이미 지움**) | ✅ 누름 | 1·2 **뒤**에 가야 | ✅ 409「이미 종료」= 끝난 것 | **B 는 협업이 살아있는 줄 알고 현장에 온다** | **1** |
| 4 | 협업 **그만두기(협업자 B)** `POST /api/shared/end` | `ScheduleViewModel.leaveCollabSite` (토스트는 하지만 **로컬은 이미 숨김**) | ✅ 누름 | — | ✅ 409 = 끝 | **A 는 B 가 오는 줄 알고 기다린다** | **1** |
| 5 | 협업 요청 **취소→해제** `cancel` 실패 시 `end` | `ScheduleViewModel.removeCollabAssignment` (**로컬은 이미 지움**) | ✅ 누름 | — | ✅ 409 → `end` 로 | **B 가 취소된 일을 수락한다** | **1** |
| 6 | 협업 **진행(출발/도착/완료+계좌)** `POST /api/shared/progress` | `HomeViewModel.completeCollabSite` · `SharedSiteViewModel.updateProgress` (실패 토스트, 로컬은 성공 때만) | ✅ 누름 | **같은 현장 안에서 순서** (완료 뒤 도착 = 「되돌리기」로 해석됨!) | ❌ **`INSERT shared_owner_events` + FCM** → 재전송 = **A 에게 완료 알림 두 번·카드 두 장** | **A 가 계좌를 못 받아 입금을 못 한다** | **2 + 서버 op_key** |
| 7 | 협업 **댓글** `POST /api/shared/comment` | `CustomerCollabParts` · `SharedSiteScreen` (실패 시 화면 안내) | ✅ **쓴 글** | 시간순 | ❌ `INSERT` → 두 번 보이면 두 줄 | 쓴 말이 사라진다 | **2 + 서버 op_key** |
| 8 | 협업 **증거 사진** `POST /api/shared/photo` | `SharedSiteScreen` | ✅ 사진 | — | ❌ `INSERT` | 사진이 사라진다 | 3 |
| 9 | 내 **현장 사진 백필** `POST /api/site-photo/owner-upload` | `OwnerPhotoUploadManager` (**이미 우체통 방식** — `site_photos.serverUploadedAt IS NULL` 을 모아 재시도) | ✅ 사진 | 오래된 것부터 | ❌ `INSERT` (돌리기→재업로드 때 **옛 사진이 서버에 남아 두 장**이 된다 — §1-D) | 지금은 안 날아간다(재시도함) | **1-b: 같은 우체통 위로 옮김** |
| 10 | 팀원 메모 **답글** `POST /api/team/note/reply` | `CustomerDetailViewModel:329` | ✅ 쓴 글 | — | ❌ INSERT | 답글 유실 | 3 |
| 11 | 사진 **삭제** `DELETE /api/team/photo/{id}` · `POST /api/shared/photo/delete` | `CustomerDetailViewModel:373` 등 | ✅ 누름 | — | ✅ (404 = 이미 없음) | 지운 줄 알았는데 PC 웹에 남음 | 3 |

### 1-B. ⚪ 날아가도 **스스로 낫는 것** — 우체통에 **안** 넣는다 (대신 「길 뚫림」 신호만 준다)

이것들은 「명령」이 아니라 **「지금 상태 통째」**를 보내는 것이다. 지난 걸 다시 보낼 필요가 없고, **다음 한 번이 전부를 덮는다.** 이미 **지문(hash)이 다를 때만 보내고, 성공해야만 지문을 저장**한다 → 실패하면 다음 기회에 알아서 다시 간다.

| 보내는 것 | 지금 재시도 기회 | 빠진 것 |
|---|---|---|
| 본폰 미러 스냅샷 `POST /api/mirror/snapshot` (`MirrorSyncManager`, `INSERT OR REPLACE`) | 변경 30초 뒤 · `ReminderWorker` ~3h | **인터넷 돌아온 직후** 트리거 |
| 웹 사진캘린더 피드 `POST /api/web/schedule-feed` (`WebFeedSyncManager`, 전량 DELETE+INSERT) | 변경 30초 뒤 | 같음 |
| 구글 캘린더 (`CalendarAutoSync` 지문 + `eventHash`) | 변경 4초 뒤 · 앱 켤 때 | 같음 |
| FCM 토큰 등록 `POST /api/push/register` (UPSERT) | 앱 켤 때마다 | 없음 |
| 서버 백업 `POST /api/app-backup` (번호당 1개 REPLACE) | `ReminderWorker` 하루 1회 | 없음 |

→ **설계**: 우체통을 비우는 신호(앱 시작·화면 복귀·**네트워크 복구**·주기 워커)가 올 때 **이 셋도 같이 찌른다**(`pushNow(force=false)` / `syncAll()`). 큐에 넣지 않는다. (§12-I: "저쪽이 바뀌면 이쪽도 바뀌어야 하나?" → 아니오. 역할이 다른 두 문이다.)

### 1-C. ⚪ **답을 기다려야 하는 것** — 우체통에 **안** 넣는다 (지금처럼 즉시 성공/실패를 말한다)

사장님이 **답을 보고 다음 행동을 정하는** 요청은 「나중에 보냄」이 성립하지 않는다.

- 협업 **초대** `shared/invite` — 서버가 `share_id`·링크를 줘야 다음이 진행된다. 이미 실패 토스트 있음.
- 협업 **수락/거절** `shared/respond` — **서버가 날짜 지난 수락을 막는다(409)**. 지하철에서 수락한 걸 세 시간 뒤 보내면 그 사이 날짜가 지날 수 있다. 결정은 그 자리에서. 이미 실패 토스트 있음. (§13② 서버가 최종 결정권자)
- 팀원 초대/제외, 접수서 발급, 견적 발행, 웹 QR 로그인, 로그인 인증, AI 호출 전부(답변 추천·다듬기·요약·분류), 진단 보내기.
- 여정 이벤트 `POST /api/event` — 사장님이 적은 게 아니라 **통계**. 메모리 버퍼(200개)라 앱이 죽으면 사라지지만, 서버에 dedup 이 없어 큐에 넣으면 **중복이 늘어 통계가 더 틀려진다.** 그대로 둔다.
- 답변추천 피드백 `SuggestionEventEntity.reportedToServer` — 통계. 지금 방식 유지.
- 크래시 보고 `CrashCatcher`(파일, 다음 켤 때 전송) — 잘 돌고 있고 한 건짜리라 그대로.

### 1-D. 🔎 조사하다 **발견한 것** (설계와 별개로 사장님이 아셔야 할 것)

1. **`POST /api/shared/update-address` 가 서버에 없다.** `server/main.py` 에 route 0건. 앱은 2026-08-02 부터 이 길로 보내고 있고(`docs/SERVER_HANDOFF_collab_update_address.md`), `runCatching` 으로 덮여 있어 **두 달째 아무도 모르고 404 를 받고 있었다.** → 사장님 버그신고("협업 주소 바꿔도 상대는 옛 주소")는 **오프라인 문제가 아니라 애초에 안 되는 것**이었다. 이게 §13①「조용한 실패」의 실물이다. 우체통이 있었으면 「주소 변경 N건 못 보냄」으로 **첫날 드러났다.**
2. **`markPaid`(입금 완료 알림)는 호출하는 곳이 0곳** — 서버 길은 있는데 앱이 안 쓴다. 이 설계에선 다루지 않는다(죽은 코드 정리는 별건).
3. **사진 돌리기 → 재업로드가 서버에 두 장을 남긴다.** `SitePhotoRepository.rotate` 가 `serverUploadedAt` 을 지워 다시 올리는데, 서버 `owner-upload` 는 무조건 `INSERT` 라 옛 사진이 남는다 → PC 웹에 누운 사진·선 사진이 같이 보인다. 멱등키(§5)로 같이 고칠 수 있다.
4. B 앱에 `collab_address_change` FCM 처리가 없다(`RingGoFcmService` 8종에 없음). 서버가 `shared_sites.addr` 만 고쳐도 B 는 다음 `with-me` 조회 때 새 주소를 받으니 **1단계엔 FCM 없이도 된다.**
5. 앱에 `ConnectivityManager` 사용이 **0곳** — 「인터넷 돌아왔다」를 듣는 귀가 없다. 그래서 1-B 의 스냅샷들도 복구 직후엔 안 가고 다음 변경/3시간을 기다린다.

### 1-E. 🧬 두 벌 셈 (§12-B) — 「모아뒀다 다시 보내기」가 지금 **몇 벌**인가

| 벌 | 어디 | 저장 방식 | 재시도 신호 |
|---|---|---|---|
| 1 | `OwnerPhotoUploadManager` + `site_photos.serverUploadedAt` | Room 칸(null=미전송) | 앱 시작·웹 로그인 |
| 2 | `JourneyEventRepository` | **메모리** 리스트(200) | 30초 루프 |
| 3 | `SuggestionEventEntity.reportedToServer` | Room 칸 | 배치 보고 |
| 4 | `CrashCatcher` | 파일 1개 | 다음 켤 때 |
| (5·6) | `MirrorSyncManager` · `WebFeedSyncManager` | prefs 의 지문 | 30초 디바운스·3h 워커 |

**4벌(명령형) + 2벌(상태형)**. §12-C 「2~5곳 → 옆에 하나 더 만들지 말고 먼저 합친다」에 해당한다.
합치는 범위는 **명령형**이다. 1(사진)은 1단계에서 옮기고, 2·3·4 는 「사장님이 적은 것」이 아니라 §12-I 잣대로 **같이 고쳐야 하는 사이가 아니다** → 그대로 둔다(단, 다섯 번째를 만들지 못하게 가드를 건다 §6).

---

## 2. 핵심 원칙 — 「명령」과 「상태」를 가른다

```
명령(op)   = "이 현장 일정을 15일로 바꿔라" · "이 협업을 끝내라" · "이 사진을 올려라"
            → 로컬은 이미 바뀌었고, 서버가 따라와야 한다. 못 보내면 그 말이 사라진다.  → 우체통
상태(snap) = "내 일정 전부는 지금 이렇다" (미러·웹피드·캘린더)
            → 다음 한 번이 전부를 덮는다. 지난 걸 다시 보낼 이유가 없다.             → 우체통 X
```

- 우체통에는 **작은 명령**만 넣는다(JSON 수백 바이트). 사진은 **참조**(`photoId`)만 넣고 보낼 때 파일을 읽는다.
- **한 현장(targetKey) 안에서는 순서를 지킨다.** 다른 현장끼리는 서로 안 기다린다.
- **같은 종류·같은 대상의 최신 것만 남기는** 종류(일정·주소·사진)와 **쌓이는** 종류(댓글)를 가른다.
- **서버가 최종 결정권자**(§13②): 400·403·404·409·413 은 「서버가 안 된다고 했다」 → **다시 보내지 않고 사장님께 말한다.** 5xx·타임아웃·끊김만 재시도.
- **연타**(§13③): 비우기(drain)는 `Mutex.tryLock` 으로 한 번에 하나. 넣기는 Room 이 직렬화.

---

## 3. 데이터 모델 — Room `outbox` 표 (DB **v61 → v62**)

```
outbox
  id               INTEGER PK AUTOINCREMENT   -- 넣은 순서 = 보내는 순서(같은 targetKey 안에서)
  kind             TEXT NOT NULL              -- "collab_reschedule" | "collab_address" | "collab_end" | "site_photo" | (2단계) "collab_progress" | "collab_comment"
  targetKey        TEXT NOT NULL              -- shareId / photoId. 순서·합치기의 단위
  opKey            TEXT NOT NULL UNIQUE       -- 편지 일련번호 = 멱등키. 넣을 때 UUID 생성. 헤더 X-Op-Key 로 보냄
  ownerPhone       TEXT NOT NULL              -- 넣을 때 로그인 번호(숫자만). 다른 번호로 로그인하면 안 보냄
  payloadJson      TEXT NOT NULL              -- 그 kind 의 handler 만 읽는다. 사진은 {"photoId":123} 참조만
  createdAtMs      INTEGER NOT NULL           -- 처음 넣은 시각(합쳐져도 유지 → 「며칠째」계산의 기준)
  attempts         INTEGER NOT NULL DEFAULT 0
  nextAttemptAtMs  INTEGER NOT NULL DEFAULT 0 -- 백오프. 0 = 지금 바로
  lastError        TEXT                       -- "HTTP 503" / "timeout" / "offline" — 진단·목록에 그대로 보여줌
  status           TEXT NOT NULL DEFAULT 'pending'   -- 'pending' | 'dead'  (보낸 것은 지운다 — 표가 안 커진다)
  deadReason       TEXT                       -- 사장님 말로("서버가 이 현장을 모른다고 했어요")
INDEX (status, nextAttemptAtMs)
INDEX (kind, targetKey)
```

규칙
- **보냈으면 행을 지운다.** 「보낸 것 목록」은 만들지 않는다(필요하면 타임라인 카드가 이미 있다).
- `dead` 는 **사장님이 보고 치울 때까지** 남긴다(「다시 보내기」/「그만」). 30일 지나면 자동 삭제.
- `site_photos.serverUploadedAt` 은 **남긴다 — 단 뜻이 바뀐다.** 「올렸다는 도장(결과)」이지 **「보낼 목록(큐)」이 아니다.** 보낼 목록은 `outbox` 하나다(§13④ 같은 뜻 칸 두 군데 금지). 그래서 `SitePhotoDao.pendingUpload()` 는 **지운다**, `markUploaded()` 는 handler 가 성공 때 찍는 데만 쓴다. 마이그레이션 때 `serverUploadedAt IS NULL` 인 사진을 한 번 `outbox` 로 옮긴다(`INSERT ... SELECT`, NOT NULL 칸 전부 채울 것 — GOTCHAS §6).
- `payloadJson` 은 **넣는 순간의 값**이다. 「보낼 때 다시 계산」은 안 한다(handler 가 도메인을 알아야 해서 커진다). 대신 **합치기(아래)** 로 최신을 보장한다.

### 합치기(coalesce) — kind 마다 하나

| 방식 | 뜻 | kind |
|---|---|---|
| `LATEST_WINS` | 같은 (kind,targetKey) 의 `pending` 행이 있으면 **payload 만 갈아끼운다** (id·createdAtMs 유지, attempts 유지, nextAttemptAtMs=0) | 일정·주소·사진·(2단계) 진행 |
| `APPEND` | 늘 새 행 | 댓글·사진 삭제 |
| `TERMINAL` | 이 행이 들어오면 같은 targetKey 의 **앞선 pending 행을 전부 지운다**(끝낸 현장에 일정 변경을 보낼 이유가 없다) | 협업 해제/취소 |

> 왜 `TERMINAL` 이 필요한가: 10:00 지하철에서 일정 변경 → 10:05 협업 해제. 둘 다 못 보냈다. 11:00 에 길이 뚫려 「일정 변경」을 먼저 보내면 B 에게 **「일정이 바뀌었어요」 알림이 간 뒤 「해제됐어요」** 가 간다. 헷갈리고 쓸데없다. 해제가 앞엣것을 지운다.

---

## 4. 언제 넣고, 언제 보내나

### 4-A. 넣는 시점 (enqueue) — **로컬 저장과 같은 호흡**

```
사장님 누름
 → SaveGuard.run("주소", _toast) {            // 지금 있는 그대로
       customerRepository.updateAddress(...)   // ① 내 폰에 저장
       outbox.enqueue(kind, targetKey, payload) // ② 우체통에 넣기 — 이것도 Room 쓰기라 실패하면 SaveGuard 가 말한다
   }
 → outbox.tryNow()                              // ③ 바로 한 번 보내본다 (온라인이면 지금과 똑같이 즉시 간다)
 → 결과로 토스트 고르기 (§7)
```
- ①과 ②는 **같은 트랜잭션**이면 제일 좋다(Room `withTransaction`). 못 하면 ① 뒤 ②.
- ③은 `viewModelScope` 가 아니라 **appScope** 에서(화면 닫혀도 계속). `NonCancellable` 과 같은 뜻.

### 4-B. 비우는 신호 (drain) — 다섯 군데, **전부 같은 함수 하나**를 부른다

| 신호 | 어디에 한 줄 | 비고 |
|---|---|---|
| 넣은 직후 | `enqueue` 뒤 `tryNow()` | 온라인이면 지금과 체감 동일 |
| 앱 시작 | `CallFollowCrmApplication.onCreate` — `ownerPhotoUploadManager.kick()` 자리 | 그 줄을 **대체**한다(사진이 우체통으로 들어오므로) |
| 화면 복귀·60초 루프 | `syncAllOnce()` 안 | 이미 `Mutex.tryLock` 으로 겹침 막음 |
| **네트워크 복구** | `ConnectivityManager.registerDefaultNetworkCallback` → `onAvailable` | **새로 단다**(지금 0곳). 같은 자리에서 미러·웹피드·캘린더도 찌른다(§1-B) |
| 앱 꺼진 동안 | `ReminderWorker.doWork()` 에 한 줄 (~3h) | 새 Worker 를 만들지 않는다(워커 두 벌 금지). 3h 가 느리면 2단계에서 `NetworkType.CONNECTED` 제약의 OneTime 작업 하나 추가를 **그때 결정** |

### 4-C. 보내는 순서와 백오프 (순수 함수 — 단위 테스트 §8)

```
drain():
  mutex.tryLock() 못 잡으면 return
  rows = pending AND nextAttemptAtMs <= now, ORDER BY id      -- 넣은 순서
  blocked = {}                                                 -- 이번 라운드에 실패한 targetKey
  for r in rows:
     if r.targetKey in blocked: continue                        -- 같은 현장 뒤엣것은 건너뜀(순서 보존)
     verdict = handler(r.kind).send(r.payload, r.opKey)
     DONE  → delete(r) (+ kind 별 후처리: 사진이면 markUploaded)
     RETRY → attempts+1, nextAttemptAtMs = now + backoff(attempts), lastError, blocked += targetKey
     DEAD  → status='dead', deadReason
  오프라인(ConnectivityManager 가 "없음")이면 네트워크를 시도하지 않고 즉시 return  -- 배터리·타임아웃 낭비 방지
```

백오프 `OutboxBackoff.delayMs(attempts)`: `0 → 30초 → 2분 → 10분 → 30분 → 1시간 → 3시간(상한)`, ±20% 지터. **횟수로 죽이지 않는다** — 사장님이 적은 것은 7일까지 계속 시도하고, 그 뒤 `dead`(「일주일째 못 보냈어요」).

### 4-D. 응답 → 판정 (kind 마다 조금씩 다름)

| 응답 | 기본 판정 | 예외 |
|---|---|---|
| 2xx | DONE | — |
| 끊김·타임아웃·5xx·429·408 | RETRY | — |
| 401 | RETRY(긴 백오프) | 세션 갱신은 인터셉터가 함. `X-Auth-Required` 면 재로그인 안내는 기존 길 |
| 403 | DEAD「본인 현장이 아니라고 했어요」 | **사진 `owner-upload` 의 403 은 티어 게이트** → RETRY(3h). 기존 동작 유지 |
| 404 | DEAD「서버가 이 현장을 모른다고 했어요」 | — |
| 409 | DEAD | **`collab_end`: 409「이미 종료」= DONE.** `cancel` 이 409 면 **같은 행에서 `end` 로 바꿔 보냄**(지금 VM 이 하는 걸 handler 안으로) |
| 400·413·422 | DEAD「내용이 잘못됐다고 했어요」 | — |

---

## 5. 멱등성 — 같은 편지가 두 번 가도 한 번만

### 5-A. 앱
- 행마다 `opKey`(UUID). 보낼 때 **헤더 `X-Op-Key: <uuid>`** 로. 재시도해도 **같은 키**.
- 왜 **헤더**인가: 서버는 pydantic 이라 **모르는 본문 칸은 조용히 버린다**(GOTCHAS §3). 본문에 `op_key` 를 넣으면 모델마다 칸을 추가해야 하고 하나라도 빠지면 **에러 없이 사라진다.** 헤더는 `request.headers` 로 어떤 핸들러든 읽는다. **길은 하나(헤더)만** 둔다.
- 붙이는 자리: `SessionAuthInterceptor` 가 아니라 **handler 가 요청마다 명시**로. (인터셉터는 「모든 요청」이라 GET 에도 붙는다 — 뜻 없는 헤더가 생긴다.)

### 5-B. 서버 — 작은 표 하나 + 헬퍼 둘 (Python 3.9, `Optional[str]`)

```sql
CREATE TABLE IF NOT EXISTS op_keys (
  op_key        TEXT PRIMARY KEY,
  owner_phone   TEXT NOT NULL,
  path          TEXT NOT NULL,          -- "/api/shared/progress"
  status_code   INTEGER NOT NULL,
  response_json TEXT NOT NULL,          -- 처음 응답 그대로 돌려준다
  created_at_ms INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_op_keys_created ON op_keys(created_at_ms);
```
- `_idem_lookup(op_key) -> Optional[dict]` : 있으면 **그 응답을 그대로 반환**(2xx). 앱은 DONE.
- `_idem_store(op_key, owner_phone, path, status, body)` : **성공(2xx) 응답만** 저장. 실패는 저장 안 함(다시 시도해야 하니까).
- 30일 지난 키는 기존 정리 루프에서 삭제.
- **적용 핸들러(INSERT 가 있는 것만)**: `shared/progress`, `shared/comment`, `shared/photo`, `site-photo/owner-upload`, (3단계) `team/note/reply`. 핸들러 시그니처에 `request: Request` 를 더해 헤더를 읽는다.
- **UPDATE 형(reschedule·update-address·end·cancel·paid)은 안 건다.** 이미 상태 기반 멱등이다(같은 값 다시 써도 같음). 단 reschedule 은 **재전송 때 FCM 이 또 간다** → B 에게 「일정 변경」 알림 두 번. 받아들일 만한가? → 1단계는 **받아들인다**(알림이 두 번 가는 것 < 안 가는 것). 거슬리면 2단계에서 `op_keys` 를 여기도 건다.
- **헤더 없는 요청은 지금과 똑같이 처리** → 옛 앱 무해.
- 배포는 GOTCHAS §3 절차(scp → 패치 → py_compile → 백업 → 교체 → kickstart → `smoke.sh` 10개). repo 와 라이브가 지금 같으니(2026-10-04) repo 에 먼저 넣고 **그 diff 만** 올린다.

### 5-C. 서버에 **새로 만들 길** — `POST /api/shared/update-address`
`docs/SERVER_HANDOFF_collab_update_address.md` 그대로. `reschedule` 핸들러 복사 수준: `share_id` 주인 확인(403) → `UPDATE shared_sites SET addr=?, customer_label=COALESCE(?,customer_label), updated_at_ms=?` → accepted 면 B 에게 FCM `collab_address_change`(B 앱 처리 없어도 무해). **이게 없으면 1단계 우체통의 「주소」 kind 는 전부 404 → dead 가 된다.** 순서: **서버 먼저**.

### 5-D. 사진 두 장 문제(§1-D-3)도 같은 키로
`site_photo` kind 의 opKey 를 **UUID 가 아니라 `photo:<localId>`** 로 고정하고, 서버 `owner-upload` 가 `X-Op-Key` 가 있으면 `team_site_photos` 에 `client_key` 칸(ALTER, NULL 허용)으로 저장 → 같은 키면 **UPDATE(교체)**. 돌리기 재업로드가 **덮어쓰기**가 된다. → 2단계(서버 칸 추가가 필요하므로).

---

## 6. 코드 자리 (§12-E 다섯 단계에 맞춰)

| 무엇 | 자리 | 성격 |
|---|---|---|
| 판정·백오프·합치기·순서·나이 (순수) | `domain/outbox/OutboxRules.kt` (`Verdict`, `Coalesce`, `delayMs`, `ageLabel`, `nextRound`) | **단위 테스트 대상** |
| 표·DAO | `data/local/entity/OutboxEntity.kt` · `data/local/dao/OutboxDao.kt` · `AppDatabase` v62 | Room |
| 우체통 본체 | `data/outbox/Outbox.kt` — `enqueue` / `tryNow` / `drain` / `observeTrouble` + `Mutex` | Android |
| 종류별 보내기 | `data/outbox/handlers/` — `CollabRescheduleHandler` · `CollabAddressHandler` · `CollabEndHandler` · `SitePhotoHandler` … 각각 **기존 Repository 함수를 부르기만** 한다(HTTP 코드는 그대로 Repository 에) | Android |
| 네트워크 귀 | `util/NetworkWatch.kt` — `isOnline()` + `onAvailable { }` | Android, **공용**(미러·웹피드·캘린더도 쓴다) |
| 말하기 | `presentation/util/OutboxWording.kt` — 「연결되면 보내요」「N일째 못 보냄」 **문구 한 곳** | UI |

가드(§12-E ④): `tools/dup_guard.gradle.kts` 에
```
DupRule(id = "outbox-only",
        regex = Regex("""pendingUpload\(|serverUploadedAt IS NULL|ArrayList<JSONObject>\(\)\s*//\s*retry"""),
        instead = "Outbox.enqueue(kind, targetKey, payload)  (data/outbox/Outbox.kt)",
        homes = listOf("/data/outbox/", "/domain/outbox/"))
```
+ §12 표에 한 줄: 「서버로 보내는 명령 | `data/outbox/Outbox.kt` | 화면·매니저에서 `runCatching { repo.xxx() }` 로 던지기 」.

기존 입구는 남긴다(§12-D ③): `OwnerPhotoUploadManager.kick()` → 안에서 `outbox.tryNow()` 한 줄. 지우는 건 호출부가 0 이 된 다음.

---

## 7. 실패를 사장님께 어떻게 보이나 — SaveGuard 와의 관계

### 7-A. 세 가지 말, 각각 한 곳

| 상황 | 말 | 누가 |
|---|---|---|
| **내 폰 저장**이 실패 (Room) | 「주소 저장을 못 했어요 — 다시 한 번 눌러주세요」 | **SaveGuard** (지금 그대로) |
| 저장은 됐고, **지금 못 보냄**(오프라인·서버 잠깐 죽음) | 「저장했어요 · 상대 사장님께는 **연결되면 자동으로** 보내요」 | `OutboxWording.queued(what)` — **실패가 아니다.** 빨간 토스트 X |
| 서버가 **안 된다고 함**(dead) 또는 **하루 넘게** 못 보냄 | 「협업 주소 변경 1건을 **하루째 못 보냈어요** — 눌러서 확인」 | `observeTrouble()` → 홈 상단 띠 |

- `SaveGuard` 는 **그대로 두고** 넓히지 않는다. 「저장 실패」와 「전송 보류」는 다른 말이고, 섞으면 사장님이 다시 누른다(두 번 넣는다).
- 토스트 문구는 `OutboxWording` **한 곳**. `_toast` 통로는 기존 ViewModel 것을 쓴다.
- `dead` 와 `하루째(≥24h)` 는 **반드시 보인다** — 「조용히 넘기면 안 되는 것」(§13①). 어디에: **홈 상단 띠**(업데이트 안내 띠와 같은 자리·같은 모양) → 탭 → 목록(무엇을·언제·왜 못 갔나·[다시 보내기]·[그만 보내기]).
  ⚠️ **이 화면은 프로토가 없다.** §0 룰대로 **프로토를 먼저 만들어 보여드리고 「가」를 받은 뒤** 만든다. 1단계는 **토스트 + 진단 보내기에 「미전송 N건·사유」 포함**까지만. 띠·목록은 프로토 뒤.
- `dead` 가 생기면 `DiagnosticsReporter` 본문에 자동으로 `[미전송] kind·target·reason` 을 붙인다 → 사장님이 「문제 신고」만 눌러도 우리가 안다. 서버 500 자가신고(슬랙)는 서버 쪽이 이미 한다.

### 7-B. 지금 토스트가 거짓말하는 자리 (고칠 것)
- `ScheduleViewModel.removeCollabAssignment` — 실패해도 로컬은 이미 지웠으면서 「지금 연결이 안 돼 처리를 못 했어요」: **실제론 내 쪽은 처리됐고 상대에게만 못 알린 것.** 우체통 뒤엔 「상대 사장님께는 연결되면 알려요」가 맞는 말.
- `CustomerDetailScreen:829` 해제 — `runCatching` 만 있고 **말이 없다**(§13①). 우체통으로 옮기면 자동으로 말이 생긴다.

---

## 8. 단위 테스트로 보는 것 (폰에서 눈으로 못 보는 것들)

| 테스트 | 고정하는 것 |
|---|---|
| `OutboxBackoffTest` | 0·30s·2m·10m·30m·1h·3h 상한 · 지터 ±20% 안 · 음수 없음 |
| `OutboxVerdictTest` | 코드→판정 표(§4-D) 전부. **`collab_end` 의 409 = DONE**, **`site_photo` 의 403 = RETRY**, 404 = DEAD. 글자(detail)가 아니라 **코드**로만 판정 |
| `OutboxCoalesceTest` | LATEST_WINS 는 payload 만 바뀌고 id·createdAt 유지 / APPEND 는 두 행 / **TERMINAL 이 같은 target 의 앞선 pending 을 지운다** |
| `OutboxOrderTest` | 같은 target 에서 앞이 RETRY 면 뒤는 이번 라운드 건너뜀 · 다른 target 은 진행 · dead 는 막지 않음 · ownerPhone 다르면 건너뜀 |
| `OutboxAgeTest` | 24h 미만 조용 / 24h~7d 「N일째」 / 7d dead 전환 · 문구는 `OutboxWording` 결과와 같음 |
| `DbUpgradeChainTest` (있음) + **61→62 `MigrationTestHelper`** (GOTCHAS §6-6: 61.json 이 있으니 이번부터 진짜로 올려본다) | 사진 백필 `INSERT…SELECT` 가 NOT NULL 칸을 다 채우는지 |

서버: `server/smoke.sh` 에 2개 추가 — ① `X-Op-Key` 같은 키로 `shared/comment` 두 번 → 댓글 **1건** ② `update-address` 200 + `with-me` 되읽기에 새 addr. (「보내고 되읽는다」— GOTCHAS §3)

---

## 9. 단계 (가장 아픈 것부터)

### 1단계 — 「상대가 엉뚱한 데 가는 것」을 막는다
**서버(먼저)**: `POST /api/shared/update-address` 신설(§5-C). `op_keys` 표+헬퍼는 만들어 두되 1단계 kind 엔 안 걸린다(전부 UPDATE 형).
**앱**: DB v62 `outbox` · `domain/outbox` 순수 규칙 + 테스트 5개 · `Outbox` 본체 · `NetworkWatch` · 5개 신호(§4-B) · kind 4개 — `collab_reschedule`(LATEST) · `collab_address`(LATEST) · `collab_end`(TERMINAL, cancel→end 포함) · **`site_photo`(LATEST, 기존 코드 verbatim 이동 + `pendingUpload` 삭제 + 백필 마이그레이션)**.
호출부 5곳 바꿈(§1-A #1~#5 + #9). 토스트는 `OutboxWording.queued`. dead 는 **진단 본문**에만.
가드 `outbox-only` + §12 표 한 줄 + `shared_baseline` 갱신.
끝낼 때 보고 숫자: 「명령형 모아보내기 4벌 → **1벌+(통계 3)** · `runCatching { sharedSiteRepository.x }` 5곳 → 0곳 · 테스트 +5 · DB v62 · smoke 12/12」.

### 2단계 — 「돈·말」
서버 `op_keys` 를 `shared/progress`·`shared/comment`·`owner-upload` 에 적용 + `team_site_photos.client_key`(사진 덮어쓰기 §5-D).
앱 kind 추가 — `collab_progress`(LATEST — 같은 현장의 마지막 단계만; 출발·도착 시각은 서버에 안 남는 걸 **감수**, 대신 A 에게 알림이 한 번만) · `collab_comment`(APPEND).
**홈 상단 띠 + 미전송 목록** — 프로토 먼저(§7-A).
3h 가 느리다는 실측이 있으면 `NetworkType.CONNECTED` OneTime 작업 1개.

### 3단계 — 나머지
`shared/photo`(협업 증거사진) · `team/note/reply` · 사진 삭제 2종. 여정 이벤트는 **사장님이 원하면** 그때(서버 dedup 부터).

---

## 10. 안 하는 것 / 위험

- ❌ **「동기화 엔진」을 만들지 않는다.** 충돌 해결·버전 벡터·양방향 머지 없음. 이건 **보내다 만 명령을 마저 보내는 우체통**이다. 서버→앱 방향은 지금의 폴링·FCM 그대로.
- ❌ 초대·수락·거절·AI·로그인은 넣지 않는다(§1-C). 「나중에 수락」은 다른 결정이다.
- ❌ 상태형(미러·웹피드·캘린더)을 넣지 않는다. 지문 방식이 이미 더 싸고 더 맞다. 신호만 공유.
- ❌ 새 WorkManager 워커·새 OkHttp 클라이언트·새 토스트 통로를 만들지 않는다. 있는 것에 한 줄씩.
- ❌ payload 에 사진 바이트를 넣지 않는다(참조만). 큐 표가 수십 MB 가 되면 Room 이 느려진다.
- ❌ 4xx 를 재시도하지 않는다. 404 에 대고 두 달 보내던 일을 또 만든다.
- ⚠️ **번호가 바뀌면**(다른 사업자번호로 로그인) 옛 번호의 행은 보내지 않고 목록에 남긴다. 403 으로 dead 되는 것보다 낫다.
- ⚠️ **옛 버전 앱**은 우체통 없이 지금처럼 던진다. 서버 변경은 전부 「헤더 있으면 더 잘, 없으면 지금처럼」이라 무해.
- ⚠️ **B 폰이 오프라인**인 경우는 이 설계 밖이다(FCM 은 폰이 켜지면 오고, `with-me` 폴링이 받는다).
- ⚠️ 1단계 `site_photo` 이동이 제일 「깨질까 봐」 무서운 자리다. 그래서 **① 쓰는 곳을 셌고(2곳: `kick`·`rotate`) ② handler 는 verbatim ③ `kick()` 입구는 남긴다 ④ 61→62 를 MigrationTestHelper 로 실제 올려본다.** 그래도 무서우면 사진만 **1-b 커밋으로 분리**해 같은 릴리스에 싣는다 — **릴리스 사이에 두 벌이 존재하는 날은 없게.**

---

## 11. 끝내기 전 세 가지 (§13 🧪)

- **느린 네트워크**: `callTimeout` 40초는 그대로. drain 은 appScope, 화면을 막지 않는다. 라운드당 상한 50행(사진 포함) — 300장 백필도 6라운드면 끝.
- **빈 값**: `targetKey` 빈 문자열은 enqueue 가 거부(IllegalArgument → SaveGuard 가 말함). payload 가 비면 handler 가 DEAD.
- **연타**: enqueue 는 Room 직렬 + LATEST_WINS 라 두 번 눌러도 한 행. drain 은 `tryLock`. 서버는 `op_keys`.

> 📍 이 문서가 「가」를 받으면: `docs/PROTOTYPES.md` 에 미전송 띠·목록 프로토 줄 추가(2단계 전) · `CLAUDE.md` §12 표에 Outbox 한 줄 · `docs/SYNC.md` 에 서버 변경(update-address, op_keys) 블록.
