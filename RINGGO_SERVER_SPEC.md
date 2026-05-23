# RING-GO 자체 서버 사양서 (맥미니, Tailnet)

이 문서는 맥미니에서 동작할 작은 HTTP 서버의 사양이다. **RING-GO 안드로이드 앱**이 이 서버를 호출해서:

1. 고객 SMS 수신 시 → **답변 추천 3개를 미리 준비** (백그라운드 LLM 호출)
2. 사장님이 채팅 화면 열 때 → 미리 준비된 답변을 **즉시** 반환

핵심 가치: 사장님 폰 배터리/대기시간 최소화. 무거운 일은 맥미니가 다 한다.

---

## 0. 환경 (이미 갖춰진 것)

- Mac mini M4, 32GB / 512GB
- Tailscale 설치 + 로그인 완료. Tailnet IP = **100.86.114.49**
- Ollama 동작 중, 포트 **11434**, 모델 **gpt-oss:20b**
- `curl http://100.86.114.49:11434/api/tags` 정상 응답 확인됨

## 1. 만들 것

| 항목 | 값 |
|------|-----|
| 포트 | **8000** (Ollama 와 별개) |
| 바인드 | `0.0.0.0:8000` (Tailscale 내부망에서만 접근 — 인터넷 노출 X) |
| 권장 스택 | **Python 3.11+ FastAPI + uvicorn + httpx + SQLite** (또는 Node + Express. FastAPI 가 가장 단순) |
| 인증 | 없음 (Tailscale 자체가 인증) |
| 자동 시작 | launchd (부팅 시 자동 실행) |
| 추가 파일 | **`pricing.md`** (서버 작업 디렉터리 직속) — 사장님 가격표. 동적 로드. §3.3 참조. |

---

## 2. 엔드포인트

### 2.1 POST /prepare-reply

폰의 SmsReceiver 가 고객 SMS 수신 즉시 호출. **fire-and-forget** — 서버는 즉시 200 응답 후 백그라운드에서 LLM 호출.

**요청 헤더**: `Content-Type: application/json`

**요청 본문**:
```json
{
  "phone": "01012345678",
  "latestMessage": "내일 견적 가능해요?",
  "latestMessageReceivedAtMs": 1716234567000,
  "recentHistory": [
    { "role": "customer", "body": "안녕하세요 줄눈시공 문의드려요", "timestampMs": 1716230000000 },
    { "role": "owner",    "body": "네 안녕하세요! 어디신지요?", "timestampMs": 1716230500000 },
    { "role": "customer", "body": "강남 빌라 30평이에요", "timestampMs": 1716231000000 }
  ],
  "customer": {
    "name": "홍길동",
    "memo": "강남 빌라 30평, 베란다 포함",
    "leadHeat": "HOT",
    "depositPaid": false
  }
}
```

필드 의미:
- `phone`: 캐시 키. 그대로 보관.
- `latestMessage`, `latestMessageReceivedAtMs`: 이 메시지에 대한 답변 후보 3개 생성. 폰의 신선도 판정용.
- `recentHistory`: 시간순 오름차순 (오래 → 최신). 최대 20건. `role` 은 `"customer"` 또는 `"owner"`.
- `customer`: nullable. 정보 없으면 `null`. memo/leadHeat 도 nullable.

**응답 (즉시 200)**:
```json
{ "ok": true }
```

서버는 응답 후 **백그라운드 태스크**로 LLM 호출 → 캐시 갱신. 폰은 응답 받는 즉시 끊김.

### 2.2 GET /suggestions/{phone}

사장님이 ChatScreen 열 때 폰이 호출. 캐시에서 즉시 반환.

**경로 파라미터**: `phone` (한국 휴대폰 번호. 정규화 안 한 raw 값)

