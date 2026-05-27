# RING-GO 서버 Phase 1 업그레이드 사양서

> 이 문서는 **이미 동작 중인 `server/main.py` (FastAPI + Ollama gpt-oss:20b)** 를 **Anthropic Claude Sonnet 4.6 API** 로 교체하고, 답변 품질을 본격적으로 끌어올리는 작업의 사양이다.

## 0. 왜 바꾸나

- 사장님이 실제 사용 후 피드백 = "AI 답변이 오타 교정 수준. 말투 변환 느낌 X"
- 자체 LLM (gpt-oss:20b on Ollama) 의 한국어 톤 변환 능력 한계
- 비전 = "AI 세일즈 운영 OS" → **답변 품질이 제품 핵심 가치**
- → 결정: Claude Sonnet 4.6 API 로 교체 (사장님 본인 결정)

## 1. 변경 범위

### 1.1 의존성 추가
`server/requirements.txt` 에 추가:
```
anthropic>=0.40.0
```

### 1.2 환경 변수 (보안)
**API 키는 코드에 박지 말 것**. launchd plist 의 `EnvironmentVariables` 또는 `~/.zshrc` 의 `export CLAUDE_API_KEY=...` 로:

`~/Library/LaunchAgents/com.detailline.ringgo-server.plist` 의 `EnvironmentVariables` 섹션 추가:
```xml
<key>EnvironmentVariables</key>
<dict>
    <key>CLAUDE_API_KEY</key>
    <string>sk-ant-...</string>
</dict>
```

`main.py` 상단:
```python
import os
CLAUDE_API_KEY = os.environ.get("CLAUDE_API_KEY")
if not CLAUDE_API_KEY:
    raise RuntimeError("CLAUDE_API_KEY env var not set")
CLAUDE_MODEL = "claude-sonnet-4-6"
```

### 1.3 모델
- **`claude-sonnet-4-6`** (한국어 톤 변환 최상)
- 다음 단계 가서 트래픽 늘면 `claude-haiku-4-5-20251001` 로 다운그레이드 검토

### 1.4 새 클라이언트 필드
안드로이드 측에서 `POST /prepare-reply` 의 JSON body 에 **`ownerToneSamples`** + **P3 일정 필드** 추가됨:

```json
{
  "phone": "01012345678",
  "latestMessage": "...",
  "latestMessageReceivedAtMs": 1716234567000,
  "recentHistory": [...],
  "customer": {
    "name": "홍길동",
    "memo": "...",
    "leadHeat": "HOT",
    "depositPaid": false,
    "scheduledWorkDateMs": 1716988800000   // P3 — 이 고객의 시공 예약일 (있으면)
  },
  "ownerToneSamples": [
    "내일 오전 10시에 방문드리겠습니다.",
    "잔금 입금 부탁드립니다. 계좌는 신한 ...",
    "오늘 시공 마무리됐습니다. 좋은 하루 되세요."
  ],
  "otherUpcomingSchedulesMs": [             // P3 — 사장님 다른 시공 일정 (14일 내, ms 만)
    1716988800000,
    1717248000000
  ]
}
```

→ Pydantic 모델 `PrepareReplyRequest`:
- `ownerToneSamples: list[str] = Field(default_factory=list)`
- `customer.scheduledWorkDateMs: int | None = None`
- `otherUpcomingSchedulesMs: list[int] = Field(default_factory=list)`

### 1.5 P3 — 일정 prompt inject (2026-05-24)

`otherUpcomingSchedulesMs` 가 비어있지 않으면 시스템 프롬프트의 `{SCHEDULE_CONTEXT}` 슬롯에 가공해서 inject:

```
────── 사장님 시공 일정 (앞으로 14일) ──────
- 5/26(수): 다른 시공 1건
- 5/28(금): 다른 시공 1건
- 5/30(일): 비어있음 ← 가능
- 5/31(월): 비어있음 ← 가능
(이 고객 본인 예약: 5/27(목) — customer.scheduledWorkDateMs 가 있을 때만)
```

고객이 일정 질문 → 위 정보 근거로 정확히 답변. 14일 밖 또는 데이터 없는 시점 = "확인 후 안내드릴게요". **추측 금지.**

