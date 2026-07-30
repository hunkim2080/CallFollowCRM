# 핸드오프 — 통화 녹음 "직접 고르기"(수동 연결) (android, 2026-07-16)

> **읽는 사람에게 먼저:** 사장님이 "맥미니에게 핸드오프"로 요청하셨으나, **조사 결과 서버(맥미니) 작업은 0건**입니다.
> 근거는 §3. 이 문서는 **앱(안드로이드) 작업 명세**이며, 맥미니는 §6(진단 협조)만 필요 시 봐주면 됩니다.
> — CLAUDE.md §1 영역 분담상 `app/` 은 데스크탑 Claude Code 담당입니다.

## 1. 사장님 보고 (2026-07-16, 현장)

> "통화 파일을 찾았는데 이 통화요약하기 누르면 녹음파일을 못찾았다고 나오거든?"
> "지금 현장이라 다른 폰 설치해주고 있는데 통화요약이 안되는거야. **수동으로 연결 가능한 기능 있으면 좋겠네**."

- 설정 → 🎙️ 통화 녹음 자동 찾기 = **"✅ 자동 찾기 · 녹음 N개 발견"** (사장님 확인) → 앱은 녹음을 N개 보고 있음.
- 그런데 **특정 통화 한 건**에 [통화 요약하기] 를 누르면 "이 통화의 녹음 파일을 못 찾았어요".

## 2. 원인 — 앱이 녹음을 고르는 유일한 기준 = **파일 이름**

`AdotFolderScanner.summarizeCallNow` (app/src/main/java/com/detailline/callfollowcrm/recording/AdotFolderScanner.kt:379-389):

```kotlin
val parsed = AdotFilenameParser.parse(name) ?: continue        // ① 이름이 [번호_날짜] 형식이어야 함
if (parsed.phoneNumber.takeLast(8) != target) continue          // ② 이름 속 번호 끝 8자리 == 통화 번호
val delta = abs(parsed.recordedAt - callAtMs)
if (delta <= win /* 30분 */ && delta < bestDelta) { ... }       // ③ 이름 속 시각이 통화 시각 ±30분
...
val uriStr = bestUri ?: return SummarizeResult.NO_FILE          // 하나라도 어긋나면 "못 찾았어요"
```

`CallAudioSummarizer.summarizeAndSave` (recording/CallAudioSummarizer.kt:41) 도 같은 빗장:

```kotlin
val parsed = AdotFilenameParser.parse(fileName) ?: return false  // 서버에 보내보지도 않고 포기
val phone = parsed.phoneNumber
val recordedAt = parsed.recordedAt
```

**깨지는 실제 경우 (전부 파일은 폰에 멀쩡히 있음):**

| 경우 | 파일 이름 예 | 왜 못 찾나 |
|---|---|---|
| 삼성 통화녹음 + **연락처에 저장된 사람** | `통화 녹음 홍길동_260716_190911.m4a` | 이름 자리에 번호가 없음 → `parse` = null → **N개 집계에도 안 들어감** |
| 안심번호/대표번호로 걸려온 통화 | `05071234567_20260716190911.m4a` | 통화기록의 번호와 끝 8자리가 다름 |
| 녹음앱이 시각을 다르게 적음 / 이름 형식이 다름 | `Recording_001.m4a`, `20260716_홍길동.m4a` | ①③ 불일치 |
| 사장님이 다른 폰·PC 에서 받아온 파일 | 아무 이름 | 위 전부 |

→ **파일 이름이 앱의 유일한 눈**이라, 이름이 규칙에서 벗어나면 사람 눈엔 보여도 앱은 장님.
→ 그래서 사장님 요청(**수동 연결**)이 정확한 해법. 자동 매칭의 대체가 아니라 **최후의 수단(fallback)** 으로 둔다.

## 3. 서버(맥미니) 변경 없음 — 근거

서버는 이미 **번호와 시각을 앱에게서 폼 필드로 받아씁니다.** 파일 이름은 첨부 이름일 뿐, 서버는 파싱하지 않음:

- `ai/CallAudioSummaryRepository.kt:63-67` — `file`, **`phone`**, **`started_at_ms`**, `direction`, `duration_sec` 를 multipart 로 전송.
- `server/main.py:12776-12802` (`/api/call-audio-summary`) — `phone` / `started_at_ms` 를 Form 으로 받음. 파일명 미사용.
- 베타 화이트리스트 게이트: `server/main.py:12802` `_ensure_and_touch_beta_whitelist(owner_phone)` = **막지 않고 자동 등록**
  (2026-06-20 fix 이후). → **"새 폰이라 서버가 막는다"는 아님.**

즉 앱이 "이 파일 = 이 번호 · 이 시각" 이라고 말해주기만 하면 서버는 그대로 처리한다. **수동 연결에 서버 API 변경/배포 불필요.**

## 4. 만들 것 (앱)

### 4.1 진입점 — 통화카드 "녹음 직접 고르기"

- 위치: 채팅 통화카드에서 [통화 요약하기] 를 눌러 **"못 찾았어요"(NO_FILE) 가 난 그 자리**.
- ⚠️ **프로토(design-preview/ringgo-redesign.html)에 없는 UI** → CLAUDE.md §0 에 따라 **문구·위치는 사장님 확인 필요** (§7).

### 4.2 흐름

1. [녹음 직접 고르기] 탭 → `ActivityResultContracts.OpenDocument(arrayOf("audio/*"))`
   (※ `OpenDocumentTree`(폴더) 아님 — **파일 하나**를 고르는 것. 「내 파일」에서 사장님이 눈으로 찾은 그 파일.)
2. 결과 uri → `takePersistableUriPermission(READ)` — 앱 재시작 후에도 재생 가능해야 함.
3. **번호·시각은 파일 이름에서 뽑지 않고, 탭한 통화카드 것을 그대로 쓴다** (이게 이 기능의 핵심):
   - `phone` = `record.phoneNumber`
   - `recordedAt` = `record.startedAt ?: record.endedAt`
   - `callRecordId` = `record.id`
4. `RecordingAttachment` 저장 — `sourceType = MANUAL_PICK`, `callRecordId` 직접 연결
   (→ 통화카드 재생 플레이어가 즉시 뜸: `ChatScreen.kt:245-250` 이 `callRecordId` 연결을 1순위로 봄).
5. `CallAudioSummarizer.summarizeAndSave(..., phoneOverride, recordedAtOverride)` 로 서버 요약 → 저장 → 그 통화에 연결.

### 4.3 코드 변경 지점

| 파일 | 변경 |
|---|---|
| `recording/CallAudioSummarizer.kt:41` | `phoneOverride: String? = null, recordedAtOverride: Long? = null` 파라미터 추가. 있으면 `parse` 실패해도 진행 (`parse` 는 override 없을 때만 필수). |
| `recording/AdotFolderScanner.kt` | `summarizeCallNow` 은 그대로. 수동 경로는 스캐너를 타지 않음(폴더/MediaStore 무관) → **폴더 연결 안 해도 수동 연결은 되어야 함**(새 폰에서 즉효). |
| `presentation/screen/chat/ChatViewModel.kt:226` | `summarizeCallWithPickedFile(record, uri, context)` 신설. `Dispatchers.IO` 필수(파일 읽기 = 메인 스레드면 ANR — 2026-06-18 전례). |
| `presentation/screen/chat/ChatScreen.kt:805` | `CallSegment` 에 `onPickRecording` 콜백 + 런처. |
| `data/repository/RecordingRepository.kt` | 기존 `add(...)` 재사용 (`MANUAL_PICK` 은 이미 있는 enum). |

### 4.4 가드 (경우의 수 — feedback: 사장님을 버그 리포터로 만들지 마라)

