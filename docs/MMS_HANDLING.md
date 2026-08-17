# RING-GO(시공막내) — MMS(사진 문자) 처리 방식 (검토용)

> 목적: 지금 우리 앱이 MMS(사진 첨부 문자)를 **감지 → 조회 → 캐시 → 화면 표시**하는 전체 방식을 정리.
> 페이블(다른 모델) 검토용. 설계 근거 + 트레이드오프 + 열린 질문을 끝에 정리함.
> 스택: Android, Kotlin/Compose, Room. minSdk 26. 대상 기기 예: 갤S23U(OneUI), 사장님 폰 시스템 MMS 약 8,900건.

---

## 0. 가장 중요한 제약 — 우리는 "기본 문자앱"이 아니다

- RING-GO 는 **기본 SMS/MMS 앱이 아님.** 삼성 메시지가 기본앱이고, **MMS 다운로드(통신사→폰 저장)는 삼성이 담당.**
- 우리는 시스템 MMS provider(`content://mms`)를 **READ_SMS 권한으로 읽기만** 한다. (기본앱 전환은 과거 시도했으나 klinker 경로에서 사진 유실 → 철회. 삼성 메시지를 기본앱으로 두는 게 안전하다는 결론.)
- 결과: **MMS 수신 실시간 브로드캐스트(WAP_PUSH_DELIVER)를 못 받는다.** (그 경로 코드 `MmsReceiver`→`MmsDownloadService`(klinker wrap)는 존재하지만 기본앱일 때만 발동 → 현재 비활성.)
- 따라서 새 MMS 는 **provider 변경 감시(ContentObserver) + 폴링**으로 알아챈다.

---

## 1. 시스템 MMS 데이터 모델 (읽는 대상)

- `content://mms` row: `_id`, `date`(**초 단위**, ×1000 해야 ms), `msg_box`(1=INBOX 받음, 2=SENT 보냄), `thread_id`
- `content://mms/{id}/addr`: `address`, `type`(137=FROM, 151=TO). **OneUI 함정**: 사장님이 보낸 MMS 는 TO 행이 실제 번호 대신 `"insert-address-token"` placeholder 인 경우가 흔함 → address 매칭만으론 sent MMS 누락.
- `content://mms/part` (WHERE `mid`={mmsId}): `_id`, `ct`(content type), `text`
  - `ct="text/plain"` → 본문(비면 part 파일 fallback 읽기)
  - `ct` 가 `image/*` → 이미지 첨부. URI = `content://mms/part/{partId}` (Coil 로 렌더)

---

## 2. 감지 (새 MMS 가 왔다는 걸 알아채기)

기본앱이 아니라 실시간 push 가 없으므로 3중 안전망:

1. **ContentObserver** — `content://mms-sms`, `content://mms`, `content://mms/inbox` **3개 URI 모두** 등록.
   - 이유: 삼성이 MMS 저장을 알리는 URI 가 기종마다 달라, 하나만 보면 알림을 놓쳐 감지가 60초 폴링까지 밀림.
   - onChange → debounce 후 **1.2초 + 5초 두 번** `syncMmsContacts()`. (큰 사진은 삼성이 여러 번 나눠 저장해 첫 스캔에 아직 안 박히는 경우 보강)
   - `syncMmsContacts()` 는 **홈 연락처 목록 캐시(sms_contacts_cache)** 만 갱신. (대화별 메시지 캐시는 아님)
2. **60초 폴링** (`syncAllOnce`) + **앱 복귀(ON_RESUME)** 시 1회.
3. 위 둘 다 앱 프로세스가 살아있어야 동작. (백그라운드에서 OS 가 프로세스 종료하면 감지 못 하고, 앱 재진입 시 따라잡음. 완전 백그라운드 감지는 미구현 — 통신사 MMS 라 FCM 으로 깨울 수도 없음.)

파일: `CallFollowCrmApplication.kt`(observer/poll/syncMmsContacts), `MmsReceiver.kt`/`MmsDownloadService.kt`(기본앱 경로, 현재 비활성).

---

## 3. 조회 (`SmsRepository`)

### queryMmsOnly(phoneNumber, scanLimit=2000) → queryMmsByPhone(suffix, scanLimit)
- **전역 스캔 후 필터 방식**: `content://mms` 를 date DESC 로 최근 `scanLimit` 행 훑고 → 번호 끝 8자리(suffix)로 필터.
  - ⚠️ 즉 특정 대화만 콕 집는 게 아니라 **전역 최근 N행** 안에서 그 번호 것을 찾음.