**응답 — 준비됨**:
```json
{
  "status": "ready",
  "phone": "01012345678",
  "basedOnMessage": "내일 견적 가능해요?",
  "basedOnReceivedAtMs": 1716234567000,
  "generatedAtMs": 1716234572000,
  "suggestions": [
    "내일 가능합니다. 오전/오후 어느 쪽이 편하세요?",
    "네 가능해요. 시간 말씀 주시면 방문 일정 잡겠습니다.",
    "가능합니다. 주소 알려주시면 그쪽으로 가겠습니다."
  ]
}
```

**응답 — 생성 중**:
```json
{ "status": "generating" }
```

**응답 — 캐시 없음**:
```json
{ "status": "missing" }
```

폰은 `status` 값으로 분기. `ready` 아니면 빈 상태로 표시.

---

## 3. LLM 호출 (Ollama /api/chat)

prepare-reply 받으면 백그라운드에서 다음을 실행:

### 3.1 시스템 프롬프트

시스템 프롬프트는 **고정 부분 + 동적 가격표 부분**으로 구성된다.

**고정 부분** (코드에 상수로):

```
너는 줄눈 시공 사장님이 고객 문자에 답장할 때 도와주는 비서다.
사장님이 답하기 전에, 사장님이 보낼 만한 답변 후보 3개를 미리 제안한다.

받는 정보:
- 사장님과 고객의 최근 대화 (시간순)
- 고객 정보 (이름·메모·리드 온도·입금 여부, 없을 수도 있음)
- 고객이 방금 보낸 메시지

규칙:
- 정확히 3개 답변을 JSON 으로 답하라.
- 각 답변은 한 문장에서 두 문장. 짧게.
- 고객에게 보낼 메시지이므로 존댓말.
- 사장님은 친절하지만 군더더기 없이 핵심부터.
- 인사말·이모지·따옴표를 임의로 새로 넣지 마라.
- 가격·날짜·시간 같은 정보는 대화 내용 또는 아래 가격표에서만 추출. 추측하지 마라.
- 3개 답변은 서로 다른 방향성을 가져라 (예: 적극 응답 / 추가 정보 요청 / 일정 협의).

가격 문의 케이스 처리:
- 고객이 가격/견적 문의면 아래 가격표를 활용해 답변 후보 만들기.
- 신축/구축 여부 미확정 → 한 후보는 "신축이세요 구축이세요?" 물어보기.
- 타일 크기 미언급 → 한 후보는 "타일 사진 보내주시면 정확히 견적 드려요" 안내.
- 답변 형식: 표 금지. 항목별 줄바꿈 나열 + 합계.
- 실리콘 제거/셀프줄눈/누수 가능성 = "현장 확인 후 추가될 수 있어요" 한 문장 덧붙임.

답 형식은 반드시 다음 JSON 만. 다른 텍스트 X.
{
  "suggestions": ["...", "...", "..."]
}

────── 가격표 ──────
{PRICING}
```

**동적 부분** `{PRICING}`: `pricing.md` 파일을 통째로 읽어서 치환. 자세한 형식은 §3.4 참조.

### 3.2 user 메시지 구성

다음 형식으로 한 덩어리 텍스트로 합쳐서 보낸다:

```
[고객 정보]
이름: {customer.name or "없음"}
메모: {customer.memo or "없음"}
리드 온도: {customer.leadHeat or "없음"}
계약금 입금: {"받음" if customer.depositPaid else "안 받음"}

[최근 대화]
({recentHistory[0].role}): {recentHistory[0].body}
({recentHistory[1].role}): {recentHistory[1].body}
...
(customer): {latestMessage}   ← 이게 방금 받은 메시지

답변 후보 3개를 JSON 으로 생성하라.
```

### 3.3 가격표 동적 삽입 (pricing.md)

`pricing.md` (서버 작업 디렉터리 직속) 가 가격표 원본. 사장님이 이 파일 텍스트만 고치면 다음 LLM 호출부터 즉시 반영되도록 동작해야 한다.

