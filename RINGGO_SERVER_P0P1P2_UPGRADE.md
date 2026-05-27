# RING-GO 서버 P0+P1+P2 업그레이드 사양서

> **목적**: 에이닷 수준의 "AI 세일즈 운영 OS" 로 진화. 안드로이드 UI 는 이미 P0+P1+P2 UI 가 박혀있고, 데이터가 비어있을 뿐. 이 사양서대로 서버에 endpoint 3개 추가하면 자동으로 채워짐.
>
> **선행**: `RINGGO_SERVER_PHASE1_UPGRADE.md` (Claude Sonnet 4.6 교체) 이미 완료 가정.

---

## 0. 개요

### 추가할 endpoint 3개
| Endpoint | 입력 | 출력 | 호출 시점 |
|----------|------|------|-----------|
| `POST /api/card-summary` | 그 사람의 최근 메시지 + 통화 요약 | 한 줄 요약 (15~25자) | HomeScreen 카드 표시 시 |
| `POST /api/conversation-summary` | 같은 입력 | 3-5줄 상세 요약 + 단계별 진행 상황 | ChatScreen 진입 시 |
| `POST /api/next-action-suggest` | 같은 입력 | 다음 액션 1개 + 추천 일정/액션 | ChatScreen 진입 시, 또는 후속 처리 시 |

### 통일 원칙
- 세 endpoint 모두 **같은 입력 schema** (재사용성)
- prompt caching 으로 비용 절약 — system prompt + 사장님 톤 코퍼스는 캐시
- 결과는 안드로이드 측 Room 캐시. 새 메시지/통화 들어왔을 때만 재호출.

---

## 1. 공통 입력 schema

```python
class ConversationContext(BaseModel):
    phone: str                           # "010-4805-2630"
    customer_name: str | None            # 등록된 이름 (없으면 null)
    customer_status: str | None          # "신규 문의" / "견적 발송" 등
    customer_memo: str | None            # 사장님이 적은 메모
    lead_heat: str | None                # "HOT" / "WARM" / "COLD" / null
    deposit_paid: bool                   # 계약금 받았나
    scheduled_work_date: int | None      # 시공 예약 epoch ms
    
    recent_messages: list[Message]       # 최근 20건 (SMS + MMS)
    call_summaries: list[CallSummary]    # 최근 통화 요약 (있으면)
    
    owner_tone_samples: list[str]        # 사장님 톤 학습용 (50건, 기존과 동일)
    
    # P3 (2026-05-24 추가) — 사장님의 다른 시공 일정 (현재 고객 제외, 14일 내).
    # 다른 고객 이름은 leak 금지 → ms 만. 서버가 요일/오전오후로 가공해서 prompt 에 inject.
    # 비어있으면 빈 리스트 (모든 endpoint).
    other_upcoming_schedules_ms: list[int] = []

class Message(BaseModel):
    role: str       # "owner" or "customer"
    body: str
    timestamp_ms: int

class CallSummary(BaseModel):
    summary: str    # 통화 요약 (에이닷에서 받은 것)
    duration_sec: int
    started_at_ms: int
    direction: str  # "incoming" / "outgoing" / "missed"
```

---

## 2. `POST /api/card-summary`

### 목적
HomeScreen 카드에 표시할 **한 줄 요약** 생성. 사장님이 카드만 봐도 "아 이 사람" 즉판.

### 출력
```json
{
  "summary": "타일 견적 문의 + 5/26 시공 의향",
  "generated_at_ms": 1234567890,
  "based_on_message_count": 12
}
```

### 제약
- **15~25 한국어 글자** (카드 한 줄에 들어가야 함)
- **사실 기반** — 명시되지 않은 정보 추측 X
- **이모지 사용 OK** (시각적 분류)