- **3-pass**:
  1. Pass1: 최근 rows 메타(_id, date, box, thread_id) 수집(INBOX/SENT만).
  2. Pass2: 각 row 의 addr 조회(`getMmsAddresses`) → suffix 매칭 → 매칭된 row 의 thread_id 수집.
  3. Pass3: address 매칭 실패했지만 thread_id 가 (SMS 시드 ∪ Pass2 매칭) 집합에 있는 row 추가.
     - **sent MMS placeholder 복구 핵심**: SMS 로 같은 번호와 주고받은 thread_id 를 시드로 써서, TO=placeholder 인 보낸 사진을 같은 thread 로 살림.
- 각 picked row → `getMmsParts` 로 본문/이미지 URI 채움. 본문·이미지 둘 다 없으면 skip.

### provider 조회 재시도 (`queryProviderWithRetry`, 3회/50ms)
- `getMmsAddresses`, `getMmsParts` 의 `contentResolver.query()` 가 **provider 경합 시 일시적으로 null 반환** → 그 MMS 가 통째로 누락되던 문제. null(=실패)일 때만 재시도(행 0개 정상 커서는 즉시 통과).

### queryRecentMmsContacts / fillFromMms (홈 목록용, mmsScanLimit=120)
- 홈 상담함 카드용 연락처 목록. `msg_box` 로 sent/received 방향(lastSent) 판정. SMS 와 같은 suffix 면 더 최신 쪽이 lastBody/방향 결정.

---

## 4. 캐시 (Room `cached_messages`)

- 목적: ChatScreen 진입 시 provider 직접 조회(무거움, 수 초)를 피하려 로컬 캐시 즉시 표시.
- Entity 키: `(systemId, isMms)` **unique index** (SMS/MMS id 도메인이 달라 함께 묶음). 이미지 URI 는 `imageUrisCsv`("|"조인)로 저장.
- **핵심 정책: MMS 캐시는 절대 replace(clear+insert) 하지 않는다 = merge(누적)만.**
  - `mergeMmsForSuffix(suffix, freshMms)` = clear 없이 `insertAll`(OnConflict=REPLACE). 같은 원본 갱신 / 새것 추가 / **안 스캔된 옛것 유지.**
  - **왜?** 전에는 replace 였는데, 8,900 MMS + 사장님이 사진을 계속 보내는 환경에서 스캔이 provider 경합으로 **불완전(일부 손실)** 하게 돌아오면, 그 불완전 결과가 좋은 캐시를 통째로 덮어써 **"재진입하면 사진 싹 사라졌다 잠시 뒤 다시 뜸"** 버그가 났음. 또 prefetch(작은 scanLimit)가 큰 캐시를 축소시키기도 했음.
  - merge 는 캐시를 절대 줄이지 않아 스캔이 불완전해도 사진이 안 사라짐.
- **트레이드오프(사장님 승인):** 삭제 즉시반영 포기. 삼성 메시지에서 사진을 지워도 앱엔 한동안 남음. (사장님: "문자에서 사진 삭제할 일 없다. 남아있어도 상관없다.")
- 캐시 상한: 현재 무제한 증가(대화별 누적). `trimOldest(n)` DAO 는 있으나 아직 호출 안 함. → **추후 상한 관리 필요.**
- SMS 는 이 문제 없음: `querySmsByPhone` 은 SQL `address LIKE '%suffix%'` 로 **대화 전체**를 가져와 replace 해도 안 줆.

파일: `CachedMessageRepository.kt`, `CachedMessageEntity.kt`, `CachedMessageDao.kt`.

---

## 5. ChatScreen 메시지 로드 (`ChatViewModel.loadMessages(fullScan)`)

3-stage(체감 즉시 + 점진 최신화):
- **stage 1**: 캐시(`load(suffix)`) 즉시 표시. (누적 merge 캐시라 예전 사진 포함 → 진입 즉시 보임)
- **stage 2**: 시스템 SMS 만 빠르게(`querySmsOnly`, SQL 필터라 빠름) + 캐시 MMS 합쳐 emit.
- **stage 3**: MMS 스캔 → **merge** → 화면엔 **누적 캐시 전체(`loadMmsOnly`)** 표시.
  - `fullScan=true`(첫 진입): scanLimit **2000** (깊게).
  - `fullScan=false`(화면 대기 중 provider 변화로 재조회): scanLimit **80** (얕게, 빠름). 방금 온/보낸 MMS 는 전역에서도 최근이라 80 안에 들어옴.
  - 두 경우 다 merge + 누적캐시 표시 → **스캔이 불완전해도 사진 안 사라짐.**

- 화면 갱신 트리거: 진입 `LaunchedEffect` 1회(fullScan=true) + ChatScreen 의 `content://sms`/`content://mms` ContentObserver(300ms debounce)로 재조회(fullScan=false).
- 발신 보존본(`localSent`, systemId<0): 기본앱이 아니라 우리가 보낸 문자가 시스템 문자함에 안 남는 경우 대비 로컬 보존.