구현:
```python
# main.py 개념
import os
from pathlib import Path

PRICING_PATH = Path(__file__).parent / "pricing.md"
_pricing_cache = {"mtime": 0, "content": ""}

def load_pricing() -> str:
    mtime = PRICING_PATH.stat().st_mtime
    if mtime != _pricing_cache["mtime"]:
        _pricing_cache["mtime"] = mtime
        _pricing_cache["content"] = PRICING_PATH.read_text(encoding="utf-8")
    return _pricing_cache["content"]

def build_system_prompt() -> str:
    return SYSTEM_PROMPT_TEMPLATE.replace("{PRICING}", load_pricing())
```

- 매 LLM 호출 직전 `load_pricing()` 호출 → mtime 바뀌었으면 reload.
- 서버 재시작 불필요.
- 파일 없으면 `{PRICING}` 자리를 `"(가격표 파일 없음)"` 으로 대체 (LLM 이 가격 추측하지 못하게).

`pricing.md` 의 현재 내용은 [pricing.md](pricing.md) 파일 참조 (윈도우 측에서 동일하게 보관, 맥미니로 복사해서 서버 디렉터리에 둘 것).

### 3.4 Ollama 호출

```python
POST http://localhost:11434/api/chat
{
  "model": "gpt-oss:20b",
  "stream": false,
  "format": "json",     ← 중요: Ollama 의 JSON 모드 활성화
  "messages": [
    {"role": "system", "content": "<위 시스템 프롬프트>"},
    {"role": "user",   "content": "<위 user 메시지>"}
  ]
}
```

`format: "json"` 을 주면 Ollama 가 valid JSON 만 출력하도록 강제. 응답의 `message.content` 를 `json.loads` 로 파싱 → `suggestions` 배열 추출.

응답 함정:
- `message.thinking` 필드는 gpt-oss 내부 추론 — **절대 사용하지 마라**.
- 파싱 실패 시: 캐시에 `{"status": "missing"}` 저장하고 끝. 폰이 ↻ 누르면 재시도.

---

## 4. 캐시 정책

**SQLite 한 테이블이면 충분**:

```sql
CREATE TABLE IF NOT EXISTS suggestions_cache (
  phone TEXT PRIMARY KEY,
  status TEXT NOT NULL,                   -- 'ready' | 'generating' | 'missing'
  based_on_message TEXT,
  based_on_received_at_ms INTEGER,
  generated_at_ms INTEGER,
  suggestions_json TEXT,                  -- JSON array stringify
  updated_at_ms INTEGER NOT NULL
);
```

흐름:
1. POST /prepare-reply 받으면 `status='generating'`, `based_on_*` 저장 + 백그라운드 태스크 시작
2. LLM 호출 성공 시 `status='ready'`, `suggestions_json` 갱신
3. 실패 시 `status='missing'` 으로 정리
4. GET /suggestions/{phone} = 그대로 반환

**만료 정책**: 같은 phone 에 새 prepare-reply 가 오면 덮어쓰기. 별도 TTL 필요 없음 (사장님이 보낸 메시지 후엔 폰에서 자동 숨겨짐).

---

## 5. 동시성

- prepare-reply 가 여러 번 빠르게 와도 → **마지막 것만 유효**. 진행 중인 작업이 있으면 취소하거나, 그냥 마지막 결과로 덮어쓰기 (단순함 우선).
- FastAPI + asyncio.create_task 로 백그라운드 시작. cancellation 은 phone 키별 dict 로 추적.

---

## 6. launchd 자동 시작

`~/Library/LaunchAgents/com.detailline.ringgo-server.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.detailline.ringgo-server</string>
  <key>ProgramArguments</key>
  <array>
    <string>/Users/<USERNAME>/.pyenv/shims/uvicorn</string>  <!-- 또는 실제 uvicorn 경로 -->
    <string>main:app</string>
    <string>--host</string>
    <string>0.0.0.0</string>
    <string>--port</string>
    <string>8000</string>
  </array>
  <key>WorkingDirectory</key>
  <string>/Users/<USERNAME>/ringgo-server</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>/Users/<USERNAME>/ringgo-server/stdout.log</string>
  <key>StandardErrorPath</key>
  <string>/Users/<USERNAME>/ringgo-server/stderr.log</string>
</dict>
</plist>
```