## 2. 시스템 프롬프트 재작성

기존 (Ollama 시절) `SYSTEM_PROMPT_TEMPLATE` 을 **확장**해서 다음 세 가지를 추가:

### 2.1 사장님 톤 few-shot (가장 중요)

```python
SYSTEM_PROMPT_TEMPLATE = """너는 줄눈 시공 사장님이 고객 문자에 답장할 때 도와주는 비서다.

────── 사장님 톤 학습 (반드시 모방할 것) ──────
다음은 사장님이 평소 고객에게 보낸 실제 메시지들이다.
어휘·문장 길이·반말/존댓말 비율·이모지 사용·인사 방식·문장 끝 처리를
모방해야 한다. 절대 사장님이 안 쓸 법한 단어/문체로 답하지 말 것.

{OWNER_TONE_SAMPLES}

────── 답변 후보 3개의 차별화 (반드시 다른 방향) ──────
세 후보는 명확히 다른 방향성을 가진다:

1번 = 짧은 답변
- 한 문장. 즉답. 사장님이 바쁠 때 그대로 보낼 수 있어야.
- 예: "내일 오전 10시 가능합니다."

2번 = 친절한 답변
- 두 문장. 추가 안내/배려 한 줄 더.
- 예: "내일 오전 10시 방문드리겠습니다. 시공 시 1시간 정도 비워두시면 좋아요."

3번 = 전환 유도 답변
- 두 문장. 다음 단계로 자연스럽게 유도 (사진 요청 / 일정 확정 / 견적 안내 / 입금 안내).
- 예: "내일 가능합니다. 정확한 견적을 위해 시공 부위 사진 한 장만 보내주실 수 있나요?"

세 후보가 비슷비슷하면 실패. 사장님이 상황 따라 골라 쓸 수 있도록 다양해야 함.

────── 가격표 ──────
{PRICING}

────── 기본 규칙 ──────
- 정확히 3개 답변을 JSON 으로 답하라.
- 고객에게 보낼 메시지이므로 존댓말.
- 이모지·따옴표를 임의로 새로 넣지 마라 (사장님 톤 샘플에 있으면 OK).
- 가격·날짜·시간은 대화 또는 가격표에서만 추출. 추측하지 마라.
- 금기어: "급하면" 계열, "싸다" 계열 → 변형 표현도 피하라.

가격 문의 케이스 처리:
- 신축/구축 미확정 → 한 후보는 "신축이세요 구축이세요?" 물어보기.
- 타일 크기 미언급 → 한 후보는 "타일 사진 보내주시면 정확히 견적 드려요" 안내.
- 답변 형식: 표 금지. 항목별 줄바꿈 나열 + 합계.
- 실리콘 제거/셀프줄눈/누수 가능성 = "현장 확인 후 추가될 수 있어요" 한 문장 덧붙임.

답 형식 (반드시 다음 JSON 만):
{
  "suggestions": ["...", "...", "..."]
}
"""
```

### 2.2 `{OWNER_TONE_SAMPLES}` 치환 함수

```python
def format_owner_tone(samples: list[str]) -> str:
    if not samples:
        return "(샘플 없음 — 기본 톤으로 답하되 군더더기 없이)"
    # 너무 짧은/긴 거 제외 + 중복 제거 + 최대 50건
    cleaned = []
    seen = set()
    for s in samples:
        s = s.strip()
        if len(s) < 5 or len(s) > 300:
            continue
        if s in seen:
            continue
        seen.add(s)
        cleaned.append(s)
        if len(cleaned) >= 50:
            break
    return "\n".join(f"- {s}" for s in cleaned)
```

### 2.3 `build_system_prompt` 갱신

```python
def build_system_prompt(owner_tone_samples: list[str]) -> str:
    return (
        SYSTEM_PROMPT_TEMPLATE
        .replace("{PRICING}", load_pricing())
        .replace("{OWNER_TONE_SAMPLES}", format_owner_tone(owner_tone_samples))
    )
```

## 3. Claude API 호출

기존 `call_ollama_for_suggestions` 를 **`call_claude_for_suggestions`** 로 교체:

```python
import anthropic

claude_client = anthropic.Anthropic(api_key=CLAUDE_API_KEY)

async def call_claude_for_suggestions(request: PrepareReplyRequest | SuggestRequest) -> list[str]:
    system_text = build_system_prompt(
        getattr(request, "ownerToneSamples", []) or []
    )

    response = await asyncio.to_thread(
        claude_client.messages.create,
        model=CLAUDE_MODEL,
        max_tokens=800,
        system=[
            {
                "type": "text",
                "text": system_text,
                "cache_control": {"type": "ephemeral"}   # prompt caching = 비용 90% 절약
            }
        ],
        messages=[
            {"role": "user", "content": build_user_payload(request)},
            # JSON 강제용 prefill: assistant 답을 "{" 로 시작하게 prime
            {"role": "assistant", "content": "{"}
        ],
    )
    text = response.content[0].text
    # prefill 때문에 응답이 "{" 없이 시작 → 앞에 붙여서 파싱
    full_json = "{" + text
    return _parse_suggestions(full_json)
```

**Prompt caching 효과**: 시스템 프롬프트 (가격표 + 톤 샘플 50건) 이 길어도 동일 prompt 연속 호출 시 입력 토큰 비용 1/10. 5분 TTL.

## 4. Rate limit (필수)

`server/main.py` 에 새 테이블 + 미들웨어 추가:

```sql
CREATE TABLE IF NOT EXISTS api_usage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    cost_usd REAL NOT NULL DEFAULT 0,
    created_at_ms INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_api_usage_phone_created ON api_usage(phone, created_at_ms);
CREATE INDEX IF NOT EXISTS idx_api_usage_created ON api_usage(created_at_ms);
```

Limit 정책:
```python
DAILY_TOTAL_CALLS_LIMIT = 5000        # 폭주 차단
PER_PHONE_DAILY_LIMIT = 200           # 한 사용자가 가스 다 쓰는 거 방지

def check_rate_limit(phone: str) -> None:
    one_day_ms = 24 * 60 * 60 * 1000
    cutoff = now_ms() - one_day_ms
    with db_conn() as conn:
        total = conn.execute(
            "SELECT COUNT(*) FROM api_usage WHERE created_at_ms > ?", (cutoff,)
        ).fetchone()[0]
        if total >= DAILY_TOTAL_CALLS_LIMIT:
            raise HTTPException(429, "일일 호출 한도 초과")
        per_phone = conn.execute(
            "SELECT COUNT(*) FROM api_usage WHERE phone = ? AND created_at_ms > ?",
            (phone, cutoff)
        ).fetchone()[0]
        if per_phone >= PER_PHONE_DAILY_LIMIT:
            raise HTTPException(429, f"{phone} 일일 호출 한도 초과")
```

호출 직후 usage 기록:
```python
def log_usage(phone: str, endpoint: str, response) -> None:
    usage = response.usage
    # Sonnet 4.6 가격 (per 1M tokens): input $3, cached input $0.30, output $15
    cost = (
        (usage.input_tokens / 1_000_000 * 3.0) +
        (getattr(usage, "cache_read_input_tokens", 0) / 1_000_000 * 0.30) +
        (usage.output_tokens / 1_000_000 * 15.0)
    )
    with db_conn() as conn:
        conn.execute(
            """INSERT INTO api_usage
               (phone, endpoint, input_tokens, output_tokens, cost_usd, created_at_ms)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (phone, endpoint, usage.input_tokens, usage.output_tokens, cost, now_ms())
        )
```

## 5. 새 엔드포인트: GET /admin/usage

폰에서 (또는 사장님이 브라우저로) 사용량 + 비용 확인용.

```python
@app.get("/admin/usage")
async def admin_usage() -> dict:
    one_day_ms = 24 * 60 * 60 * 1000
    cutoff = now_ms() - one_day_ms
    with db_conn() as conn:
        row = conn.execute(
            """SELECT COUNT(*) as calls,
                      COALESCE(SUM(input_tokens), 0) as input_tokens,
                      COALESCE(SUM(output_tokens), 0) as output_tokens,
                      COALESCE(SUM(cost_usd), 0) as cost_usd
               FROM api_usage WHERE created_at_ms > ?""",
            (cutoff,)
        ).fetchone()
    krw_rate = 1380   # 대략. 정확한 환율은 fixed 또는 외부 API
    return {
        "windowHours": 24,
        "calls": row["calls"],
        "inputTokens": row["input_tokens"],
        "outputTokens": row["output_tokens"],
        "costUsd": round(row["cost_usd"], 4),
        "costKrw": round(row["cost_usd"] * krw_rate),
    }
```