- **재과금**: 고르기 전에 `callSummaryRepository.findExistingNear(phone, recordedAt)` → 있으면 "이미 요약돼 있어요"(기존 동작과 동일).
- **중복 저장**: `recordingRepository.existsByUri(uri)` 체크.
- **오디오 아닌 파일**: 서버가 422("받아쓰기 결과 비어있음") 반환 → "이 파일은 통화 녹음이 아닌 것 같아요" 로 안내.
- **큰 파일**: 서버 50MB 제한(`server/main.py:12829`, HTTP 413) → 안내 문구 분리.
- **엉뚱한 파일을 고름**: 사장님이 A 통화에 B 파일을 붙일 수 있음 = **의도된 자유**(수동이므로). 되돌리기 = 요약 수정(✏️) 으로 이미 가능.
- **권한**: SAF 파일 선택은 오디오 런타임 권한 불필요 → **권한 거부 상태에서도 동작해야 함**(새 폰 초기 상태의 구제책).

### 4.5 검증 (실기, adb 로 자동화 불가 — 실통화·실파일 필요)

1. 이름에 번호 없는 파일(`통화 녹음 홍길동_260716_190911.m4a`) → 수동 연결 → 요약 뜸 + 재생됨.
2. 앱 종료 후 재진입 → 재생 여전히 됨(persistable permission).
3. 이미 요약된 통화 → "이미 요약돼 있어요".
4. 녹음 폴더/권한 연결 **안 한 새 폰** → 수동 연결만으로 요약 성공. ← **이번 현장 케이스**
5. 같은 번호 통화 2건(예: 5:49 / 0:11) → 고른 파일이 **탭한 카드에만** 붙음(2026-06-18 뒤바뀜 전례).

## 5. 같이 잡을 별개 버그 2건 (이번 조사 중 발견, 사장님 폰 증상과는 별개)

- `AdotFolderScanner.kt:149` — **폴더 직접 연결** 시 "녹음 N개 발견"의 N 이 **폴더 안 파일 전부**를 셈
  (오디오인지·이름 해석되는지 안 봄) → "찾았다면서 못 찾는다"는 **거짓 숫자**. 자동 찾기(MediaStore) 쪽은 정상.
- `AdotFolderScanner.kt:169-182` — 폴더를 한 번이라도 연결하면 `listCandidates` 가 **영원히 그 폴더만** 봄.
  이후 "자동으로 찾기"를 켜도 MediaStore 를 안 봄 → 그 폴더가 비었거나 틀리면 영구 NO_FILE.

## 6. 맥미니(cowork) 협조 — 필요할 때만

앱 작업이라 서버 할 일은 없으나, 현장에서 **수동 연결까지 했는데도 실패**하면 그때만:

- `/api/call-audio-summary` 최근 로그에서 해당 owner_phone 요청의 **HTTP 코드**를 확인해 주세요.
  - 502 = Anthropic 크레딧 0 (사장님 충전 필요) / STT 실패
  - 422 = 받아쓰기 결과 비어있음(무음·인식불가 파일)
  - 413 = 50MB 초과
  - 200 인데 앱에 안 뜸 = 앱 문제(내가 봄)
- 판단 근거: 앱 로그는 `adb logcat -s CallAudioSum` 의 HTTP 코드가 진실. 토스트 문구는 믿지 말 것(전례: 2026-06-20).

## 7. 사장님 확인 필요 (CLAUDE.md §0 — 프로토에 없는 것은 지어내지 않는다)

1. **버튼 문구/위치** — 프로토에 없음. 안(案): "못 찾았어요" 안내 아래 작은 링크 **"녹음 파일 직접 고르기"**.
2. 자동으로 못 찾았을 때 **바로 파일 선택창을 띄울지**, 링크를 눌러야 뜰지.
3. 이 진입점을 **항상 노출**할지, **NO_FILE 난 뒤에만** 노출할지 (추천: 후자 — 평소엔 자동이 정답).

## 8. SYNC 워크플로우

- 시작 전: `git pull --rebase` + `tail -100 docs/SYNC.md`
- 끝난 후: `docs/SYNC.md` 블록 append + commit + push.

— android (데스크탑 Claude Code)