파일: `ChatViewModel.kt`, `ChatScreen.kt`.

---

## 6. Prefetch (`SmsCachePrefetcher`)

- 시점: 앱 시작 최근 20연락처 + 홈 화면 보이는 카드 + SMS 수신 시.
- MMS: `queryMmsOnly(scanLimit=500)` → **mergeMmsForSuffix**(누적). (앱시작에 20×2000 = 4만행 스캔이 앱 전체를 느리게 해 500 으로 축소했고, merge 라 캐시 축소 안 됨.)

---

## 7. 성능 특성 (솔직히)

- **첫 진입(캐시 없는 대화)**: stage-3 전역 2000 스캔 + 행마다 addr 조회 = 최대 2000 round-trip → 8,900 MMS 폰에서 수 초. (단, stage-1 캐시가 있으면 즉시 표시되므로 체감은 재진입부턴 빠름.)
- **화면 대기 중 새 사진**: 얕은 80 스캔이라 보통 1초 내.
- 재시도(retry)는 provider 경합 시 지연을 더할 수 있으나, merge+캐시표시로 지연이 화면을 막진 않음.

---

## 8. 알려진 트레이드오프 / 열린 질문 (검토 포인트)

1. **전역-스캔-후-필터 vs thread_id 직접 조회**
   - 현재: `content://mms` 최근 N행 훑고 suffix/thread 필터. → 특정 대화의 오래된 사진이 최근 N행 밖이면 그 대화 첫 스캔에 안 잡힘(재진입/누적으로 보완).
   - 대안: `content://mms WHERE thread_id IN (...)` 로 그 대화만 직접 조회 → 훨씬 빠름. **리스크**: OneUI 의 sent placeholder/멀티 thread 케이스에서 매칭 누락 우려(과거 사진 유실 트라우마). 실기기(8,900 MMS)로만 검증 가능.
   - ❓ thread_id 직접 조회로 가도 안전할까? 안전하게 가는 방법?
2. **merge-only + 무제한 캐시 성장** — 삭제 반영 포기(승인됨). 캐시 상한/trim 전략은? 언제 어떻게?
3. **감지 방식** — ContentObserver(3 URI) + 60초 폴링 + 프로세스 생존 의존. 더 나은 방법? (WorkManager 15분 주기 백스톱? 배터리 vs 신뢰성)
4. **retry(3×50ms)** — provider null 흡수용. 경합 시 지연 누적 가능. 상한/대안?
5. **날짜 단위 함정** — mms `date` 초 단위(×1000). 캐시/정렬 일관성 OK?
6. **전반 구조** — 3-stage + merge 캐시 + 얕은/깊은 스캔 분기가 과한가/맞는가?

---

## 9. 관련 파일 요약

| 파일 | 역할 |
|---|---|
| `data/repository/SmsRepository.kt` | queryMmsOnly / queryMmsByPhone(3-pass) / getMmsAddresses / getMmsParts / queryProviderWithRetry / queryRecentMmsContacts(fillFromMms) |
| `data/repository/CachedMessageRepository.kt` | mergeMmsForSuffix / loadMmsOnly / replaceMmsOnlyForSuffix(MMS 는 이제 미사용) |
| `data/local/entity/CachedMessageEntity.kt` | (systemId,isMms) unique, imageUrisCsv |
| `data/SmsCachePrefetcher.kt` | 백그라운드 prefetch(merge, scanLimit=500) |
| `presentation/screen/chat/ChatViewModel.kt` | loadMessages(fullScan) 3-stage |
| `presentation/screen/chat/ChatScreen.kt` | 진입/재조회 ContentObserver |
| `CallFollowCrmApplication.kt` | 감지 observer(3 URI)/60초 폴링/syncMmsContacts |
| `service/MmsReceiver.kt`, `service/MmsDownloadService.kt` | 기본앱 WAP_PUSH 경로(현재 비활성) |

---

## 10. 우리가 겪은 문제 → 시도 → 개선 (시간순 디버깅 기록)

이 순서로 부딪히며 고쳤음. (검토 시: 이미 실패한 시도를 다시 제안하지 않도록 참고)

### 문제 A — 채팅 화면에 있을 때 기존 사진이 사라졌다 다시 뜸(깜빡임)
- **원인**: ChatScreen 이 `content://sms/mms` 변화마다 `loadMessages` 재실행 → stage-3 `queryMmsOnly` 가 provider 경합으로 **일시 빈 결과**를 주면 그걸 그대로 화면·캐시에 덮어씀.
- **시도1**: stage-3 "빈 결과 가드"(freshMms 비었는데 캐시 있으면 덮어쓰기 skip). → **부분 완화**(완전 빈 것만 막음).
- **시도2**: `getMmsParts`/`getMmsAddresses` 의 `query()` null 반환 재시도(3×50ms). → 행 누락 줄임. → **완화**.
- **최종 해결**: 아래 문제 D 의 merge 전환으로 근본 제거.

