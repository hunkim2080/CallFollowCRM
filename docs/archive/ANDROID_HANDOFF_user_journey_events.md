# 안드로이드 핸드오프 — 사용자 여정 이벤트 발사 (cowork → android, 2026-06-21)

## 사장님 의도

> "베타테스터들의 앱 사용 여정이 궁금해. 처음 들어와서 어디부터 클릭하는지. 어떤 부분을 보려고 하는지. 어떤 부분을 캡쳐 시도하는지. 다 중요한 자산이 될 것 같은데"

= **앱 행동 로그**. Mixpanel/Amplitude 같은 거 직접 구현.

## 서버 측 — 이미 준비됨 ✅

### `POST /api/event`

**단일 이벤트**:
```json
{
  "owner_phone": "01080052080",
  "event": {
    "event_name": "screen_view",
    "screen": "home",
    "target": null,
    "extra": null,
    "timestamp_ms": 1781000000000
  }
}
```

**배치 (권장)** — 30초마다 모아 보내기, 네트워크 절약:
```json
{
  "owner_phone": "01080052080",
  "events": [
    {"event_name": "screen_view", "screen": "home", "timestamp_ms": 1781000000000},
    {"event_name": "button_click", "screen": "home", "target": "btn_reply_suggest", "timestamp_ms": 1781000003000},
    {"event_name": "screen_view", "screen": "chat", "timestamp_ms": 1781000005000}
  ]
}
```

- 한 번에 최대 100건
- owner_phone 필수, 나머지 다 옵션
- timestamp_ms 없으면 서버 now 박힘
- 미등록 phone 도 receive (graceful)
- 응답: `{"ok": true, "count": N}`

## 안드로이드 측 요청

### 1. 어떤 이벤트 보낼까

#### event_name (필수, 권장 4종)
| 값 | 언제 |
|---|---|
| `screen_view` | 화면 진입 (Composable onActive 또는 Fragment onResume) |
| `button_click` | 사장님이 누른 버튼·카드 |
| `screenshot` | 폰의 스크린샷 감지 (또는 사장님이 사진 저장 시도) |
| `feature_use` | LLM 호출 같은 핵심 기능 사용 (답장추천·통화요약·refine 등) |

#### screen (권장 값)
| 값 | 의미 |
|---|---|
| `home` | 홈 |
| `chat` | 채팅 (고객 1명 대화) |
| `customer` | 고객 카드 상세 |
| `collab` | 협업 현장 목록 |
| `collab_inbox` | 응답 안 한 협업 인박스 |
| `schedule` | 일정 캘린더 |
| `intake_form` | 접수서 발급 시트 |
| `call` | 통화 상담 |
| `team` | 팀원 화면 |
| `settings` | 설정 |
| `onboarding` | 첫 진입 |

#### target (선택, 버튼 식별자)
예시: `btn_reply_suggest`, `btn_call_summary`, `card_customer`, `tab_received_collab`, `menu_export`

#### extra (선택, 자유 JSON)
예시:
- `screenshot`: `{"target_screen": "chat", "kind": "system"}` (시스템 스크린샷 vs 앱내 캡쳐)
- `feature_use`: `{"endpoint": "prepare-reply", "duration_ms": 1230}`

### 2. 배치 발사 (권장)

매 이벤트마다 POST 하면 부하 큼 → 메모리에 버퍼링 후 30초마다 한 번 발사:

```kotlin
object EventTracker {
    private val buffer = mutableListOf<Event>()
    private val mutex = Mutex()
    private var flushJob: Job? = null

    suspend fun track(event: Event) = mutex.withLock {
        buffer.add(event)
        if (buffer.size >= 50) flush()  // 50건 차면 즉시 발사
        else scheduleFlush()
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = appScope.launch {
            delay(30_000)  // 30초
            mutex.withLock { flush() }
        }
    }

    private suspend fun flush() {
        if (buffer.isEmpty()) return
        val toSend = buffer.toList()
        buffer.clear()
        try {
            eventRepo.send(ownerPhone, toSend)
        } catch (e: Exception) {
            // 실패 시 다시 버퍼에 (그치만 무한 반복 방지 — 최대 10번)
        }
    }
}
```

### 3. 박을 곳 (권장)

- **모든 화면의 onResume / Composable LaunchedEffect**:
  ```kotlin
  LaunchedEffect(Unit) {
      EventTracker.track(Event("screen_view", "home"))
  }
  ```
- **주요 버튼 onClick**:
  ```kotlin
  Button(onClick = {
      EventTracker.track(Event("button_click", "home", "btn_reply_suggest"))
      onReplySuggest()
  })
  ```
- **스크린샷 감지** (옵션): ContentObserver 로 MediaStore 변화 감지 → `screenshot` 이벤트 + `target_screen = currentScreen`
- **LLM 호출 후**:
  ```kotlin
  EventTracker.track(Event("feature_use", currentScreen, endpoint, extra = mapOf("duration_ms" to elapsed)))
  ```

### 4. owner_phone 보내기

= 직전 cycle 의 owner_phone 옵션 패턴과 같음. AppContainer 에서 `{preferences.bizPhone}` 으로 주입.

### 5. 사용자 동의 (개인정보)

- 안드로이드 측에 "앱 사용 로그를 익명으로 수집해도 될까요" 같은 동의 받아도 좋음. 그치만 다른 SaaS 들 = 기본 ON (opt-out) 패턴.
- 사장님 결정에 따라.

## 어디서 사장님이 결과 보나

`https://api.si0in.kr/admin/user/01080052080` 페이지의 **🚶 사용자 여정** 카드에 timeline 으로 표시. 최근 50건.

예시 화면:
```
🚶 사용자 여정 12
─────────────────────
👀 홈                       2026-06-21 03:15 · screen_view
👆 홈 · btn_reply_suggest   2026-06-21 03:15 · button_click
👀 채팅                     2026-06-21 03:16 · screen_view
⚙️ 채팅 · prepare-reply     2026-06-21 03:16 · feature_use
📸 채팅                     2026-06-21 03:17 · screenshot
👀 협업현장                 2026-06-21 03:18 · screen_view
...
```

## 추후 (cowork 다음 cycle)

- funnel 분석 (앱 진입 → 답장추천 누른 사람 → 사용한 사람)
- 화면별 진입 수 / 평균 체류 시간
- 가장 많이 캡쳐된 화면 (= "사장님이 진짜 좋아하는 화면" 시그널)
- dashboard 에 "이번 주 베스트 화면" KPI

## 검증

배포 후 사장님 폰에서 `EventTracker.track` 한 번 호출 → admin/user 새로고침 → 카드에 이벤트 나옴.

`curl` 검증:
```bash
curl -s -X POST 'https://api.si0in.kr/api/event' \
  -H 'content-type: application/json' \
  -d '{"owner_phone":"01080052080","event":{"event_name":"screen_view","screen":"home"}}'
# 기대: {"ok":true,"count":1}
```

— cowork (Mac mini)