## 6. /prepare-reply 갱신

기존 흐름은 유지. `call_ollama_for_suggestions` → `call_claude_for_suggestions` 로 교체하고, rate limit + log_usage 추가:

```python
async def generate_and_store(request: PrepareReplyRequest) -> None:
    try:
        check_rate_limit(request.phone)   # 추가
        suggestions, response = await call_claude_for_suggestions_with_meta(request)
        log_usage(request.phone, "prepare-reply", response)   # 추가
        upsert_cache_row(
            phone=request.phone,
            status="ready",
            based_on_message=request.latestMessage,
            based_on_received_at_ms=request.latestMessageReceivedAtMs,
            generated_at_ms=now_ms(),
            suggestions=suggestions,
        )
    except asyncio.CancelledError:
        raise
    except HTTPException as exc:
        # rate limit 초과 같은 거 → missing 처리. 폰에서 ↻ 누르면 재시도
        upsert_cache_row(
            phone=request.phone,
            status="missing",
            based_on_message=request.latestMessage,
            based_on_received_at_ms=request.latestMessageReceivedAtMs,
            generated_at_ms=None,
            suggestions=None,
        )
    except Exception:
        upsert_cache_row(
            phone=request.phone,
            status="missing",
            based_on_message=request.latestMessage,
            based_on_received_at_ms=request.latestMessageReceivedAtMs,
            generated_at_ms=None,
            suggestions=None,
        )
    finally:
        existing = _tasks_by_phone.get(request.phone)
        if existing is asyncio.current_task():
            _tasks_by_phone.pop(request.phone, None)
```

`call_claude_for_suggestions_with_meta` = response 객체까지 반환하는 버전:
```python
async def call_claude_for_suggestions_with_meta(request) -> tuple[list[str], anthropic.types.Message]:
    # ... 위 §3 코드와 동일하되 response 도 같이 반환
```

## 7. /api/intent/classify 와 /api/reply/suggest 도 동일 패턴 적용

기존 `call_ollama_json` → Claude 버전 만들기. 같은 client + 같은 prompt caching + 같은 rate limit + log_usage. AI 문자함 (사장님이 안 쓰는 화면) 에서 호출되는 엔드포인트들이지만 일관성 위해.

## 8. 검증 시나리오

### 8.1 가격 문의 (가격표 동작 확인)
```bash
curl -X POST http://localhost:8000/prepare-reply \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "01099999991",
    "latestMessage": "신축인데 욕조 있는 화장실 바닥 줄눈 견적 얼마예요?",
    "latestMessageReceivedAtMs": 1716234567000,
    "recentHistory": [],
    "customer": null,
    "ownerToneSamples": []
  }'

sleep 5
curl http://localhost:8000/suggestions/01099999991
```

기대: 후보 3개 중 적어도 하나에 **"40만원"** 포함. 또 다른 후보는 **타일 크기 사진 요청** 안내.

### 8.2 톤 코퍼스 동작 확인
```bash
curl -X POST http://localhost:8000/prepare-reply \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "01099999992",
    "latestMessage": "내일 가능하세요?",
    "latestMessageReceivedAtMs": 1716234567000,
    "recentHistory": [],
    "customer": null,
    "ownerToneSamples": [
      "내일 오전 10시에 가요",
      "주소 알려주세요",
      "잔금 부탁드립니다"
    ]
  }'

sleep 5
curl http://localhost:8000/suggestions/01099999992
```

기대: 답변이 위 톤 샘플과 비슷한 길이/어휘 (반말 안 쓰지만 짧고 군더더기 X). 사장님이 보고 "내가 쓴 거 같다" 라고 느껴야 통과.