### 문제 B — 진입 시 예전 사진이 안 뜨고, 느린 스캔 끝나야 다 같이 뜸
- **원인(병렬 조사로 확정)**: 챗 열 때 stage-3(2000 스캔)가 완전 캐시를 넣어도, 이후 **prefetch 가 `scanLimit=500` 로 replace(clear+insert)** → 사장님이 사진을 계속 보내 전역 MMS 가 빠르게 쌓이면 이 대화 예전 사진이 "최근 500(전역)" 밖으로 밀려 **캐시가 축소**됨. (직전 최적화 커밋 b1c2037 = prefetch 2000→500 이 악화시킨 회귀)
- **개선(bdd14cc)**: prefetch 를 replace→**merge**(누적)로. 이후 절대 캐시 축소 안 함.

### 문제 C — "사진 하나 보냈는데 엄청 오래 걸림"
- **원인**: 화면 대기 중 재조회마다 **전역 2000 행 스캔 + 행별 주소조회** = 8,900 MMS 폰에서 수 초.
- **개선(3a177d3)**: 첫 진입만 깊게(2000), **재조회는 얕게(80) 증분 스캔 → merge**. 방금 온 사진은 전역에서도 최근이라 80 안에 포함 → 1초 내 표시.

### 문제 D — merge 로 바꿨는데도 "재진입하면 사진 싹 없어졌다 시간지나면 다시 뜸"
- **디버깅**: 기기 DB 를 직접 뽑아(`adb exec-out run-as ... cat databases/…` → python sqlite3) 확인 → **캐시엔 사진이 멀쩡히 있었음**(테스트 대화 MMS 368/이미지 201, 최근 것까지).
- **원인**: 그런데도 사라짐 = **첫 진입 stage-3 가 여전히 replace** 였고, 2000 스캔이 경합으로 **불완전(일부만)** 돌아오면 그 불완전 결과가 좋은 캐시를 부숨. (문제 A 의 "빈 결과 가드"는 완전 빈 것만 막고 **부분 손실은 못 막음**.)
- **개선(1b667ed)**: **stage-3 도 replace→merge**. 화면엔 `freshMms`(그때 스캔) 대신 **`loadMmsOnly`(누적 캐시 전체)** 표시. → 스캔이 불완전해도 사진이 **구조적으로 사라질 수 없음**.

### 부수 — 감지 지연("1분/2분 들쭉날쭉")
- ContentObserver 를 `content://mms` 하나만 관찰 → 삼성이 다른 URI 로 알리면 놓쳐 60초 폴링까지 밀림.
- **개선**: `mms-sms`/`mms`/`mms/inbox` **3 URI** 관찰 + 1.2초/5초 2단 스캔.

### 지금 상태(원칙)
- **MMS 캐시는 어디서도 replace 안 함 = 누적(merge)만. 화면은 누적 캐시 전체 표시.** → 한 번 본 사진은 사라지지 않음.
- 대가: 삭제 즉시반영 포기(사장님 승인). 캐시 상한 미구현(추후 trim).

---

## 11. 검토 요청 — 더 좋은 방향이 있을까?

지금 방식은 "**전역 스캔+필터 → 누적 merge 캐시 → 캐시 전체 표시**"로, *사진이 안 사라지게*는 확실히 잡았지만 근본적으로 무거움(첫 스캔 O(전역 N) + 행별 addr 조회)과 무제한 캐시가 남음. 아래를 특히 봐주면 좋겠음:

1. **조회를 thread_id 기반 직접 조회로?** `content://mms WHERE thread_id IN (그 대화 thread들)` 로 바꾸면 전역 스캔을 없애 훨씬 빠름. 다만 OneUI 의 sent placeholder/멀티 thread 에서 누락 리스크(과거 사진 유실 경험). → **안전하게 thread 직접 조회로 전환하는 설계**가 있나? thread_id 를 신뢰 가능하게 모으는 법?
2. **감지** — 프로세스 생존 의존(백그라운드 종료 시 재진입까지 못 잡음). WorkManager 백스톱 vs 배터리? 더 나은 신뢰성/실시간성 방법?
3. **캐시 성장** — merge-only 무제한. 합리적 상한/트림 전략(대화별 최근 N? 전역 LRU? 오래된 이미지 URI 만 제거?)
4. **retry(3×50ms)** — 경합 시 지연 누적. 더 나은 백프레셔/타임아웃?
5. **전체 구조** — 3-stage + 얕은/깊은 분기 + merge 가 과한가? 더 단순하고 견고한 모델이 있나?
6. 놓친 함정(날짜 초/ms, 멀티심, 그룹 MMS, 매우 긴 대화 등)?
