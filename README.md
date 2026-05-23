# Call Follow CRM (상담후속 CRM)

개인 업무폰 전용 Android 통화 후속관리 CRM 앱입니다.
줄눈 시공 상담 업무에서 통화 직후 후속 문자를 빠르게 보내고 고객 상태와 메모를 저장하는 것이 목표입니다.

## 핵심 정책

- **SMS 자동 발송하지 않습니다.** `ACTION_SENDTO` + `smsto:` URI 로 기본 문자앱 작성 화면만 엽니다.
- `SEND_SMS` 권한을 요청하지 않습니다. Manifest 에 포함되지 않습니다.
- 통화 녹음을 직접 구현하지 않습니다.
- 에이닷 등 다른 앱의 내부 저장소를 자동 스캔하지 않습니다.
- AI 요약은 v1 에서 호출하지 않습니다. 인터페이스와 데이터 구조만 준비합니다.

## 스택

- Kotlin 1.9.22
- Jetpack Compose (BOM 2024.02.00) + Material 3
- Room 2.6.1 + KSP
- Navigation Compose 2.7.7
- MVVM, manual DI (Hilt 사용 안 함)
- minSdk 26, targetSdk 34

## 프로젝트 구조

```
app/src/main/java/com/detailline/callfollowcrm/
  CallFollowCrmApplication.kt        Application + AppContainer 초기화 + 템플릿 seed
  MainActivity.kt                    deep link / share intent 라우팅
  data/
    AppContainer.kt                  manual DI container
    local/
      AppDatabase.kt
      dao/                           6개 DAO
      entity/                        6개 Entity
      seed/DefaultTemplates.kt
    repository/                      Customer/CallRecord/MessageTemplate/MessageHistory/Recording/CallSummary
  domain/model/Statuses.kt           모든 상태 enum
  presentation/
    AppRoot.kt
    navigation/                      AppNavHost, Destinations, NavEvents
    theme/AppTheme.kt
    util/ViewModelFactory.kt
    screen/
      onboarding/OnboardingPermissionScreen.kt
      home/HomeScreen.kt + HomeViewModel.kt
      followup/FollowUpScreen.kt + FollowUpViewModel.kt
      customer/CustomerDetailScreen.kt + CustomerDetailViewModel.kt
      template/TemplateListScreen.kt + TemplateEditScreen.kt + ViewModels
      settings/SettingsScreen.kt + SettingsViewModel.kt
  service/
    CallStateReceiver.kt             통화 종료 감지 (Phase 2)
    NotificationHelper.kt            알림 채널 + 알림 표시
  util/
    PermissionHelper.kt
    SmsIntentHelper.kt               ACTION_SENDTO 전용
    CallLogHelper.kt
    DateTimeUtils.kt
  recording/
    RecordingShareHandler.kt         share intent / 수동 파일 픽
    ServerUploadRepository.kt        Phase 4 인터페이스 (NoOp)
  ai/
    AiSummaryModels.kt
    AiSummaryRepository.kt           Phase 4 인터페이스 (NoOp)
```

## 빌드 방법

### Android Studio 사용 (권장)

1. Android Studio Hedgehog (2023.1) 이상에서 프로젝트 열기.
2. Gradle Sync 진행. Gradle 8.2+, JDK 17 필요.
3. Run → app.
4. 실기기 또는 API 26+ 에뮬레이터에 설치.

### CLI 빌드 (선택)

먼저 Gradle Wrapper 가 필요합니다 (저장소에 포함되어 있지 않음):

```bash
# 처음 한 번
gradle wrapper --gradle-version 8.2
# 디버그 APK
./gradlew :app:assembleDebug
# APK 위치
ls app/build/outputs/apk/debug/app-debug.apk
```

### APK 설치 (개인 폰)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 테스트 방법

### Phase 1: 수동 흐름 (권한 없이도 동작)

1. 앱 실행 → 권한 안내 화면에서 **"권한 없이 수동 모드로 시작"** 선택.
2. Home 화면 우측 하단 **수동 입력** FAB 클릭.
3. 전화번호 입력, 템플릿 선택 (예: "사진 요청"), 상태/메모 입력.
4. **문자 보내기** 클릭 → 기본 문자앱이 열리는지 확인 (전송 버튼은 직접 누르지 않음).
5. 뒤로 가서 Home 의 오늘 처리완료 카운트 증가 확인.

### Phase 2: 통화 종료 감지

1. Onboarding 에서 **권한 요청하고 시작** 클릭.
2. 전화 / 통화 기록 / (Android 13+) 알림 권한 허용.
3. 다른 사람에게 전화 → 종료.
4. 약 1~2초 후 알림이 뜨는지 확인.
5. 알림 클릭 → FollowUpScreen 으로 진입하면서 번호가 자동 채워지는지 확인.

### Phase 3: 녹음 파일 공유받기 / 수동 선택

1. 파일관리자 또는 에이닷에서 audio 파일을 [공유] → **상담후속 CRM** 선택.
2. 앱이 열리고 RecordingAttachment 가 저장됨 (현재는 unlinked).
3. CustomerDetail → **녹음 파일 → 파일 선택해서 첨부** 로 수동 선택도 동일하게 동작 확인.