로드: `launchctl load ~/Library/LaunchAgents/com.detailline.ringgo-server.plist`

---

## 7. 검증 curl

서버 띄운 후 맥에서:

```bash
# 1. prepare-reply 호출
curl -X POST http://localhost:8000/prepare-reply \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "01012345678",
    "latestMessage": "내일 견적 가능해요?",
    "latestMessageReceivedAtMs": 1716234567000,
    "recentHistory": [],
    "customer": null
  }'

# 즉시 {"ok":true} 받음

# 2. 약 5초 기다린 후 GET
sleep 5
curl http://localhost:8000/suggestions/01012345678
# → {"status":"ready", "suggestions":[...]} 받아야 정상
```

폰에서 (Tailscale 연결된 상태):
```
curl http://100.86.114.49:8000/suggestions/01012345678
```

**가격 문의 시나리오 검증** (pricing.md 가 잘 박혀있는지):

```bash
curl -X POST http://localhost:8000/prepare-reply \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "01099999999",
    "latestMessage": "신축인데 욕조 있는 화장실 바닥 줄눈 견적 얼마예요?",
    "latestMessageReceivedAtMs": 1716234567000,
    "recentHistory": [],
    "customer": null
  }'

sleep 7
curl http://localhost:8000/suggestions/01099999999
```

기대 결과: 후보 3개 중 적어도 하나에 **"40만원"** 또는 **"40"** 이 정확히 포함돼야 함 (신축 / 욕조 있는 화장실 바닥 = 40만원). 또 다른 후보 하나는 **타일 크기 사진 요청** 포함되면 더 좋음.

이 검증 통과 = pricing.md 가 시스템 프롬프트에 실제로 박히고 LLM 이 활용 중이라는 증거.

---

## 8. 첫 마일스톤

1. FastAPI 프로젝트 셋업 + 두 엔드포인트 스켈레톤
2. SQLite 캐시 + 메모리 dict
3. **`pricing.md` 파일을 서버 디렉터리에 복사** + `load_pricing()` 함수 (mtime 캐시, 자동 reload)
4. Ollama 호출 (system prompt + `{PRICING}` 치환 + user 메시지 + format=json)
5. 검증 curl 통과 (가격 문의 시나리오 포함 — §7 확장 예시 참조)
6. launchd 자동 시작 + 부팅 후 재확인

JSON 파싱 실패 / Ollama timeout / 모델 미로드 같은 에러 케이스도 silent 처리 (`status: missing` 저장). 사장님 폰 측에서는 ↻ 로 재시도 가능.

---

## 9. RING-GO 안드로이드 측 코드 상태

이 사양서가 가리키는 클라이언트 코드는 이미 작성됨 (윈도우 RING-GO 프로젝트 측):

- [SmsReceiver](app/src/main/java/com/detailline/callfollowcrm/service/SmsReceiver.kt) — SMS 수신 시 POST /prepare-reply
- [ServerSuggestionRepository](app/src/main/java/com/detailline/callfollowcrm/ai/ServerSuggestionRepository.kt) — POST/GET 추상화
- [ChatViewModel.loadSuggestions / regenerateSuggestions](app/src/main/java/com/detailline/callfollowcrm/presentation/screen/chat/ChatViewModel.kt) — fetch + 재생성 폴링
- [ChatScreen SuggestionArea](app/src/main/java/com/detailline/callfollowcrm/presentation/screen/chat/ChatScreen.kt) — [1][2][3] 칩 UI

서버만 띄우면 즉시 동작. BASE_URL 은 클라이언트에 `http://100.86.114.49:8000` 으로 박혀 있음.
