# 📊 베타 대시보드를 **기계가** 읽는 법 (아침 브리핑용)

> 2026-09-23 — 아침 브리핑이 "서버 응답 없음" 으로 실패한 원인과 해결.
> 사장님: "쉬운 쪽으로 가자" → **코드는 안 고치고, 부르는 법만 맞춘다.**

## 무엇이 문제였나

`/admin/beta/dashboard/data` 는 **문이 두 개**인데 **서로 다른 열쇠**를 요구한다.

| 층 | 요구하는 열쇠 | 어디서 |
|---|---|---|
| ① 바깥문 (`/admin` 전체) | 쿠키 **또는** 주소에 `?token=<ADMIN_TOKEN>` | `_admin_gate_middleware` |
| ② 안쪽 방 (이 엔드포인트) | `Authorization: Bearer <ADMIN_TOKEN>` 헤더 | `_admin_auth_bearer_from_header` |

**하나만 주면 반드시 실패한다.**

| 준 것 | 결과 |
|---|---|
| `?token=` 만 | **401** (문은 통과, 방이 막음) |
| `Bearer` 헤더만 | **303 → /admin/login** ← 브리핑이 본 것 |
| **둘 다** | **200** ✅ |

브라우저로는 멀쩡하다 — 로그인하면 쿠키가 생기고(①), 페이지 안 JS 가 헤더를 붙인다(②).
**사람이 아니라 기계가 읽을 때만 걸린다.**

## 올바른 부르는 법

```bash
TOK="$ADMIN_TOKEN"   # plist EnvironmentVariables 에 있음. 로그·메일에 찍지 말 것.

curl -s --max-time 60 \
  -H "Authorization: Bearer $TOK" \
  "https://api.si0in.kr/admin/beta/dashboard/data?days=7&token=$TOK"
```

맥미니 안에서면 `http://127.0.0.1:8000` 으로 불러도 된다(터널·인증서 안 탐).

토큰은 plist 에서 꺼낸다 — **값을 절대 출력하지 말 것**:

```python
import plistlib
p = plistlib.load(open('/Users/hun/Library/LaunchAgents/com.detailline.ringgo-server.plist','rb'))
tok = (p.get('EnvironmentVariables') or {}).get('ADMIN_TOKEN')
```

## 실패를 어떻게 알아보나

- **303** → 바깥문에서 튕김. `?token=` 을 빠뜨렸다.
- **401** → 방에서 막힘. `Authorization: Bearer` 헤더를 빠뜨렸다.
- **403** → 토큰 값이 틀리다 (서버가 다른 ADMIN_TOKEN 으로 떠 있다).
- **503** → 서버에 ADMIN_TOKEN 자체가 안 박혔다 → plist 확인.

브리핑 메일에 "서버 응답 없음" 이라고 쓰면 **서버가 죽은 줄 안다.**
실제론 살아 있었다(HTTP 200). → 위 코드 중 무엇이었는지 **숫자를 적어라.**

## 응답에 뭐가 들어있나

`days`, `generated_at_ms`, `subscription`, `adoption`, `system_health`,
`trends`, `kpi`, `network`, `cost`, `daily_series`, `feature_usage`, `users`, `details`

브리핑에 쓸 만한 것:
- `kpi.total_users` / `active_7d` / `new_7d`
- `system_health.today_errors` / `errors_24h` / `recent[]`
- `cost.period_krw` / `period_calls`
- `subscription.paid_total` / `month_collected_krw`
- `adoption.rate_pct` / `feature_discovery[]`

⚠️ **`users[]` 에는 사장님 본인 폰도 섞여 있다.** 숫자를 보고할 땐 내부 것을 빼라 —
`docs/INTERNAL_TESTERS.md` 참고.

## 제대로 고치려면 (나중에)

열쇠가 둘인 구조 자체가 헷갈린다. 안쪽 방도 바깥문과 같은 열쇠를 받게 하면 된다
(`_admin_auth_bearer_from_header` 가 쿠키·`?token=` 도 인정). **서버 코드 변경이라 코워크 영역.**
