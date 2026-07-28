# Play 자동 배포 세팅 (1회) — 2026-07-29

> 목표: 매번 Play Console 에서 **수동 업로드하던 걸 없앰.** GitHub Actions 가 AAB 빌드+업로드까지 자동.
> 워크플로: `.github/workflows/play-deploy.yml` (이미 만들어 둠 — 아래 비밀값만 넣으면 동작).

## 동작 방식
- **[Run workflow] 버튼**: GitHub 저장소 → **Actions** 탭 → "Play 배포" → **Run workflow** → 트랙(internal/production) 골라 실행. 정식 출시는 이걸로.
- **main 에 app 코드 push**: 자동으로 **내부 테스트(internal)** 트랙에 올라감(구글 심사 없음·즉시).
- versionCode 는 자동(git 커밋수+1). 지금 커밋수 1179 > 마지막 1157 이라 안전하게 증가.

## ⚠️ 두 가지 현실 (자동화해도 못 바꾸는 것)
1. **정식(production) 트랙은 구글 심사(몇 시간~며칠) 를 여전히 거침.** 내부 테스트는 즉시.
2. 아래 비밀값(서명키·서비스계정)은 **서명키만큼 중요** → GitHub Secrets(암호화됨)에만. 절대 코드/공개에 X.

---

## Part A. Play 서비스 계정(업로드 열쇠) 만들기 — 사장님 구글 계정에서 (제가 대신 못 함)
3~5분. 결과물 = **JSON 파일 하나**.

1. **Google Play Console** (play.google.com/console) 접속.
2. 왼쪽 메뉴 맨 아래 **설정(Settings)** → **API 액세스(API access)**.
   (또는 "개발자 계정 > API 액세스".)
3. **Google Cloud 프로젝트 연결** (없으면 "새 프로젝트 만들기").
4. **서비스 계정 만들기** 클릭 → Google Cloud Console 로 이동됨 →
   - 서비스 계정 생성(이름 아무거나, 예: `play-uploader`)
   - 만든 계정 → **키(Keys)** 탭 → **키 추가 → 새 키 만들기 → JSON** → **다운로드**. (이 JSON 이 열쇠)
5. **Play Console 로 돌아와서** 그 서비스 계정에 **권한 부여**:
   - 방금 만든 서비스 계정이 목록에 뜸 → **액세스 권한 부여**
   - 앱 권한에서 **"테스트 트랙에 출시"** 체크 (정식도 자동으로 내려면 **"프로덕션 출시"** 도 체크)
   - **적용/초대**.
6. 다운로드한 **JSON 파일 내용 전체** = 아래 `PLAY_SERVICE_ACCOUNT_JSON` 값.

---

## Part B. GitHub 비밀값 6개 등록
저장소 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret** 로 6개 추가.

| 이름 | 값 만드는 법 |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | Part A 에서 받은 **JSON 파일 내용 전체**를 그대로 붙여넣기 |
| `KEYSTORE_BASE64` | 서명키를 base64 로 (아래 명령) |
| `KEYSTORE_PASSWORD` | `keystore.properties` 의 `storePassword=` 값 |
| `KEY_ALIAS` | `keystore.properties` 의 `keyAlias=` 값 |
| `KEY_PASSWORD` | `keystore.properties` 의 `keyPassword=` 값 |
| `GOOGLE_SERVICES_JSON` | `app/google-services.json` 을 base64 로 (아래 명령) |

**base64 만들기** (프로젝트 폴더에서 실행 → 출력된 긴 문자열을 통째로 복사 → GitHub 시크릿 값에 붙여넣기):

- Git Bash:
  ```bash
  base64 ringgo-release.jks | tr -d '\n'        # → KEYSTORE_BASE64
  base64 app/google-services.json | tr -d '\n'  # → GOOGLE_SERVICES_JSON
  ```
- PowerShell:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("ringgo-release.jks"))       # → KEYSTORE_BASE64
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("app/google-services.json")) # → GOOGLE_SERVICES_JSON
  ```

`keystore.properties` 안의 비번 3개는 그 파일을 열어 값만 복사(제가 값은 안 봄).

---

## Part C. 첫 실행 & 확인
1. 6개 시크릿 다 넣은 뒤, GitHub → **Actions** → "Play 배포" → **Run workflow** → track=`internal` → 실행.
2. 초록 체크면 성공 → Play Console 내부 테스트에 새 버전 올라와 있음.
3. 빨간 X 면 로그 보고 알려주세요 — 대개 시크릿 오타/권한 미부여가 원인(제가 같이 봄).

> 이후: **정식 출시** = Actions 에서 track=`production` 으로 Run (그다음 구글 심사). 또는 Play Console 에서 내부→정식 승격.

## 참고 (기술)
- 빌드: `./gradlew bundleRelease` (JDK 17, ubuntu-latest). 서명키·google-services.json 은 CI 에서 시크릿으로 복원(러너는 1회용, 끝나면 폐기).
- 업로드: `r0adkll/upload-google-play@v1`, packageName=`com.detailline.callfollowcrm`.
- **로컬 release 빌드와 병행 주의**: CI 로 넘어간 뒤엔 로컬에서 또 release 빌드+업로드하면 같은 versionCode(커밋수+1) 라 Play 가 둘 중 하나 거절. 릴리즈는 CI 로 일원화 권장.
- R8(minify) off 라 크래시 매핑 업로드 이슈 없음.
