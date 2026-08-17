# 시공막내 프로덕션 준비도 감사 (2026-07-19)

> Play 스토어 출시 직후 "표준/기본인데 안 해둔 것"을 점검. 감사: **Fable 5**, 근거는 repo 실측.
> 종합 등급: **별 2.5 / 5** → 아래 "지금 당장 3개" 처리 시 3.5. 최대 약점은 코드 품질이 아니라
> **"터졌을 때 신호도 복구수단도 없음"**.

상태 표기: ✅ 완료 · ⬜ 대기 · 🔵 사장님 액션 · 🟢 관찰만

---

## 이번 세션(0.2.1064)에 처리한 것

### ✅ ③(자폭 장치) — 데이터 통삭제 지뢰 제거  🔴
- **문제**: `AppDatabase.kt` 가 `.fallbackToDestructiveMigration()` → **어떤 migration 이든 실패하면 고객·정산 DB 전체를 조용히 삭제**하고 새로 시작. 주석엔 "개발 단계"인데 출시본까지 딸려옴. DB 는 이미 v41, 과거 "index 불일치" 크래시 이력 있음 = 실제로 밟힐 뻔한 지뢰.
- **수정**: `.fallbackToDestructiveMigrationFrom(1, 2)` — v1·v2(migration 경로 자체가 없는 초기 버전)에서 올라올 때만 예외 삭제 허용. **그 외(v3~ 및 미래 41→42 등의 실수)는 삭제 대신 크래시**로 멈춤 → 우리가 보고받고 고쳐 재배포(복구 가능). commit: (아래)
- 근거: [AppDatabase.kt:791](../app/src/main/java/com/detailline/callfollowcrm/data/local/AppDatabase.kt#L791)

### ✅ ②(블랙박스) — Crashlytics 크래시 자동 수집  🔴
- **문제**: 지인 폰에서 앱이 죽어도 우리가 알 방법이 없었음(카톡 신고 or Play vitals 뿐). 이 앱은 삼성 특정 기종 크래시 이력이 잦음.
- **수정**: Firebase Crashlytics 추가 (플러그인 `com.google.firebase.crashlytics` 3.0.2 + `firebase-crashlytics-ktx`). 자동 초기화라 코드 배선 불필요. minify OFF 라 mapping 업로드 없음.
- 🔵 **사장님 1회 액션**: Firebase 콘솔(ringgo-2844c) > Crashlytics 탭 진입해 활성화 확인. (첫 크래시 보고 시 자동 활성화되지만 탭이 보이는지 확인 권장)
- 근거: [app/build.gradle.kts](../app/build.gradle.kts) plugins + deps

---

## 🔵 지금 당장 (사장님 액션)

### ① keystore 백업 — 오늘, 10분  🔴
- **왜**: 앱 업데이트에 필요한 **서명 열쇠가 데스크탑 1대에만** 존재. 그 컴퓨터가 죽으면 **기존 사용자에게 업데이트 영구 불가** → 재설치 유도 시 데이터도 소실(자폭장치와 연쇄됐던 리스크).
- **할 일**: 아래 3개 파일을 클라우드 2곳(예: 구글드라이브 + USB)에 복사.
  - `keystore.properties`, `ringgo-release.jks`(경로는 keystore.properties 안 storeFile), `version.properties` (모두 git 제외라 유일본)
- 그리고 Play 콘솔에서 **App Signing(앱 서명) 등록 여부 확인** — 등록돼 있으면 구글이 열쇠를 대신 보관(⑩·④ 연결).

---

## ✅ 이미 되어 있던 것 (Fable 이 놓친 것 — 확인함)

### ✅ ④ 업데이트 배너 vs Play 충돌 — 이미 방어됨
- Fable 은 "Play 설치자에게도 사이드로드 APK 배너가 뜬다"고 우려했으나, **2026-07-18 에 이미 막아둠**: `HomeViewModel.isInstalledFromPlayStore()` 로 Play(`com.android.vending`) 설치면 배너·시트 안 띄움.
- 근거: [HomeViewModel.kt:1376](../app/src/main/java/com/detailline/callfollowcrm/presentation/screen/home/HomeViewModel.kt#L1376)

---

## ⬜ 남은 항목 (곧 / 우선순위 순)

| # | 항목 | 심각도 | 품 | 메모 |
|---|---|---|---|---|
| ⑤ | **compileSdk 34→35 정렬** | 🟡 | 작음~중 | targetSdk 35 인데 compileSdk 34(비표준). S23U 키보드 인셋 버그 해결 도구가 여기 있음. AGP 요구사항 확인 |
| ⑥ | **R8/난독화 켜기** | 🟡 | 중 | Play 권장조치. **반드시 ②Crashlytics 안착 후**(R8 켜면 출시본에서만 터지는 버그 가능→블랙박스로 추적). keep 룰 정비 + mapping.txt 를 keystore 와 함께 백업 + 전기능 실기 검증 |
| ⑦ | **돈/정산 경로 회귀 테스트 + 최소 CI + exportSchema** | 🟡 | 작음~중 | 계약금 10억·일당 이중차감 = 전부 출시 후 발견된 돈버그. GitHub Actions 로 push마다 `testDebug+assembleDebug`. `exportSchema=true`(+ksp schemaLocation) 켜서 migration 자동 테스트 가능케. "돈 계산 코드는 유닛테스트 없이 머지 금지" 룰 |
| ⑧ | **릴리즈 로그의 고객 번호·문자 마스킹** | 🟢 | 작음 | 78개 Log 중 일부가 번호+문자본문 노출([MmsSentReceiver.kt:25](../app/src/main/java/com/detailline/callfollowcrm/service/MmsSentReceiver.kt#L25) 등). ⑥ R8 켤 때 `-assumenosideeffects` 로 릴리즈 로그 제거 |
| ① | **인앱 데이터 내보내기/가져오기** | 🟡 | 중 | allowBackup=false 의 대가(폰 교체 시 데이터 소실). 파일 1개로 백업/복구. ⑦ 과 묶어서 |
| ⑨ | 상시 폴링 3개 배터리 | 🟢 | 중 | 접수 60초 폴링 등. FCM 있으니 전환 여지. **지금은 관찰만**, 배터리 불만 신고 오면 착수 |

## ⬜ 🔵 확인 필요 (repo 밖, Play 콘솔/서버)
- ⑩-a **Play 데이터 안전 섹션** 이 실제 수집(서버로 가는 문자·통화요약)과 일치하는지
- ⑩-b **App Signing 등록 여부** (①·④ 연결)
- ⑩-c **assetlinks.json 에 Play 서명키 SHA256** 추가됐는지 — 없으면 Play 설치본에서 협업 링크가 앱으로 안 열림

---

## 🚫 이 규모(1인·소수 사용자)엔 오버킬 — 지금 안 함
UI/계측 테스트 풀스위트 · Analytics(사용량 분석) · Sentry 등 별도 관측스택 · 완전 자동배포(Fastlane) · Baseline Profile · 멀티모듈화 · allowBackup 되살리기(①의 수동 내보내기로 대체).

---

## 배포 / 다음
- 사이드로드 베타: **0.2.1064** 배포 완료(si0in.kr, version_code 1064, 24,375,599 B).
- **Play(플레이) 반영은 별도**: AAB 를 콘솔에 업로드해야 함 → 필요 시 `bundleRelease` 로 AAB 생성(사장님 요청 시).
