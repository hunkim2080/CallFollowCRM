# 온보딩 재설계 — 원클릭 연결 마법사 (2026-07-28 사장님 승인)

> 배경: 어르신 사용자 "왜 이렇게 복잡하냐, 설정이 왜 이렇게 많냐". 실제 복잡함의 상당수는 외부(에이닷·빅스비·삼성)인데 전부 "이 앱이 복잡"으로 느낌. 목표 = 우리 몫은 원클릭, 외부 몫은 손잡고 안내, 우리가 복잡함을 더 얹지 않기.
> 승인 목업: scratchpad/onboarding_flow.html + .png (7프레임 filmstrip).
> 방향 확정: **온보딩 안에 필수 단계로** 넣되, **모든 단계에 "나중에 할게요" 탈출구** 둠(막히면 그만두는 것 방지 + 홈에서 재권유). 녹음 0개면 삼성 통화녹음 켜기 안내.

## 확정 흐름 (가입·프로필 뒤 → 연결 마법사 → 홈)
기존: LOGIN → CONSENT → ONBOARDING(프로필 4단계) → PERMISSIONS(문자앱+권한) → HOME
신규 연결 마법사(프로필 뒤):
1. **① 시작 안내** — "거의 다 됐어요! 딱 4가지만 연결할게요 · 1분". 1·2·3·4 목록 미리보기. [시작하기]
2. **② 기본 문자 앱** — "문자를 여기서 받을게요". [기본 문자 앱으로 설정](ROLE_SMS 다이얼로그). 힌트 "확인 창 뜨면 '예'만". [나중에]
3. **③ 권한** — "통화·문자를 자동으로 정리하려면". 칩(전화상태·통화기록·문자·알림·연락처). [허용하기](runtime batch). [수동 모드로 시작]
4. **④ 통화 녹음 연결** — "통화 끝나면 자동 요약. 폴더 고를 필요 없어요". [자동으로 찾기](오디오권한 1회 → AdotFolderScanner.enableMediaStore) → "녹음 N개 찾았어요 🎉". [나중에]
   - **④' 녹음 0개** → "삼성 통화녹음을 먼저 켜주세요" 그림 + [통화 녹음 설정 열기](딥링크) + [다시 찾기] / [나중에]. (에이닷·삼성 어디든 찾지만, 하나도 없으면 = 녹음이 안 켜진 것)
5. **⑤ 가격표·답장 자동 만들기** — "보낸 문자에서 가격표·자주 쓰는 답장 자동 생성"(보낸 문자만). [자동으로 만들기](→ 가격표 PricingExtract + 템플릿 TemplateDiscover). [나중에 할게요]
6. **⑥ 완료** — "이제 준비 끝!" 초록 체크 recap(문자앱·권한·녹음N·가격표·답장). [시작하기] → hasOnboarded/setup 완료.

## 원칙
- 한 화면 = 한 가지 + 큰 버튼 하나 + 큰 글씨 + 쉬운 말 + 진행 점(N/4).
- 모든 단계 "나중에" 탈출구. 건너뛴 항목은 홈 "시작 준비" 체크리스트(SetupCheckCard)에서 다시 권유(실시간 감지).
- 베타 가입 단계 **없음**(코드상 fail-open 관문 아님. 로그인=번호만).

## 구현 지점 (조사 결과 기반)
- 게이팅: `presentation/AppRoot.kt:63-75` startDestination. 새 pref `setupWizardDone` 추가 → PERMISSIONS 뒤 SETUP 라우트.
- 기존 프로필: `onboarding/OnboardingScreen.kt`(Story/Trade/Profile/Born). **Born "가격표·답변 템플릿이 자동으로 만들어졌어요"는 거짓 → 문구 교체 or 실제 생성 반영**(`:483`).
- 권한: `onboarding/OnboardingPermissionScreen.kt`(ROLE_SMS + runtime). 문구 ②③에 맞춰 손질.
- 녹음: `recording/AdotFolderScanner.kt`(enableMediaStore `:71`, isConnected `:146`, MediaStore 자동 찾기, 폴더 안 고름). 오디오권한 `READ_MEDIA_AUDIO`.
- 가격표: `PricingExtractViewModel`/`PricingExtractScreen`(consent → scan → /extract-pricing → review). 진입 `PricingItemsScreen.kt:155`.
- 템플릿: `TemplateDiscoverViewModel`/`TemplateDiscoverScreen`. 진입 `TemplateListScreen.kt:121`.
- 홈 체크리스트: `SettingsScreen.kt:3098 SetupCheckCard`(2개→확장, done 감지자 존재: pricing count, template count, AdotFolderScanner.isConnected).

## 진행 상태
- [x] 목업 승인(2026-07-28)
- [x] **v1 구현·검증 완료**: OnboardingSetupScreen(안내→통화녹음 연결+0개 안내→완료) + nav(PERMISSIONS→SETUP→HOME) + Born 거짓문구 교체. S9 실기 전 흐름 검증(녹음 519개 실스캔, 완료→HOME). 임시 startDestination 강제로 캡처 후 원복.
  - ⚠️ enableMediaStore 는 실기 검증 중 실제 호출됨(사장님 폰 MediaStore 자동스캔 ON + connectedAt=now, 과거 519개 재요약 안 함=cutoff). 기능상 정상/이득.
- [ ] **v2 (후속)**: ⑤ 가격표·답장 단계를 마법사 안에서 원클릭(현재는 완료화면서 "홈에서" 안내 + 기존 빈화면 버튼 존치). 홈 SetupCheckCard 를 녹음·가격표·템플릿까지 확장(건너뛴 항목 재권유).
- [ ] 사이트/Play 배포는 사장님 요청 시
