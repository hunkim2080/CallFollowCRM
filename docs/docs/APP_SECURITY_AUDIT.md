# RING-GO (시공막내) 안드로이드 앱 — 보안 감사 리포트

- 감사 주체: cowork(server) · **읽기 전용**(코드 수정 없음). 실제 수정은 안드로이드 담당.
- 기준 코드: `origin/main` (감사 시점 HEAD `0341ca3` 계열, `app/` 260 파일 / ~58K LOC).
- 날짜: 2026-08-17.
- 표기: 🔴 높음(우선) · 🟡 중간 · 🟢 양호/현행 안전.

> ⚠️ 이건 **핸드오프**입니다. cowork 는 `app/` 를 수정하지 않습니다. 아래 항목은 안드로이드가 우선순위대로 판단·반영하세요.

---

## 0. 요약 (한눈에)

**이미 잘 돼 있는 것 (칭찬)**
- 🟢 세션 토큰: `SessionTokenStore` 가 **EncryptedSharedPreferences(MasterKey)** 로 암호화 저장(+keystore 실패 시 평문 폴백). `SessionAuthInterceptor` 가 `api.si0in.kr` 요청에 `Authorization: Bearer` 부착 + 401 시 재로그인. 토큰을 로그로 찍지 않음.
- 🟢 `allowBackup="false"` (민감데이터 백업 유출 차단).
- 🟢 `networkSecurityConfig` 로 cleartext 를 **Tailnet IP(100.86.114.49) 한 곳**으로만 제한. 고객 통화·PII 는 `https://api.si0in.kr` 로 감(평문 IP 로 안 감).
- 🟢 대부분 receiver/service `exported=false` 또는 시스템 권한(BROADCAST_SMS/WAP_PUSH/SEND_RESPOND_VIA_MESSAGE) 게이트. FileProvider `exported=false`.
- 🟢 통화요약 업로드는 단발 호출(무한 재시도 storm 없음). 서버측에서 재시도 중복도 이미 막음(락).

**손볼 것 (우선순위)**
1. 🔴 **FCM data 메시지 무검증** — 스푸핑 알림/서버동기화 유발 가능 + 잠금화면 PII.
2. 🔴 **인증서 피닝 없음** — 기기 신뢰 CA MITM 에 통화·고객정보 노출 여지.
3. 🟡 **앱 잠금(생체/PIN) 없음** — 폰만 열리면 모든 고객정보 접근(알림 탭 통과 포함).
4. 🟡 **알림 lock-screen 노출** — `VISIBILITY_PRIVATE` 미설정, 전화번호·통화·댓글 내용이 잠금화면에.
5. 🟡 **민감화면 `FLAG_SECURE` 없음** — 통화요약·고객정보·키 화면 스크린샷/최근앱 미리보기 노출.
6. 🟡 **릴리스 난독화 off** (`isMinifyEnabled=false`) — 역공학 쉬움 + APK 큼.
7. 🟡 **외부 공유(SEND) 텍스트 무확인 저장** — 아무 앱이 가짜 통화요약 주입 가능.
8. 🟡 **동기화 동시성** — 같은 통화 중복 업로드 레이스(로컬 dedup 이 시간창 기반·TOCTOU).

---

## 1. 🔴 FCM 푸시 무검증 — `service/RingGoFcmService.kt`

`onMessageReceived` 가 `data["type"]`(collab_invite/collab_event/intake_submitted 등)만 보고 알림 표시·일부 서버동기화까지 함. **페이로드 서명/nonce/세션 검증이 없음.**
- 위험: FCM sender 로 발행 가능한 주체나 잘못 향한 topic 이 가짜 알림을 띄우거나(사회공학), 동기화 트리거를 유발.
- 권장: 서버가 data 에 **서명 토큰(짧은 만료)** 을 실어 보내고, 앱이 그걸 검증한 뒤에만 동작. (서버는 이미 `session_token`/HMAC 인프라 있음 — 재사용 가능.)

## 2. 🔴 인증서 피닝 없음 — `ai/*Repository.kt` 전반

`CertificatePinner`/`sslSocketFactory`/`hostnameVerifier` 검색 0건. 모든 리포지토리가 맨 `OkHttpClient.Builder()`.
- 위험: 기기에 악성 CA 가 깔리면(사내 MDM·피싱) `api.si0in.kr` 트래픽(통화 오디오·전사·고객 PII) MITM.
- 권장: 공용 OkHttp 클라이언트에 `api.si0in.kr` **CertificatePinner** 추가(백업 핀 포함, 만료 대비 로테이션 계획).

## 3. 🟡 앱 잠금 부재 — 전역

`BiometricPrompt`/`BiometricManager` 미사용(있는 `KeyguardManager` 는 `IncomingCallOverlay` 의 잠금화면 위 표시용이지 앱락 아님).
- 위험: 폰이 열려 있으면(또는 알림 탭으로) 재인증 없이 전 고객 상세·통화요약 접근.
- 권장: 실행/민감 딥링크 시 **선택형 생체·PIN 잠금**(설정 토글). 최소한 알림 탭→민감화면 진입 시.

