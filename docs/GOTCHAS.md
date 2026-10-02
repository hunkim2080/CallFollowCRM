# 🩹 여기서 실제로 사고가 났던 자리 (이 repo 전용)

**이 파일은 「조심하자」 모음이 아니다.** 아래 전부 **진짜로 한 번씩 터졌고**, 그래서 적어둔 것이다.
새로 들어온 Claude(어느 머신이든)는 **CLAUDE.md 다음으로 이걸 먼저** 읽는다.

> 왜 따로 파냐: 이 지식이 2026-10-02 까지 **데스크탑 Claude 한 대의 개인 메모에만** 있었다.
> 맥미니 Cowork 나 새 컴퓨터의 Claude 는 모른 채 시작했고, 그래서 같은 사고를 다시 쳤다.
> repo 안에 있어야 **모두가** 본다. (2026-10-02 Fable 점검에서 지적)

---

## 1. 🖥️ 폰 — 업무폰은 **읽기 전용**

| 폰 | 일련번호 | 무엇 | 할 수 있는 일 |
|---|---|---|---|
| 사장님 업무폰 | `R3CW201RMCW` (SM-S918N, Android 16) | **실제 영업에 쓰는 폰** | **보기·재기·스크린샷만.** 설치·삭제·데이터 지우기 **금지** |
| 테스트폰 | `23514638000c7ece` (SM-G965N, Android 10) | 우리 서명 | `adb install -r` 여기만 |

**사고(2026-09-18):** 모델명만 보고 업무폰이라 생각 못 하고 **앱을 지웠다.**
→ 지우거나 데이터를 날리는 명령 전에 **① 일련번호 ② `installerPackageName`** 을 확인하고
**사장님께 먼저 묻는다.** (`com.android.vending` = 플레이로 받은 폰 = 업무폰일 가능성)

```bash
adb devices -l
adb -s <일련번호> shell "dumpsys package com.detailline.callfollowcrm | grep -i installerPackage"
```

**업무폰에서 뭘 못 보면** 그 대신 **단위 테스트로 못을 박는다.**
(예: S23U 의 키보드 실측값 991/316/675 를 `KeyboardFitTest.kt` 에 박아뒀다 — 폰을 못 만지니 숫자를 가뒀다)

**화면을 누를 땐 좌표 금지 — 글자로 찾는다.** `uiautomator dump` → 글자로 찾아 그 가운데를 누른다.
⚠️ 버튼 글자가 **본문 안에도** 들어 있으면(「'해제'하면…」) 본문을 누른다 → `--exact` 로 찾는다.

---

