# 구글 캘린더 연동 (본폰 미러링 대체) — 계획/진행 SoT

**2026-08-31 시작.** 사장님 지시: **기존 '본폰 미러링' 없애고 구글 캘린더 연동 도입.**

## 목적 (사장님 — 셋 다 선택)
- **위젯**: 구글 캘린더 앱/위젯으로 시공·AS 일정 한눈에
- **백업**: 폰 교체/재설치해도 구글에서 복원
- **공유**: 그 캘린더를 가족·직원과 구글에서 공유

## 결정
- **방식 = 구글 Calendar API + Google Sign-In** (로컬 CalendarContract 아님 — 백업/공유 때문).
  동생분 Flutter 앱 `jeongsan/lib/calendar_sync.dart` 설계를 Kotlin으로 이식 (참고 원본: `D:\dev\jeongsan-main\jeongsan-main`).
- **캘린더 = "시공막내" 하나**에 시공+AS 다 (동생은 라벨별 다중캘린더 — 우린 단순화).
- **프로젝트 = ringgo-2844c** (기존 Firebase, 새로 안 만듦). project_number 135651750126, package `com.detailline.callfollowcrm`.

## SHA-1 지문 (공개정보 — OAuth Android 클라이언트에 등록)
- Release (로컬 ringgo-release.jks · si0in 자체서명): `17:74:69:A1:9B:74:EB:9C:E3:7D:76:A7:B9:53:22:2E:FA:AF:03:4B`
- Debug (adb): `19:AC:07:FE:0D:BF:7E:2C:7C:E7:C0:2B:58:CA:D6:00:95:ED:05:89`
- Play 앱서명 (테스트 주경로): Play Console → 앱 무결성 → 앱 서명 키 인증서 SHA-1 (사장님이 복사).

## OAuth 클라이언트 (2026-08-31 생성)
- **웹 클라이언트 ID (= serverClientId · 앱에 심음)**: `135651750126-0moktqeb3dao996jupkvfb1c88rkpbdp.apps.googleusercontent.com`
- Android 클라이언트 ID (구글 등록용 · 앱엔 안 넣음): `135651750126-m84gae7s50spiutog8gfbmemhmemvatt.apps.googleusercontent.com` (package `com.detailline.callfollowcrm` + debug SHA-1)
- ⚠️ 웹 클라이언트 secret(`GOCSPX-…`)은 **안 씀** — 안드로이드 앱은 client secret 불필요 → 코드/깃 어디에도 저장 안 함.
- ⏳ TODO: **테스트 사용자**(본인 구글 이메일)를 대상에 등록 확인 + **Play 앱서명 SHA-1**을 Android 클라이언트에 추가(메인폰 데이터 보존 테스트용).

## 구글 콘솔 세팅 (사장님 ~20분 · 클로드가 단계별 안내)
1. **Calendar API 사용설정**: `console.cloud.google.com/apis/library/calendar-json.googleapis.com?project=ringgo-2844c` → [사용 설정]
2. **OAuth 동의 화면**: External, 앱이름/이메일, 범위 `.../auth/calendar`, 테스트 사용자에 본인 구글계정 추가(또는 게시)
3. **사용자 인증 정보 → OAuth 클라이언트 ID**:
   - (a) **웹 애플리케이션** → 웹 클라이언트 ID 생성 → 앱의 `serverClientId` 로 사용 (google_sign_in 안드로이드 필수)
   - (b) **Android** → package `com.detailline.callfollowcrm` + SHA-1(위 3개) 등록
4. **웹 클라이언트 ID를 클로드에게 전달** → 앱에 심음

## 앱 구현 (Kotlin — 클로드)
- **로그인**: Credential Manager / GoogleSignIn + scope `calendar`. serverClientId = 웹 클라이언트 ID.
- **API 호출**: OkHttp + Bearer 토큰 (동생 `_AuthClient` 패턴) 또는 google-api-services-calendar.
- **DB**: 일정 소스(CustomerEntity 시공예정일 / A/S)에 `googleCalendarEventId` 저장 + dirty 플래그(실패 재시도).
- **1단계**: 연결 + "시공막내" 캘린더 find-or-create + 올리기(insert/update/move/delete) + 설정화면.
- **2단계**: 내리기(events.list · extendedProperties 복원 · showDeleted · last-writer-wins) = 폰교체 복원 + 손입력 역수입.
- **무손실 왕복**: extendedProperties.private 에 앱 데이터 심음 (동생 `_toEvent`/`_fromAppEvent` 패턴).