### System prompt 예시
```
너는 1인 시공자(줄눈/타일) 사장님의 비서다. 고객과 주고받은 메시지를 보고
사장님이 카드 한 줄만 봐도 "아, 이 사람" 알 수 있게 요약하라.

규칙:
- 정확히 15~25자
- 다음 정보 우선순위: (1) 현재 단계 (문의/견적/예약/시공) (2) 핵심 키워드 (3) 다음 일정
- 명시되지 않은 정보 추측 금지
- 가격은 사장님이 명시한 것만, 추측 금지

예시:
- "타일 견적 문의 + 5/26 시공 의향"
- "줄눈 사진 보냄, 답변 대기"
- "계약금 받음, 5/30 시공 확정"
- "AS 문의, 시공 후 3개월"
```

---

## 3. `POST /api/conversation-summary`

### 목적
ChatScreen 진입 시 상단에 표시할 **3-5줄 상세 요약**. 사장님이 ChatScreen 진입 즉시 "그래 이 사람 이런 흐름이었지" 파악.

### 출력
```json
{
  "summary_lines": [
    "📍 마곡 신축 아파트, 거실 + 안방 줄눈",
    "💰 1m당 X원 안내, 총 견적 미발송",
    "📅 5/26 화요일 시공 가능 여부 확인 중",
    "📷 5/22 사진 받음, 사장님이 답변 안 함"
  ],
  "current_stage": "estimate_pending",
  "generated_at_ms": 1234567890
}
```

### 제약
- 3-5줄
- 각 줄 앞에 이모지 1개 (시각 분류)
- **사장님이 빠뜨린 것** 명시 (예: "사장님이 답변 안 함")
- 가격/일정은 명시된 것만

### `current_stage` enum
- `inquiry`: 신규 문의
- `photo_pending`: 사진 요청/대기
- `estimate_pending`: 견적 작성 중
- `estimate_sent`: 견적 발송 완료
- `schedule_pending`: 일정 조율
- `deposit_pending`: 계약금 대기
- `scheduled`: 시공 확정
- `done`: 시공 완료
- `as_needed`: AS 필요

이 stage 는 P2 의 자동 분류와도 연동 (CustomerEntity.status 자동 업데이트).

---

## 4. `POST /api/next-action-suggest`

### 목적
"AI 제안" 박스 — 사장님이 다음에 무엇을 해야 하는지 한 줄로 제안 + 1탭으로 실행.

### 출력
```json
{
  "action_type": "send_estimate",
  "title": "견적 보내기",
  "subtitle": "사장님이 본 사진 기준 견적 작성",
  "primary_action": {
    "label": "견적 작성하기",
    "action": "open_template_estimate"
  },
  "secondary_action": null,
  "urgency": "high"
}
```

### P3 — 일정 후보 추천 (2026-05-24 추가)

`other_upcoming_schedules_ms` 가 비어있지 않으면 prompt 에 사장님 일정 요약 inject:

```
사장님 시공 일정 (앞으로 14일, 다른 고객 포함):
- 5/26(수): 예약 1건
- 5/28(금): 예약 1건
- 5/30(일): 비어있음
- 5/31(월): 비어있음
- 6/1(화): 예약 1건
...
```

고객이 "토요일 가능하세요?" / "이번 주 언제 되세요?" 같은 일정 질문 → 위 정보 근거로 정확히 답변:
- 가능한 날 = 비어있는 날 (다른 시공 X)
- 불가능한 날 = 예약 있는 날 ("그날은 어렵고 ○일 어떠세요" 제안)
- **추측 금지** — 데이터 없는 미래 (14일 밖) 는 "확인 후 안내드릴게요"

`scheduled_work_date` (현재 고객) 가 설정되어 있으면 이미 확정된 일정으로 처리. 변경 의향 표현 시 next-action-suggest 가 `register_schedule` 으로 다시 트리거.

---

