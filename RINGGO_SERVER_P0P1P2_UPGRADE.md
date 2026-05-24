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