## 4. 🟡 알림 잠금화면 PII — `service/NotificationHelper.kt`, `RingGoFcmService.kt`

`setVisibility(VISIBILITY_PRIVATE)`/`VISIBILITY_SECRET` 검색 0건. 알림 본문에 전화번호·통화/댓글 내용(`author_name`, `body.take(60)`)이 그대로.
- 위험: 잠금화면에 고객 개인정보·대화 노출.
- 권장: PII 채널에 `VISIBILITY_PRIVATE` + 잠금화면용 redacted public 버전.

## 5. 🟡 민감화면 캡처 노출 — 화면 전반

`FLAG_SECURE` 검색 0건. 통화요약·고객상세·(웹키 입력) 화면이 스크린샷·화면녹화·최근앱 썸네일에 남음.
- 권장: 통화요약·고객상세 등 민감 Activity/화면에 `window.setFlags(FLAG_SECURE)`.

## 6. 🟡 릴리스 난독화/축소 off — `app/build.gradle.kts:74`

`isMinifyEnabled = false` + `proguard-rules.pro` 사실상 비어 있음(3줄).
- 위험: 역공학으로 로직·엔드포인트 노출 쉬움, APK 비대.
- 권장: release 에 R8 켜고(`isMinifyEnabled=true`, `isShrinkResources=true`) keep 규칙 정비 + 로그 스트립(`assumenosideeffects Log`).

## 7. 🟡 외부 공유 텍스트 무확인 저장 — `MainActivity` ACTION_SEND → `AdotSummaryImporter`

외부 앱의 `ACTION_SEND text/plain` 을 파싱해 통화요약 DB 에 upsert(전화 추출로 고객 매칭). 인젝션은 아님(Room/파서)이나 **출처 확인 없이** 가짜 요약 주입 가능.
- 권장: 저장 전 확인 UI 또는 출처를 '외부(신뢰 안 됨)' 로 태깅.
- 참고: `audio/*` SEND·SAF 폴더 접근은 `contentResolver` + `takePersistableUriPermission` 로 스코프됨(🟢, path traversal 없음). App Link host(api.si0in.kr/si0in.kr)·custom scheme host 도 하드체크(🟢). 다만 `share_id` 는 형식/길이 검증 없이 서버 경로로 들어감 — 🟡 charset/length 검증 권장.

## 8. 🟡 동기화 동시성(중복 업로드) — `recording/CallAudioSummarizer.kt`, `AdotTextFolderScanner.kt`

로컬 dedup 이 `(phone, recordedAt)` **시간창 근접** + `@Volatile Boolean` 단일 플래그(check-then-set = TOCTOU)라, 공유 임포트와 폴더 스캔이 동시에 같은 통화를 통과시켜 **양쪽 업로드→중복 행** 가능.
- 권장: `(phoneNumber, recordedAt, sourceType)` **DB unique 제약** + atomic upsert, 또는 check→insert 전체를 감싸는 mutex.

## 9. 권한·컴포넌트 — Manifest

- 🟡 광범위 권한: READ_SMS/SEND_SMS/WRITE_SMS/READ_CALL_LOG/READ_PHONE_STATE/SYSTEM_ALERT_WINDOW/위치/미디어. 기능상 정당하나 **Play 정책상 SMS·CallLog 권한은 선언+심사** 필요(정식 출시 리스크). 최소권한·런타임 근거 문서 유지.
- 🟡 `SYSTEM_ALERT_WINDOW`(오버레이): 탭재킹 표면. 승인 버튼 화면엔 `filterTouchesWhenObscured` 고려.
- 🟢 exported 컴포넌트는 시스템 권한/필터로 게이트, 내부 전용은 `exported=false`.

## 10. WebView — `presentation/component/AddressSearchDialog.kt`

Daum 우편번호 임베드에 `javaScriptEnabled=true` + `addJavascriptInterface`.
- 확인 필요(🟡): 로드 URL 이 **신뢰 도메인(다음 postcode)로 고정**되고 외부 네비게이션 차단(`shouldOverrideUrlLoading`)인지, JS 인터페이스가 최소 표면(주소 콜백만)인지. 임의 URL 로드가 가능하면 인터페이스 통해 앱 접근 위험.

---

## 우선 처리 권장 (안드로이드)
1) 🔴 FCM data 서명 검증  2) 🔴 인증서 피닝  3) 🟡 알림 `VISIBILITY_PRIVATE`  4) 🟡 민감화면 `FLAG_SECURE`  5) 🟡 release R8 on + 로그 스트립.
나머지(앱락·중복 dedup·WebView 확인·SEND 출처)는 2차.

_읽기 전용 감사이며 코드 변경 없음. 반영은 안드로이드가 판단._