### action_type enum + 매칭 시나리오
| action_type | 시나리오 | primary action |
|-------------|----------|----------------|
| `send_estimate` | 사진 받음, 견적 안 보냄 | "견적 작성" → 템플릿 화면 |
| `confirm_schedule` | 견적 보냄, 일정 미확정 | "일정 협의" → 답변 추천 |
| `request_deposit` | 일정 확정, 계약금 미입금 | "계약금 안내" → 템플릿 |
| `register_schedule` | 일정 합의됨, 캘린더 미등록 | "시공일 등록" → DatePicker |
| `send_followup` | 시공 후 N일, 후기 미요청 | "후기 요청" → 템플릿 |
| `none` | 사장님이 답변할 것 없음 | (박스 안 보임) |

### urgency
- `high`: 24시간 내 처리 권장 (빨강 강조)
- `medium`: 1-3일 내 (노랑)
- `low`: 여유 (파랑)
- `none`: 제안 없음

---

## 5. 캐시 정책 (서버 측)

세 endpoint 모두 동일:
- 캐시 key = `phone` + `latest_message_timestamp_ms`
- 같은 phone + 같은 timestamp 면 캐시 반환 (Claude API 호출 안 함)
- 새 메시지 들어오면 자동 invalidation

SQLite 한 테이블로 충분:
```sql
CREATE TABLE summary_cache (
  phone TEXT NOT NULL,
  endpoint TEXT NOT NULL,
  latest_msg_ts INTEGER NOT NULL,
  response_json TEXT NOT NULL,
  generated_at INTEGER NOT NULL,
  PRIMARY KEY (phone, endpoint, latest_msg_ts)
);
```

---

## 6. 안드로이드 측 호출 패턴

### 6.1 HomeScreen 카드
```kotlin
// HomeViewModel — 가시 카드 prefetcher 와 같은 패턴
fun onVisibleCards(phones: List<String>) {
    // 1) Room 캐시에서 즉시 표시
    // 2) 백그라운드로 서버 /api/card-summary 호출
    // 3) 결과를 Room 캐시 + UI emit
}
```

### 6.2 ChatScreen 진입
```kotlin
fun loadConversationSummary(phone: String) {
    // 1) Room 캐시 즉시
    // 2) 서버 /api/conversation-summary 백그라운드
}

fun loadNextActionSuggestion(phone: String) {
    // 1) Room 캐시 즉시
    // 2) 서버 /api/next-action-suggest 백그라운드
}
```

### 6.3 호출 트리거
- HomeScreen 가시 카드: 카드 표시 시 (debounce 60초)
- ChatScreen 진입: LaunchedEffect(Unit)
- SmsReceiver: 새 메시지 받으면 그 phone 의 모든 캐시 invalidate + prefetch

---

## 7. 비용 시뮬

사장님 패턴 = 하루 100 고객, 평균 10 메시지/고객.

| 시나리오 | 일 호출 수 | 비용 (Sonnet 4.6, prompt caching ON) |
|----------|-----------|---------------------------------------|
| card-summary (캐시 hit 80%) | 100 * 0.2 = 20 | ~$0.02/day |
| conversation-summary (캐시 hit 90%) | 100 * 0.1 = 10 | ~$0.03/day |
| next-action-suggest (캐시 hit 90%) | 100 * 0.1 = 10 | ~$0.03/day |
| **합계** | | **~$0.08/day = ~3,000원/월** |

[[reference-llm-provider-cost]] 기준. prompt caching 없으면 5배.

---

## 8. 검증 시나리오

### 8.1 card-summary
- 입력: 사장님이 본 적 있는 고객의 메시지
- 기대: 15-25자 한국어, 사장님이 보고 "아 그 사람" 즉판
- 실패: 추측, 가격 잘못, 너무 짧음/길음

### 8.2 conversation-summary
- 입력: 동일
- 기대: 3-5줄, 각 줄 이모지, 명시된 정보만, current_stage 적절
- 실패: 추측 정보, stage 잘못

### 8.3 next-action-suggest
- 시나리오 A: "고객이 어제 사진 보냈는데 답 안 함" → `send_estimate` + urgency=high
- 시나리오 B: "어제 견적 보냄" → `confirm_schedule` + urgency=medium
- 시나리오 C: "이미 시공 완료, 1주일 됨" → `send_followup` (후기 요청)

