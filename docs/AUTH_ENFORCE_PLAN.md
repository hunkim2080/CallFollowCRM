# 🔐 「번호만 대면 열리는 문」 닫기 — 켜기 전 점검표

> 2026-10-03 안드로이드 Claude 가 실측해 적음. **아직 아무것도 안 켰다.**
> 켜는 것은 라이브 서버 설정을 바꾸는 일이라 **사장님이 시킬 때만** 한다.

---

## 1. 지금 상태 — **열려 있다** (실측)

토큰 없이 가짜 번호로 두드려 봤다(진짜 자료는 안 건드림):

```
200   /api/shared/with-me?phone=01000000000
200   /api/quote/submissions?owner_phone=01000000000
200   /suggestions/01000000000
```

**200** = 아무나 번호만 알면 그 사람의 협업 현장·접수서·상담 내용을 받아갈 수 있다.
(`422` 가 나온 `/api/team/members` 는 필수 인자가 달라서일 뿐, 막힌 게 아니다.)

## 2. 막는 장치는 **이미 다 만들어져 있고, 꺼져 있다**

- `server/main.py` 의 `_auth_enforce_middleware` — `AUTH_ENFORCE=1` 일 때만 작동
- 보호 경로 목록 `_AUTH_PROTECT_PREFIXES` (협업·팀·접수서·사진·미러·푸시·말투 + `/suggestions/`·`/api/customer-persona/`)
- GET 은 **요청 번호 ≠ 토큰 번호면 403**, POST 는 **토큰 보유까지** 확인

> 켜는 법은 맥미니 `~/Library/LaunchAgents/com.detailline.ringgo-server.plist` 의
> `EnvironmentVariables` 에 `AUTH_ENFORCE=1` 을 넣고 reload — **한 줄**이다.

## 3. 앱은 준비됐나 — **그렇다** (실측)

- `ai/Net.builder()` 로 만든 통신 길에 `SessionAuthInterceptor` 가 물려
  **모든 `api.si0in.kr` 요청에 `Authorization: Bearer` 가 자동으로 붙는다** — 쓰는 곳 **23곳**
- 손으로 `OkHttpClient.Builder()` 를 만든 곳은 **7곳**인데, 세어 보니
  **보호 경로를 부르는 것은 0곳**이다:

| 어디 | 어디로 | 왜 면제인가 |
|---|---|---|
| `AuthRepository` | `/api/auth/*`·`/api/consent` | **로그인 자체** — 토큰을 받기 전이다 |
| `DiagnosticsReporter` | `/api/diagnostics/report` | 보호 목록에 없음 |
| `ExpoRepository` ×2 | `/api/expo/*` | 보호 목록에 없음(박람회는 별세계) |
| `OllamaRefineRepository` | Tailnet Ollama | 우리 서버가 아님 |
| `AppContainer`(CalendarApi) | 구글 캘린더 | 우리 서버가 아님 |
| `UpdateChecker` | `si0in.kr` 버전 | 보호 목록에 없음 |

> 🤖 **새로 만들면 빌드가 막는다** — `dup_guard` 규칙 `okhttp-raw` (2026-10-03 추가).
> 위 일곱만 면제(homes). 면제를 늘리기 전에 **그 경로가 `_AUTH_PROTECT_PREFIXES` 에
> 없는지 반드시 확인**할 것. 손으로 만든 길은 **켜는 날 그 화면만 조용히 빈다.**

## 4. 🔴 그래서 **지금 켜면 안 되는 이유** — 토큰이 없는 사람이 있다

`presentation/AppRoot.kt` 의 첫 화면 결정은 **번호(`bizPhone`)만 본다.**
**세션 토큰이 있는지는 안 본다.**

```
container.preferences.bizPhone.isBlank() -> 로그인
...
else -> 홈
```

그래서 **OTP 가 생기기 전에 번호만으로 들어온 사람**(= 사장님 폰들이 그렇다)은
**토큰이 없는 채로 홈에 들어간다.** 이 상태에서 `AUTH_ENFORCE=1` 을 켜면:

1. 모든 보호 경로가 **401**
2. `SessionAuthInterceptor` 는 **`hadToken` 이 false 라 재로그인 신호를 안 켠다**
   (2026-08-15 QR 튕김 사고 때문에 일부러 넣은 가드다 — 그 자체는 맞다)
3. 결과 — **로그인하라는 말도 없이 화면만 텅 빈다.** 사장님은 「앱이 고장났다」로 본다

> 이게 **조용한 실패**다(§13①). 막는 건 맞는데 **말을 안 한다.**

## 5. 켜기 전에 해야 할 일 (순서대로)

- [ ] **① 서버가 「로그인이 필요하다」고 분명히 말하게 한다**
      미들웨어의 401 응답에 헤더 한 줄(`X-Auth-Required: 1`)을 더한다.
      → 앱이 **글자를 뒤지지 않고** 알아챌 수 있다(글자 비교는 두 벌이 된다).
- [ ] **② 앱이 그 신호를 받으면 재로그인으로 보낸다**
      `SessionAuthInterceptor` 에서 그 헤더가 보이면 `hadToken` 과 **무관하게** 재로그인 신호.
      ⚠️ 403(소유권 불일치)과 `/api/web/` 401 은 **지금처럼 그대로 둔다.**
      ⚠️ `AUTH_ENFORCE=0` 인 동안은 그 헤더가 아예 안 오므로 **무해**하다.
- [ ] **③ 문자(OTP)가 진짜 가는지 실측** — SOLAPI.
      이게 안 되면 **아무도 다시 못 들어온다.** 켜기 전 마지막 관문.
- [ ] **④ 사장님 폰 세 대를 각각 한 번씩 OTP 로그인** (테스트폰·본업무폰·디테일라인)
- [ ] **⑤ 그다음에 `AUTH_ENFORCE=1`** — 켜고 바로 `bash server/smoke.sh` 10개 + 위 세 경로가
      **401/403 으로 바뀌는지** 다시 두드려 확인

## 6. 왜 서두르는 게 이득인가

지금은 쓰는 사람이 **내부 테스터뿐**이다. 로그인을 다시 시키는 비용이 **세 대**다.
회원이 늘고 나서 켜면 **그 사람들이 전부 한 번에 로그아웃된다** — 같은 일인데
그때는 「앱이 갑자기 로그인하래요」 문의가 쏟아진다.

**싸게 닫을 수 있는 창은 지금이다.**