## 2. 🔨 앱 빌드는 **PowerShell 로만**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleRelease --console=plain -q; "BUILD=$LASTEXITCODE"
```

**사고:** Bash 에서 `./gradlew ... | tail` 로 돌렸다. 파이프가 **실패 코드를 가려서** 빌드가 깨졌는데
성공으로 보였고, **옛 APK** 를 폰에 넣고 "고쳤습니다" 라고 보고했다.

- ❌ Bash gradle · ❌ 파이프(`| tail`, `| grep`) — exit code 를 가린다
- ✅ 끝에 `$LASTEXITCODE` 를 **반드시** 찍는다. `BUILD=0` 이 아니면 **아무것도 안 된 것**이다
- adb · git · scp 는 Bash 에서 해도 된다

**가드는 release 빌드에만 걸린다.** `assembleDebug` 는 dup/style/brand 를 **안 탄다**
(compose_guard 만 `preBuild` 라 항상 탄다). 그래서 **폰에 넣을 땐 항상 `assembleRelease`.**

**가드 뒷문**(`-PskipDupCheck` / `-PskipStyleCheck` / `-PskipBrandCheck` / `-PskipComposeCheck`)은
**사장님이 쓰라고 할 때만.** 급해서 끄면 룰이 조용히 꺼진 채 배포된다.
`dupBaselineUpdate` / `styleBaselineUpdate` 는 **합쳐서 줄였을 때만** 돌린다 — 늘려놓고 돌리면 기준선이 썩는다.

---

## 3. 🛠️ 라이브 서버는 **git 이 아니다** — 통짜 배포 금물

- 라이브: 맥미니 `~/ringgo-server/` — **git 저장소가 아니고**, origin 보다 **~193커밋 뒤**에 있다
- 접속: `ssh macmini` (사용자 hun, sudo 불가), LaunchAgent 라 `launchctl` 로 혼자 재시작 가능
- **`server/deploy_phase1.sh` 를 그냥 돌리면** repo 의 main.py 가 통째로 올라가 **미배포분 193커밋이 한꺼번에 나간다**

**한 군데만 고치는 안전한 절차 (실제로 쓰는 것):**

1. 라이브 파일을 **내려받는다** — `scp macmini:~/ringgo-server/main.py <스크래치>/`
2. **패치 스크립트**로 그 사본만 고친다 (손으로 ssh 안에서 편집하지 않는다)
3. 로컬에서 `python -m py_compile` — 문법 먼저
4. 올린다 → 서버에서 **다시** `py_compile`
5. **백업**: `cp main.py main.py.bak-<날짜시각>`
6. 바꿔 넣고 `launchctl kickstart -k gui/$(id -u)/com.detailline.ringgo-server`
7. **`bash smoke.sh`** — 10개 다 통과해야 끝
8. 같은 변경을 **repo 쪽 `server/main.py` 에도** 넣고 push (안 하면 괴리가 더 벌어진다)

⚠️ **여러 줄 ssh 명령은 `\r` 때문에 깨진 것처럼 보인다** — 한 줄로 쏘거나 스크립트를 올려서 돌린다.

**배포 확인은 `/health` 로 하지 않는다.** `/health` 는 무슨 일이 나도 200 을 준다.
**사고(2026-09-23):** `/health` 가 200 이라 넘어갔는데 **폴링이 2시간 멈춰** 있었다. → 반드시 `smoke.sh`.

**서버 Python 은 3.9** — `str | None` 같은 새 문법을 쓰면 **502**. `Optional[str]` 로 쓴다.
**서버가 모르는 칸은 조용히 버린다** (pydantic). 앱이 새 칸을 보내도 **에러 없이 사라진다** —
보내고 **되읽어서** 확인한다.

---

## 4. 🔤 한글이 깨져 보이는 **착시** (Windows)

- **`curl … | python`** (stdin) 은 cp949 로 읽혀 **멀쩡한 한글이 깨진 것처럼** 보인다.
  → `curl -o <파일>` 로 받고 **utf-8 로 열어** 확인한다.
- **bash heredoc 안의 한글**도 cp949 로 망가진다 → 한글이 든 패치 스크립트는 **Write 도구로** 쓴다.
- **Git Bash `/tmp` ≠ Windows python `/tmp`** — `curl -o /tmp/x` 는 Git Bash 의 /tmp 인데
  `python open('/tmp/x')` 는 `D:\tmp` 를 본다. **조용히 실패**하고 0건으로 보인다 → 절대경로를 쓴다.
- 콘솔에 깨져 보여도 **파일 안은 멀쩡할 수 있다** — 판정은 파일을 utf-8 로 다시 읽어서 한다.

---

## 5. 🚀 올리기 — 어디까지가 자동인가

- `upload-*` **태그를 밀면** GitHub Actions 가 AAB 를 만들어 **내부 테스트까지만** 올린다
- **정식(프로덕션) 출시는 사장님이 직접** 한다. 자동 승격이 안 되는 건 **고장이 아니다**
- **versionCode = `max(version.properties, 깃 커밋수) + 1`** (릴리스 빌드). `version.properties` 는
  gitignore 라 CI 에선 **커밋수 + 1**. 로컬 빌드마다 올라가 숫자가 부풀었으니 **비교는 깃 커밋수로**
- **폰으로 못 본 변경**은 `docs/PHONE_CHECK_TODO.md` 에 쌓고, 폰이 꽂히면 **그것부터** 눈으로 본다
- 자세한 건 `docs/PLAY_AUTO_DEPLOY_SETUP.md`
- **심사자는 인증문자를 못 받는다** → 마스터 번호/코드(plist 의 `AUTH_MASTER_PHONES`/`CODE`)로 들어온다.
  로그인에 관문을 더하면 **심사가 막힌다** — 바꿀 때 이걸 먼저 생각한다

---

## 6. 💣 앱이 아예 안 켜지거나 화면이 꺼지는 사고

- **Composable 안에서 early return** → 슬롯테이블이 어긋나 recompose 때 크래시. **세 번 밟았다.**
  지금은 `tools/compose_guard` 가 **빌드로 막는다** — 우회하지 말고 `if/else` 로 감싼다
- **Room 마이그레이션 INSERT 에 NOT NULL 칸을 빼먹으면** 새로 깐 폰에서 **앱이 아예 안 켜진다**
  (2026-09-17 `jobs.memo` 실사고). 배포 전 **로컬 sqlite 에 Room 스키마로 한 번 돌려본다**
- **`@Entity(indices=)` 와 마이그레이션의 `CREATE INDEX` 가 다르면** 첫 쿼리에서 크래시
- **KDoc 주석 안에 `image/*`** 를 쓰면 `*/` 로 읽혀 빌드가 깨진다

---

## 7. 🤫 조용한 실패가 제일 비싸다

- `runCatching { }` 으로 덮고 결과를 안 보는 자리가 **925곳 중 500곳 넘게** 있었다.
  그 안에 **사장님이 손으로 적은 걸 저장하는 자리**가 섞여 있었다 (현장 메모가 조용히 안 저장됨)
- **사장님이 적은 것**(메모·금액·일정·사진·고객)을 저장하는 자리는 **실패하면 화면에 말한다** —
  공용 `presentation/util/SaveGuard.kt` 를 쓴다. 조용히 넘겨도 되는 건 **지워도 되는 것**(캐시·임시파일)뿐
- **막는 것은 서버가 최종 결정권자다.** 앱에서만 막으면 **옛 버전은 그대로 통과**한다 (2026-10-02 실제로 그랬다).
  둘 다 넣고 **끝까지 쏴서** 확인한다(409 가 정말 오는지)

---

## 끝내기 전 세 줄 (CLAUDE.md §12·§13 과 같은 것)

1. **두 벌 grep** — 방금 만든 것과 같은 일을 하는 코드가 **다른 이름으로** 또 있나?
2. **느린 네트워크 · 빈 값 · 연타** 가 오면 어떻게 되나? 하나라도 "모르겠다" 면 안 끝난 것
3. **`assembleRelease` 로 빌드했고 `BUILD=0` 을 눈으로 봤나?** (가드는 release 에만 걸린다)