---

## 9. 마이그레이션 절차

1. `server/main.py` 에 endpoint 3개 추가
2. `summary_cache` 테이블 자동 생성 (앱 시작 시 CREATE IF NOT EXISTS)
3. Claude API 시스템 프롬프트 세 종류 (각 endpoint 별) 작성
4. `GET /admin/usage` 에 endpoint 별 호출 수 추가
5. 검증 시나리오 §8 통과 후 안드로이드 배포

---

## 10. 추후 (P3+)

이 사양서는 P0+P1+P2 까지만. 추후:
- **P3** = 견적/일정/계약 자동 발송 — `POST /api/auto-action` (사장님 승인 후 실행)
- **P4** = 통화 후속 자동화 — 에이닷 통화 요약 받아서 자동 분류
- **P5** = 후기/AS/재구매 자동 트래킹

---

## 11. 카테고리 자동 분류 endpoint (다음 세션 서버 작업)

### 11.1 배경

2026-05-25: 사장님 결정 — CustomerStatus enum (신규문의/견적대기/...) 완전 폐기.
갤메시지 식 사장님 정의 카테고리로 통일. 사장님이 + 버튼으로 임의 카테고리 생성
("마곡 신축", "5월 시공", "AS 건" 등) → AI 가 대화 보고 자동으로 분류.

현재 안드로이드 측에 클라이언트 휴리스틱 (단순 substring 매칭, `CategoryAutoClassifier`)
박혀있지만 정확도 낮음. 서버 LLM 분류 필요.

### 11.2 endpoint: `POST /api/category-classify`

**입력**
```json
{
  "phone": "010-4805-2630",
  "recent_messages": [...],
  "candidate_categories": [
    {"id": 1, "name": "마곡 신축"},
    {"id": 2, "name": "5월 시공"},
    {"id": 3, "name": "AS 건"}
  ],
  "owner_tone_samples": [...]
}
```

**출력**
```json
{
  "matched_category_id": 1,
  "confidence": 0.85,
  "reason": "마곡 지역 + 신축 아파트 키워드 명시"
}
```

- `matched_category_id`: 가장 잘 맞는 카테고리. 해당 없으면 `null`.
- `confidence`: 0.0~1.0. 0.6 미만이면 안드로이드 측에서 적용 안 함 (false positive 방지).
- `reason`: 디버그용. UI 표시 X.

### 11.3 System prompt 예시

```
너는 1인 시공자 사장님의 비서다. 고객과 주고받은 메시지를 보고
사장님이 만든 카테고리 중 가장 잘 맞는 것 1개를 골라라.

규칙:
- 카테고리 이름의 의도를 추론해서 분류 (단순 substring X)
  예: "마곡 신축" = 마곡 지역 + 신축 아파트. 메시지에 "마곡" "신축" "새 아파트" 등 등장하면 매칭.
  예: "AS 건" = 시공 후 문제 제기. "물 새요" "타일 깨졌어요" 같은 표현이면 매칭.
- 확신 없으면 matched_category_id=null. 사장님이 직접 분류.
- confidence 는 추측 정도 (실제 매칭 빈도 + 키워드 명확성).
```

### 11.4 호출 시점

안드로이드 측은 `ChatViewModel.loadFullSummary` 안에서 conversation-summary 호출 후
같은 컨텍스트로 추가 호출. 사장님이 이미 분류한 고객은 절대 호출하지 않음 (의지 보존).

### 11.5 캐시

같은 phone + 같은 latest_message_ts + 같은 카테고리 목록 hash → 캐시.
카테고리가 추가/삭제되면 그 phone 의 캐시 invalidate (조건 매칭이 달라짐).

### 11.6 비용 예상

- 짧은 응답 (id + 짧은 reason)이라 토큰 적게. ~$0.01/day 추가 (캐시 hit 80% 가정).
- prompt caching: system prompt + 카테고리 목록은 캐시 가능.

---

## 12. `GET /api/usage-stats` — 토큰 사용량 모니터링 (2026-05-27 추가)

