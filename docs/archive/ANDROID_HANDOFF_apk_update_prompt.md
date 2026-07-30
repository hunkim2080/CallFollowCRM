# 안드로이드 핸드오프 — 앱 업데이트 알림 (cowork → android, 2026-06-18)

## 배경 (사장님 원문)

> "링고앱에서 새로고침을 했을때 새로운 파일이 업데이트 됐으면 업데이트하라고 알려줬으면 좋겠는데"

= 베타 테스터들이 새 APK 가 서버에 올라간 줄 모르고 옛 버전 그대로 쓰는 문제.
앱 안에서 "새 버전 있어요. 다운로드 하시겠어요?" 알림.

---

## 서버 측 — 이미 준비됨 (변경 X)

### `GET /api/download/version`

응답:
```json
{
  "available": true,
  "size_bytes": 21286768,
  "size_mb": 20.3,
  "mtime_ms": 1781787835712,
  "mtime_iso": "2026-06-18 22:03",
  "version": "v0.2-beta"
}
```

비교 기준 = `mtime_ms` (서버의 APK 파일 mtime). 이게 본인 빌드 시각보다 크면 = 더 새로운 APK.

---

## 앱 측 — 요청 사항

### 1. 본인 APK 의 build mtime 알기 (2가지 옵션)

**옵션 A — `PackageManager.lastUpdateTime`** ← 추천
```kotlin
val ctx = applicationContext
val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
val installedAtMs = pkgInfo.lastUpdateTime   // 설치/업데이트 시각 (epoch ms)
```
- APK 설치 시각. 사용자가 새 APK 깔면 자동 갱신.
- 별도 설정 필요 X.

**옵션 B — `BuildConfig.BUILD_TIME_MS`**
- `app/build.gradle` 에 박기:
  ```gradle
  buildConfigField "long", "BUILD_TIME_MS", "${System.currentTimeMillis()}L"
  ```
- 빌드 시각이 박혀 빌드된 APK 안에 영구 박힘.
- **단점**: 같은 APK 를 여러 번 다운받으면 같은 값 (= 사용자가 옛 빌드 받아도 옛 시각 그대로). 그치만 사장님이 빌드 → 업로드 흐름이라 큰 문제 X.

→ **A (lastUpdateTime)** 가 가장 정확.

### 2. 새로고침 시점에 비교

```kotlin
suspend fun checkApkUpdate() {
    val response = api.downloadVersion()  // /api/download/version
    if (!response.available) return
    val serverMs = response.mtime_ms
    val installedMs = packageManager
        .getPackageInfo(packageName, 0)
        .lastUpdateTime
    val GRACE_MS = 60_000L  // 1분 (시계 차이·중복 알림 방지)
    if (serverMs > installedMs + GRACE_MS) {
        showUpdateDialog(response)
    }
}
```

### 3. 어디서 호출

추천 진입점:
- **앱 시작 직후** (MainActivity onResume — 1회만, dedup)
- **사장님 직접 새로고침** (pull-to-refresh 또는 홈/마이페이지의 새로고침 버튼)
- (선택) 주기적 — 24시간에 한 번. WorkManager.

### 4. UI — 다이얼로그 (또는 토스트)

```
┌─────────────────────────────────────┐
│ 새 버전 있어요  v0.2-beta             │
│                                     │
│ 2026-06-18 22:03 빌드 · 20.3 MB     │
│                                     │
│ 다음 fix·기능이 들어있어요:           │
│ (서버 응답엔 changelog 없음 →        │
│  단순 "업데이트 사항이 있어요"로 충분) │
│                                     │
│   [ 나중에 ]   [ 지금 받기 ]         │
└─────────────────────────────────────┘
```

- **[지금 받기]** → 브라우저 인텐트로 `https://si0in.kr/install` 열기 (install 페이지가 안내 + APK 다운로드 버튼 제공).
- **[나중에]** → `SharedPreferences("apk_update_dismissed_ms", serverMs)` 저장 → 같은 mtime 으로는 다시 안 띄움.
- 다음 빌드가 올라가면 (serverMs 가 바뀜) 자동 다시 띄움.

### 5. dedup (중복 알림 방지)

`SharedPreferences` 에 저장:
- `last_seen_server_ms`: 마지막으로 본 서버 mtime
- `dismissed_until_ms`: "나중에" 누른 mtime

이미 본 mtime 이거나 dismiss 된 mtime 이면 안 띄움.

---

## 사장님 확인 시나리오

1. 사장님이 새 빌드 → WinSCP 로 Mac mini 업로드.
2. 베타 테스터가 앱 켬 → 시작 직후 자동 체크.
3. 다이얼로그 "새 버전 있어요 · 2026-06-19 빌드".
4. **[지금 받기]** → `https://si0in.kr/install` 열림 → 다운로드 → 설치.

---

## 디자인 (사장님 룰 §0)

`design-preview/ringgo-redesign.html` 에 update 다이얼로그가 있으면 그대로 옮겨주세요. 없으면 위 시안으로 사장님 한 번 확정 받고 진행.

---

## 추가 검토 — 서버 측 보조 (필요시 cowork 가 추가)

지금은 클라이언트가 비교. 만약 클라이언트 로직 복잡하면 서버가 한 줄로 알려주는 옵션:

```
GET /api/download/version?installed_at_ms=1781000000000
↓
응답에 추가:
  "is_newer": true,
  "your_installed_ms": 1781000000000
```

→ 앱은 `is_newer` 만 보고 다이얼로그. 비교 로직 서버 쪽으로.

필요하시면 cowork 가 한 줄로 추가 가능. **지금은 클라이언트 비교만으로도 충분** (mtime 한 번 비교).

---

## SYNC 워크플로우

- 시작 전: `git pull --rebase` + `tail -100 docs/SYNC.md`
- 끝난 후: SYNC.md 에 블록 append + commit + push.

— cowork (Mac mini)