### Phase 4: AI 요약 (Skeleton)

- CustomerDetail 의 **"AI 요약하기 (준비 중)"** 버튼은 비활성 상태.
- 실제 API 호출은 v1 에서 수행하지 않음.

## 현재 구현 범위

| 범위 | 상태 |
|---|---|
| Room DB 6개 엔티티 | 구현 |
| 기본 템플릿 5개 seed | 구현 |
| Repository 6개 | 구현 |
| 7개 화면 (Onboarding / Home / FollowUp / Customer / TemplateList / TemplateEdit / Settings) | 구현 |
| ACTION_SENDTO 문자앱 열기 | 구현 |
| MessageHistory `DRAFT_OPENED` 저장 | 구현 |
| 권한 onboarding + 거부 시 수동 모드 fallback | 구현 |
| 통화 종료 BroadcastReceiver + CallLog 조회 | 구현 |
| 알림 채널 + 알림 + deep link | 구현 |
| 공유 intent 로 audio 파일 수신 | 구현 |
| 수동 audio 파일 픽업 | 구현 |
| AiSummaryRepository / ServerUploadRepository 인터페이스 | 구현 (NoOp) |
| Customer 같은 번호 재사용 + 메모 누적 | 구현 |
| Settings 영속화 | **In-memory만** (TODO: DataStore) |

## 미구현 / TODO

- 실제 AI / STT / 서버 업로드 (Phase 4 본격 구현)
- `AUTO_DETECTED` 녹음 자동 탐지 (의도적으로 enum 만)
- CSV 백업 / 내보내기
- 데이터 마이그레이션 (현재는 `fallbackToDestructiveMigration`)
- Gradle wrapper 파일 (`gradlew`, `gradlew.bat`, `gradle/wrapper/...`) - 최초 1회 `gradle wrapper` 실행 필요
- 앱 아이콘 (현재는 시스템 drawable 사용)
- 단위 테스트 / Compose UI 테스트
- 다국어 strings (현재 한국어만)

## 알려진 Android 제약사항

### 통화 종료 감지

- **Android 10+** 부터 `EXTRA_INCOMING_NUMBER` 는 일반 앱에 차단됨. 본 앱은 항상 `CallLog` 만 사용.
- **Android 11+** 패키지 가시성 정책으로 일부 OEM 에서 `PHONE_STATE` 브로드캐스트가 늦거나 누락될 수 있음.
- **Xiaomi / Oppo / Vivo** 등 일부 OEM: 자동시작 허용 / 배터리 최적화 제외 설정이 꺼져 있으면 정적 receiver 가 호출되지 않음.
- CallLog row 가 통화 종료 직후 즉시 작성되지 않을 수 있어 `CallStateReceiver` 에서 1.5초 정도 지연 후 조회한다.

### 권한 거부 fallback

- 모든 권한이 거부되어도 앱은 정상 실행. Home → 수동 입력으로 사용 가능.
- 알림 권한 (Android 13+) 거부 시 `NotificationManagerCompat.notify` 가 `SecurityException` 을 던질 수 있어 try-catch 로 무시.

### 문자앱 호출

- 기본 문자앱이 설치되지 않은 기기에서는 `ActivityNotFoundException` 발생 → 사용자에게 토스트로 안내.
- `smsto:` URI 와 `sms_body` extra 는 표준이지만 일부 서드파티 SMS 앱은 body prefill 을 무시할 수 있음.

## 추후 AI 서버 연동 방법 요약

올바른 구조:

```
Android 앱
  → 사용자 소유 서버 (REST/gRPC)
    → STT API (예: Whisper / Google Speech)
    → 요약 API (예: Claude / GPT)
    → 서버 DB
  ← 구조화된 JSON 반환
→ Android 앱이 CallSummary 저장
```

추가 단계:

1. `ServerUploadRepository` 의 실제 구현을 `Retrofit` + OkHttp 로 작성.
2. `RecordingUploadRequest` 로 파일 URI → multipart 업로드.
3. 서버에서 STT + 요약 후 `AiSummaryResult` JSON 반환.
4. `AiSummaryRepository.requestSummary()` 의 실제 구현이 결과를 받아 `CallSummaryEntity` 로 저장.
5. CustomerDetail 의 **AI 요약하기** 버튼을 활성화하고 호출.

**절대 하지 말 것**: 앱에 OpenAI/Anthropic API 키 직접 보관, 클라이언트에서 직접 외부 LLM API 호출.

## 다음 권장 리팩토링

1. Settings 를 `Preferences DataStore` 로 영속화.
2. `viewModelFactory` 대신 `AndroidViewModelFactory.Companion.from(...)` 또는 Hilt 도입.
3. `CallStateReceiver` 에서 `TelephonyCallback` (Android 12+) 분기 추가.
4. 알림 액션 (사진 요청 / 견적 안내 / 예약 안내) 을 각각 다른 PendingIntent 로 분리해서 템플릿 자동 선택까지 연결.
5. Room 의 `fallbackToDestructiveMigration` 제거하고 Migration 작성.
6. 단위 테스트: Repository (Room in-memory) + ViewModel (TestDispatcher).

## 라이선스

개인 업무용. 사내/개인 사용만 의도됨.