### 목적
사장님이 앱 Settings 에서 "오늘 / 이번 달 / 전체" 토큰 사용량 + 비용 확인. 진짜 토큰 낭비가 어디서 발생하는지 파악 → 최적화 우선순위 결정.

### 입력
```
GET /api/usage-stats?period=today|month|all
```

### 출력
```json
{
  "period": "today",
  "from_ms": 1717000000000,
  "to_ms": 1717086399000,
  "by_endpoint": [
    {
      "endpoint": "/api/prepare-reply",
      "calls": 12,
      "input_tokens": 48000,
      "output_tokens": 2400,
      "cache_read_tokens": 36000,
      "cache_create_tokens": 12000,
      "cost_krw": 230
    },
    {
      "endpoint": "/api/card-summary",
      "calls": 8,
      "input_tokens": 16000,
      "output_tokens": 400,
      "cache_read_tokens": 14000,
      "cache_create_tokens": 2000,
      "cost_krw": 45
    }
  ],
  "total": {
    "calls": 24,
    "input_tokens": 80000,
    "output_tokens": 3500,
    "cache_read_tokens": 60000,
    "cache_create_tokens": 20000,
    "cost_krw": 350
  }
}
```

### 구현 (서버 측)

#### 12.1 SQLite 로그 테이블
```sql
CREATE TABLE IF NOT EXISTS llm_usage_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp_ms INTEGER NOT NULL,
    endpoint TEXT NOT NULL,
    model TEXT NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    cache_read_tokens INTEGER NOT NULL DEFAULT 0,
    cache_create_tokens INTEGER NOT NULL DEFAULT 0,
    cost_krw INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_usage_timestamp ON llm_usage_log(timestamp_ms);
CREATE INDEX IF NOT EXISTS idx_usage_endpoint ON llm_usage_log(endpoint);
```

#### 12.2 LLM 호출 직후 로그 기록
```python
def log_llm_usage(endpoint: str, model: str, response):
    usage = response.usage
    input_t = usage.input_tokens or 0
    output_t = usage.output_tokens or 0
    cache_read = getattr(usage, "cache_read_input_tokens", 0) or 0
    cache_create = getattr(usage, "cache_creation_input_tokens", 0) or 0
    cost = calculate_cost_krw(model, input_t, output_t, cache_read, cache_create)
    db.execute("""
        INSERT INTO llm_usage_log
        (timestamp_ms, endpoint, model, input_tokens, output_tokens,
         cache_read_tokens, cache_create_tokens, cost_krw)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, (int(time.time() * 1000), endpoint, model,
          input_t, output_t, cache_read, cache_create, cost))
    db.commit()
```

#### 12.3 비용 계산 (2026-05-27 기준, 환율 ₩1380/USD)

**중요**: Anthropic API 의 `response.model` 은 보통 정식 ID (예: `claude-haiku-4-5-20251001`) 로 옴. 단축형 dict 키랑 `dict.get()` 으로만 매칭하면 fail → cost=0 박혀 모니터링 무용. **prefix 매칭** 필수.

**현재 서버가 어떤 모델 쓰는지 모르겠으면** — 코드에서 `CLAUDE_MODEL` 또는 `messages.create(model=...)` grep. 사장님이 Sonnet → Haiku 전환했을 수 있음 (2026-05-26 메모 참고). 첫 호출 후 `response.model` 출력 보고 dict 에 해당 키 있는지 검증. 없으면 추가.