### 8.3 답변 차별화 확인
```bash
# 같은 요청 다시
curl http://localhost:8000/suggestions/01099999992
```

세 후보가 **서로 명확히 다른 방향성** 이어야:
- 1번 = 짧음 ("내일 오전 가능합니다." 등)
- 2번 = 친절 (한 줄 더 추가)
- 3번 = 전환 유도 (사진 요청 / 일정 확정 등)

비슷비슷하면 시스템 프롬프트 §2.1 차별화 가이드를 강화 (예시 추가).

### 8.4 사용량 확인
```bash
curl http://localhost:8000/admin/usage
# → {"calls": 3, "inputTokens": 6500, "outputTokens": 480, "costUsd": 0.027, "costKrw": 37}
```

### 8.5 Rate limit 확인 (선택)
같은 phone 으로 200회 빠르게 호출 → 201번째에 429 응답이 와야 정상.

## 9. 마이그레이션 절차 (안전 우선)

1. 새 `main.py` 에 위 변경 적용. 단 **OLLAMA 코드는 일단 보존** (주석 처리).
2. `requirements.txt` 갱신 + `pip install -r requirements.txt`
3. launchd plist 에 `CLAUDE_API_KEY` 추가 + 서비스 reload (`launchctl unload` + `launchctl load`)
4. §8.1 ~ §8.4 검증 통과 확인
5. 통과하면 Ollama 관련 코드 제거 (또는 fallback 으로 보존: Claude 실패 시 Ollama). 단순화 위해 제거 권장.

## 10. 비용 예상

[[reference-llm-provider-cost]] (Claude Code 메모) 참조. 요약:
- 사장님 혼자 사용 (일 50회) = 월 **~27,000원**
- 베타 30명 (일 1,500회) = 월 **~81만원**
- 1,000명 (일 50,000회) = 월 **~2,700만원** → 이 단계 가면 Haiku 4.5 로 다운그레이드 검토 ($1/$5)

Prompt caching 적용 시 위 비용의 **약 절반** 으로 떨어짐 (가격표 + 톤 샘플이 거의 매 호출 동일하므로).

## 11. 안드로이드 측 (참고용 — 이미 작업 완료)

이 사양서가 가리키는 클라이언트 변경은 이미 push 됨:

- [`ai/ReplySuggestions.kt`](app/src/main/java/com/detailline/callfollowcrm/ai/ReplySuggestions.kt) — `PrepareContext.ownerToneSamples` 필드 추가
- [`ai/ServerSuggestionRepository.kt`](app/src/main/java/com/detailline/callfollowcrm/ai/ServerSuggestionRepository.kt) — JSON 본문에 `ownerToneSamples` 직렬화
- [`service/SmsReceiver.kt`](app/src/main/java/com/detailline/callfollowcrm/service/SmsReceiver.kt) — SMS 수신 시 `SmsRepository.querySentMessages(50)` 호출 → 톤 샘플 전송
- [`presentation/screen/chat/ChatViewModel.kt`](app/src/main/java/com/detailline/callfollowcrm/presentation/screen/chat/ChatViewModel.kt) — `regenerateSuggestions()` 도 동일 톤 샘플 첨부
- [`data/repository/SmsRepository.kt`](app/src/main/java/com/detailline/callfollowcrm/data/repository/SmsRepository.kt) — `querySentMessages(limit: Int)` 헬퍼 추가

서버만 이 사양대로 업그레이드하면 즉시 동작.

## 12. 첫 작업 순서 (다른 클로드용)

1. `requirements.txt` 에 `anthropic` 추가 + `pip install`
2. launchd plist 에 `CLAUDE_API_KEY` env 추가
3. `main.py` §1.4 (Pydantic) + §2 (시스템 프롬프트) + §3 (Claude 호출) + §4~§5 (rate limit + usage) + §6 (/prepare-reply) + §7 (다른 엔드포인트)
4. `launchctl unload` + `launchctl load`
5. §8 검증 차례차례. 통과 못 하면 그 섹션의 가이드 강화

작업이 끝나면 사장님에게:
- `GET /admin/usage` 결과 한 번 보여드리기
- §8.2 톤 샘플 동작 결과를 사장님에게 평가받기 ("내 톤 같나?")