## 없앨 것 (본폰 미러링 — [[project_phone_mirror_viewer]])
- **앱**: MirrorSyncManager.kt, MirrorRepository.kt, QrGen.kt(미러링 전용 확인 후), Settings 진입점, 관련 prefs. (정확 범위는 파일 읽고 확정)
- **서버**: 미러링 엔드포인트 = cowork 영역(CLAUDE.md §1) → docs/SYNC.md 로 인계.

## 진행 체크
- [x] 방향확정 · SHA-1 추출 · 동생코드 분석(calendar_sync.dart 정독)
- [x] 콘솔: Calendar API 사용설정 + 동의화면 + Web/Android OAuth 클라이언트 생성
- [x] **코어 4파일 + 배선 전부 작성 + compileDebugKotlin 통과** — 연결/전체동기화 **테스트 가능**
- [x] 저장소: prefs(`googleCalendarId`/`googleCalendarConnected`) + DB **v47**(CustomerEntity `workCalendarEventId`/`asCalendarEventId` + `MIGRATION_46_47`) + `DefaultCalendarSyncStore`
- [x] AppContainer(DI): `googleCalendarConnection` + `calendarSyncManager` (OkHttp callTimeout 30s)
- [x] 설정화면: **MirrorSection → GoogleCalendarSection 교체** (미러 UI 제거 + 연결/지금동기화/연결끄기, `StartIntentSenderForResult` 런처)
- [x] 자동 동기화: 연결 직후 `syncAll` + 앱 켤 때 `syncAll`(연결 시, Application) + 수동 '지금 동기화'
- [ ] (refine) 일정 생성/수정 **즉시** `syncCustomer` (지금은 앱시작/수동/연결시 `syncAll` 로 커버 — 세션 내 즉시성만 부족)
- [ ] 미러링 코드 완전 제거 (`MirrorSection` 함수·`MirrorRepository`·`MirrorSyncManager`·Application `start()` line 418·미러 prefs) + 서버 docs/SYNC.md 인계
- [ ] 2단계(내리기): events.list + extendedProperties 복원 + showDeleted + last-writer-wins

### ⚠️ 테스트 경로 (사장님 결정 필요)
디버그 SHA-1 만 OAuth Android 클라이언트에 등록됨 → **디버그 빌드(adb)로만 구글 로그인 됨.**
- **(A) 스페어폰 + 디버그빌드**: 데이터 유실 X. 단 그 폰엔 실제 일정이 없어 테스트용 일정 하나 넣어 확인.
- **(B) 메인폰 + Play 내부테스트**: 실제 일정으로 진짜 테스트. 단 Play Console → 앱 무결성 → **앱 서명 SHA-1** 을 OAuth Android 클라이언트에 추가해야 함(사장님). → push → 업데이트.
→ 추천: 진짜 확인은 (B). 빠른 확인은 (A).

### 빌드된 코어 파일 (`app/.../data/calendar/`, 전부 컴파일 통과 · **아직 미배선 dead code**)
- `CalendarApi.kt` — 구글 캘린더 REST(OkHttp+org.json): 목록/찾기/생성, 이벤트 insert/update/delete
- `GoogleCalendarConnection.kt` — Identity Authorization API: `authorize()`/`getTokenSilently()`/`tokenFromConsentResult()`
- `CalendarSyncManager.kt` — `ensureCalendar` + `syncCustomer`(시공/AS→이벤트) + `deleteCustomerEvents` + `CalendarSyncStore` 인터페이스

### 참고 — 인증 방식 메모
- 현재 **Authorization API** 방식은 **Android OAuth 클라이언트(package+SHA-1)로 앱 인증** → 웹 클라이언트 ID 는 지금은 **불필요**(offline/백엔드 갈 때만). 웹 클라 생성분은 그대로 둬도 무해.
- 테스트 시: **디버그 SHA-1 로 등록** → **디버그 빌드(adb)** 로만 로그인됨. 사장님 메인폰(Play 설치)은 서명이 달라 데이터 안 잃으려면 **Play 앱서명 SHA-1** 을 Android 클라이언트에 추가하거나, **스페어폰**에서 디버그로 테스트.

### 사장님 확인 필요 (이벤트 표기 — 현재 잠정 기본값, §0 프로토 없음)
1. 제목: `"{이름} 시공"` / `"{이름} A/S"` (이름 없으면 전화번호)
2. 시간: 시각 있으면 그 시각+**2시간 블록**, 없으면 **종일**
3. 설명에 **전화번호** 노출 — 캘린더 공유하면 가족·직원도 봄. 넣을지/뺄지 (주소는 가야 하니 넣는 게 맞을 듯)
4. A/S 도 같은 **"시공막내" 캘린더**(색 1개)로 OK?