```python
PRICING_USD_PER_1M = {
    "claude-sonnet-4-6": {"input": 3.0, "output": 15.0, "cache_read": 0.3, "cache_create": 3.75},
    "claude-haiku-4-5":  {"input": 1.0, "output": 5.0,  "cache_read": 0.1, "cache_create": 1.25},
    "claude-opus-4-7":   {"input": 15.0, "output": 75.0, "cache_read": 1.5, "cache_create": 18.75},
}
FX_KRW_PER_USD = 1380

def _resolve_pricing(model: str) -> dict | None:
    """'claude-haiku-4-5-20251001' 같은 정식 ID 도 prefix 로 매칭.
       긴 키 우선 (sonnet-4-6 vs sonnet-4 충돌 방지)."""
    for key in sorted(PRICING_USD_PER_1M.keys(), key=len, reverse=True):
        if model.startswith(key):
            return PRICING_USD_PER_1M[key]
    return None

def calculate_cost_krw(model: str, input_t: int, output_t: int,
                       cache_read: int, cache_create: int) -> int:
    p = _resolve_pricing(model)
    if not p:
        # 모르는 모델 — log 박아 사장님이 dict 업데이트 가능하게.
        print(f"[usage] unknown model for pricing: {model}", flush=True)
        return 0
    usd = (
        input_t * p["input"] +
        output_t * p["output"] +
        cache_read * p["cache_read"] +
        cache_create * p["cache_create"]
    ) / 1_000_000
    return int(usd * FX_KRW_PER_USD)
```

#### 12.4 endpoint 구현
```python
@app.get("/api/usage-stats")
def usage_stats(period: str = "today"):
    now_ms = int(time.time() * 1000)
    from_ms = compute_from_ms(period, now_ms)  # today=오늘 00:00, month=이번 달 1일, all=0
    rows = db.execute("""
        SELECT endpoint,
               COUNT(*) AS calls,
               SUM(input_tokens) AS input_t,
               SUM(output_tokens) AS output_t,
               SUM(cache_read_tokens) AS cache_read_t,
               SUM(cache_create_tokens) AS cache_create_t,
               SUM(cost_krw) AS cost_krw
        FROM llm_usage_log
        WHERE timestamp_ms >= ?
        GROUP BY endpoint
        ORDER BY cost_krw DESC
    """, (from_ms,)).fetchall()
    by_endpoint = [
        {"endpoint": r[0], "calls": r[1], "input_tokens": r[2],
         "output_tokens": r[3], "cache_read_tokens": r[4],
         "cache_create_tokens": r[5], "cost_krw": r[6]}
        for r in rows
    ]
    total = {
        "calls": sum(r["calls"] for r in by_endpoint),
        "input_tokens": sum(r["input_tokens"] for r in by_endpoint),
        "output_tokens": sum(r["output_tokens"] for r in by_endpoint),
        "cache_read_tokens": sum(r["cache_read_tokens"] for r in by_endpoint),
        "cache_create_tokens": sum(r["cache_create_tokens"] for r in by_endpoint),
        "cost_krw": sum(r["cost_krw"] for r in by_endpoint),
    }
    return {
        "period": period,
        "from_ms": from_ms,
        "to_ms": now_ms,
        "by_endpoint": by_endpoint,
        "total": total,
    }
```

### 클라이언트 (이미 작업됨 — 2026-05-27)
- `UsageStatsRepository.fetch(period)` → 위 JSON 받음
- `SettingsScreen` 의 "📊 토큰 사용량" 카드에 표시
- 서버 endpoint 없으면 (404) graceful fallback — "서버 모니터링 미구현" 표시

### 12.5 작업 가이드 (맥미니 Claude 용)

**기존 서버 코드 통합 지점** — 코드 grep 으로 찾아서 박기:
- **LLM 호출 함수**: `call_claude_for_suggestions`, `call_claude_for_card_summary`, `call_claude_for_conversation_summary`, `call_claude_for_next_action`, `call_claude_for_category` 등. 각 함수의 `client.messages.create(...)` 직후 `log_llm_usage(endpoint, model, response)` 한 줄 박기.
- **DB 초기화 함수**: `init_db()` 또는 startup hook — `CREATE TABLE IF NOT EXISTS llm_usage_log` 추가.
- **endpoint 등록**: FastAPI `@app.get("/api/usage-stats")` 새 함수 추가. 기존 endpoint 파일 (보통 `main.py` 또는 `server.py`) 안.

**비용 계산 함수** (`calculate_cost_krw`): 모듈 최상단 또는 별도 `pricing.py` 에 배치. 모델명 분기 (Sonnet/Haiku/Opus). 환율은 상수 `FX_KRW_PER_USD = 1380`.

**검증 (4단계)**:
1. 서버 재시작 후 `curl 'http://localhost:8000/api/usage-stats?period=all'` → 빈 stats JSON 응답
2. 다른 endpoint 호출 (`curl -X POST http://localhost:8000/api/prepare-reply ...`) → 1번 다시 호출 → `total.calls` 증가 + 토큰/비용 박힘
3. `period=today` / `period=month` / `period=all` 응답 비교 — 다 정상 JSON
4. **회귀**: 기존 endpoint (`/api/prepare-reply`, `/api/card-summary`, `/api/conversation-summary`, `/api/next-action-suggest`, `/api/category-classify`) 들이 여전히 정상 응답하는지 confirm

**완료 조건**:
- 위 4단계 검증 통과
- `git add . && git commit -m "feat: §12 usage-stats endpoint" && git push`
- 사장님이 RING-GO 앱 Settings 새로고침 → 토큰 사용량 카드에 실측 데이터 표시되면 끝

---

## 13. `POST /api/address-resolve` — 아파트명 → 풀 주소 (2026-05-27 추가)

### 목적
고객이 메시지에 "마곡엠밸리 8단지" 같은 **아파트명만** 보낸 경우, RING-GO 가 자동으로 풀 주소를 찾아 CustomerDetail 의 📍 주소 카드에 표시. 시공자가 가는 길 / 견적 산정에 정확한 위치 정보 활용.

### 사장님 사전 작업 (1회)
1. https://developers.kakao.com 가입
2. 애플리케이션 생성 → "Local API" 활성화
3. REST API 키 발급
4. 맥미니 서버 환경변수에 `KAKAO_REST_API_KEY=...` 추가

### 입력
```json
{
  "candidate_keywords": ["마곡엠밸리 8단지"],
  "context_text": "안녕하세요 마곡엠밸리 8단지 304동 입니다"
}
```
- `candidate_keywords`: 클라이언트가 정규식으로 추출한 아파트명 후보들 (1-3개)
- `context_text`: 원본 메시지 (LLM fallback 용 — 후보 정규식 실패 시)

### 출력
```json
{
  "resolved": "서울 강서구 마곡동 740 마곡엠밸리8단지",
  "road_address": "서울 강서구 마곡중앙8로 60",
  "place_name": "마곡엠밸리 8단지",
  "lat": 37.5615,
  "lng": 126.8278,
  "confidence": 0.9
}
```
- `resolved`: 표시용 메인 주소 (지번 기준)
- `road_address`: 도로명 주소 (네비 용)
- `confidence`: 0.0 ~ 1.0. 0.6 미만이면 클라이언트가 표시 안 함 권장

resolve 실패 시:
```json
{"resolved": null, "confidence": 0.0}
```

### 구현 (서버 측)

#### 13.1 카카오 로컬 API 호출
```python
import httpx

KAKAO_KEY = os.environ["KAKAO_REST_API_KEY"]

async def search_kakao_local(query: str) -> dict | None:
    url = "https://dapi.kakao.com/v2/local/search/keyword.json"
    headers = {"Authorization": f"KakaoAK {KAKAO_KEY}"}
    params = {"query": query, "category_group_code": "AP1", "size": 5}  # AP1 = 아파트
    async with httpx.AsyncClient(timeout=5.0) as client:
        resp = await client.get(url, headers=headers, params=params)
        if resp.status_code != 200:
            return None
        docs = resp.json().get("documents", [])
        # 첫 번째 결과가 가장 score 높음 (카카오 정렬 기준)
        return docs[0] if docs else None
```

#### 13.2 endpoint
```python
@app.post("/api/address-resolve")
async def address_resolve(req: AddressResolveRequest):
    # 후보 keyword 별 카카오 검색 → 첫 hit 사용
    for kw in req.candidate_keywords or []:
        hit = await search_kakao_local(kw)
        if hit:
            log_llm_usage(  # 카카오는 LLM 아니지만 호출 카운트 위해 같이 누적
                endpoint="/api/address-resolve",
                model="kakao-local",
                response=DummyUsage(input_tokens=0, output_tokens=0)
            )
            return {
                "resolved": hit.get("address_name"),
                "road_address": hit.get("road_address_name"),
                "place_name": hit.get("place_name"),
                "lat": float(hit.get("y", 0)),
                "lng": float(hit.get("x", 0)),
                "confidence": 0.9
            }
    # 모든 후보 실패 + context_text 가 있으면 LLM fallback (간단 prompt)
    if req.context_text:
        # Claude 한테 "이 메시지에서 아파트명 + 추정 주소 추출" — 1번만 시도
        # ... LLM 호출 ...
        # confidence 0.6 정도로 마킹
        pass
    return {"resolved": None, "confidence": 0.0}
```

#### 13.3 비용
- 카카오 로컬 API: **무료** (일 30만 호출). 시공자 1명 = 일 5-30 호출 → 충분
- LLM fallback 만 토큰 — 거의 드물게 사용

### 클라이언트 측 (안드로이드)

#### 13.4 AddressExtractor 보강
기존 [AddressExtractor.kt](app/src/main/java/com/detailline/callfollowcrm/util/AddressExtractor.kt) 의 3개 정규식 다음에 **아파트명 후보** 추출 추가:
```kotlin
// 아파트명 정규식 후보: 단어 + (아파트|단지|타워|뷰|파크|힐|마을|자이|푸르지오|래미안|이편한세상|디에이치)
private val APT_NAME_PATTERN = Regex(
    "([가-힣A-Za-z0-9]+(?:\s*[가-힣A-Za-z0-9]+){0,3})\s*(아파트|단지|타워|자이|푸르지오|래미안|이편한세상|디에이치|롯데캐슬|힐스테이트|아이파크|sk뷰|sk view)"
)

fun extractApartmentCandidates(text: String): List<String> {
    return APT_NAME_PATTERN.findAll(text).map { it.value.trim() }.distinct().take(3).toList()
}
```

#### 13.5 ChatViewModel 또는 CustomerDetailViewModel
- 일반 주소 정규식 못 잡고 아파트명 후보가 있으면 → `POST /api/address-resolve` 호출
- 결과 `resolved` 가 있으면 `extractedAddress` 상태에 표시
- 같은 아파트명은 메모리 캐시 (앱 세션 동안 재호출 X)

### 검증 (4단계)
1. `curl -X POST http://localhost:8000/api/address-resolve -H "Content-Type: application/json" -d '{"candidate_keywords":["마곡엠밸리 8단지"]}'` → `resolved: "서울 강서구 마곡동 ..."` 응답
2. 카카오 API 키 없을 때 → `resolved: null` 반환 (500 에러 X)
3. 안드로이드 앱에서 메시지 "마곡엠밸리 8단지 가격 문의" → CustomerDetail 의 📍 주소 카드에 풀주소 자동 표시
4. 회귀: 기존 일반 주소 정규식 ("서울시 강남구 ...") 케이스 여전히 동작

### 작업 가이드 (맥미니 Claude 용)

**통합 지점:**
- 환경변수 추가: `.env` 또는 docker-compose 에 `KAKAO_REST_API_KEY` 박기
- 의존성: `httpx` 가 이미 있으면 재사용. 없으면 `pip install httpx`
- endpoint 등록: 기존 FastAPI `@app.post` 옆에 추가
- log_llm_usage 호출 — endpoint 카운트 모니터링 가능하게

**완료 조건:**
- 검증 4단계 통과
- 사장님 카카오 키 박은 상태에서 실제 아파트명 케이스 동작
- 회귀 (기존 endpoint 정상)
- `git add . && git commit -m "feat: §13 address-resolve endpoint" && git push`
