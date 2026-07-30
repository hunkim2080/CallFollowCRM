# 서버 작업 프롬프트 — `GET /api/tone/profile` (내 말투 학습 채우기)

> 대상: Mac mini 의 서버 Claude (cowork / Claude Code, `server/` 담당)
> 작성: Windows 안드로이드 Claude, 2026-06-03
> 영역: **`server/main.py` 만**. 앱 코드는 안드로이드 쪽이 이 계약대로 붙임 (서버는 엔드포인트만 내면 됨).

---

## 0. 왜 필요한가 (한 줄)

앱 **더보기 → 내 말투 학습** 화면(프로토 `renderTone`)을 프로토 그대로 채우려는데, 화면의 **학습률 %·말투 특징·before/after 비교** 3가지는 서버 분석이 있어야 진짜 값이 나옵니다. 지금 앱은 가짜 숫자를 안 넣고 **"서버 분석 준비 중"** 으로 정직하게 비워둔 상태입니다. 이 엔드포인트가 나오면 앱이 그 자리에 **진짜 값**을 박습니다.

## 1. 서버는 이미 재료를 다 갖고 있음

사장님이 보낸 문자는 이미 서버에 임베딩되어 있습니다:

- `POST /api/owner-tone/batch-upload` 로 업로드됨 → `owner_tone` 테이블(SQLite + sqlite-vec, bge-m3 임베딩)
- 키: **`device_id`** (현재 앱은 단일값 `"owner-anon"` 사용 — multi-device 는 다음 sprint)
- 즉, `owner_tone WHERE device_id='owner-anon'` 가 사장님 말투 코퍼스 전체입니다.

기존 분석 엔드포인트도 있음: `POST /api/style-profile/learn` → `{sampleCount, stats:{kindness, avgLength, emojiPerMessage}}`. 이건 숫자 통계만. 이번엔 **서술형 + 비교 + 학습률**이 필요합니다.

## 2. 구현할 엔드포인트 (계약 — 앱이 이 JSON 그대로 파싱함)

```
GET /api/tone/profile?device_id=owner-anon
```

**200 응답:**
```json
{
  "deviceId": "owner-anon",
  "analyzed": true,
  "sampleCount": 381,
  "learnRatePct": 76,
  "traits": [
    {"k": "말끝",     "v": "친근한 \"~요\"체"},
    {"k": "이모티콘", "v": "😊 자주 (메시지당 ~1개)"},
    {"k": "길이",     "v": "짧고 핵심만 (2~3줄)"},
    {"k": "호칭",     "v": "\"고객님\""},
    {"k": "시그니처", "v": "\"편하게 문의주세요!\""}
  ],
  "example": {
    "question": "24평 화장실 2개 줄눈 얼마예요?",
    "plain": "안녕하세요. 24평 화장실 2개 줄눈 시공은 28~32만원입니다. 현장 사진을 보내주시면 정확한 견적을 안내해 드리겠습니다.",
    "mine":  "아 화장실 2개시구나~ 보통 28~32만원선이에요 😊 사진 몇 장만 주시면 딱 맞게 봐드릴게요! 편하게 문의주세요~"
  },
  "editCount": 0
}
```

필드 의미 + 계산법:

| 필드 | 의미 | 계산 |
|---|---|---|
| `analyzed` | 분석 가능 여부 | `sampleCount >= 30` 이면 true. false 면 앱은 placeholder 유지(아래 §4) |
| `sampleCount` | 학습한 문자 수 | `SELECT COUNT(*) FROM owner_tone WHERE device_id=?` |
| `learnRatePct` | "막내가 사장님 말투를 N% 따라함" (0–100) | **`min(100, round(sampleCount / 500 * 100))`** — 앱이 막내비서 카드에서 쓰는 것과 **같은 500건 목표 공식**을 쓰세요. (두 화면 % 가 어긋나면 사장님이 혼란) 더 똑똑한 지표(코퍼스+채택률 blend)를 쓰고 싶으면 그래도 되지만, 그땐 앱이 막내비서 카드도 이 값으로 통일할 수 있게 알려주세요 |
| `traits` | 말투 특징 서술형 **5개** | 아래 §3-a. **키 5개 고정**: 말끝/이모티콘/길이/호칭/시그니처. 값은 코퍼스에서 실제로 뽑은 서술 |
| `example` | 같은 질문 before/after | 아래 §3-b. `question` 고정 1개, `plain`=톤 없는 일반 AI 답, `mine`=사장님 말투(RAG) 답 |
| `editCount` | (선택) "추천을 N번 고쳐주심" | 추천 수정 로그가 있으면 카운트, 없으면 `0` |

## 3. 생성 방법 (LLM)

코퍼스가 천천히 변하므로 **결과를 캐시**하세요(기존 `summary_cache` 류 재활용). 재계산 트리거: `sampleCount` 가 직전 대비 +50 이상 늘었거나, 캐시가 24h 지났을 때. 비용 로깅은 기존 `llm_usage_log` 에.

### 3-a. traits (말투 특징 5개)
- 입력: `owner_tone` 에서 랜덤/최근 **100건** 본문 샘플.
- 모델: **Haiku 4.5** (요약/분류 라우팅 — `MODEL_PRICING_USD_PER_M` 에 이미 있음). 충분.
- 프롬프트 취지: "아래는 한 사장님이 고객에게 보낸 실제 문자들이다. 이 사람의 말투를 5개 항목으로 요약하라: 말끝/이모티콘 사용/문장 길이/고객 호칭/자주 쓰는 마무리 인사(시그니처)." → JSON `[{k,v}]` 5개로 강제.

> ⚠️ **[2026-06-03 수정 — 중요] `v` 값은 반드시 "아주 짧은 한 마디"여야 함 (문장 금지).**
> 앱이 이 값을 **작은 알약 칩**(한 줄에 2개씩)으로 렌더함. 긴 문장이 오면 칩이 전체폭 카드로 늘어나 **프로토와 완전히 달라짐**(사장님 지적). 현재 서버가 보내는 값이 너무 길다.
> - **각 `v` ≤ 12~15자**, 핵심 한 마디. 부연설명·복수예시 나열·"~정도/~혼용/업무적" 같은 군더더기 금지.
> - 목표 스타일 = 프로토 verbatim (이 형식 그대로 맞춰라):
>   - `{"k":"말끝","v":"친근한 \"~요\"체"}`
>   - `{"k":"이모티콘","v":"😊 자주 (메시지당 ~1개)"}`
>   - `{"k":"길이","v":"짧고 핵심만 (2~3줄)"}`
>   - `{"k":"호칭","v":"\"고객님\""}`
>   - `{"k":"시그니처","v":"\"편하게 문의주세요!\""}`
> - 나쁜 예(현재 서버 출력 — 금지): `"존댓말 중심(\"~요\",\"~네요\",\"~요\") + 반말 혼용, 친근하면서도 업무적"`, `"^^, ㅠㅠ, 😂, 👍 등 자주 사용, 메시지당 평균 0.3~0.5개 정도"`
> - 프롬프트에 "각 항목은 12자 이내 짧은 명사구. 문장으로 쓰지 마라. 예: 말끝→친근한 ~요체" 처럼 길이 제약 + few-shot 을 명시하면 안정적. 안전하게 서버에서 `v` 를 한 번 더 자르고 싶으면 20자 hard-cut(말줄임 …) 도 가능하지만, 프롬프트로 짧게 뽑는 게 우선.

- 코퍼스가 빈약하면(§4) traits 생략 또는 `analyzed:false`.

### 3-b. example (before/after 비교)
- `question`: 우선 고정 1개 `"24평 화장실 2개 줄눈 얼마예요?"` 로 시작해도 됨(프로토 동일). 나중에 사장님 업종/실제 자주 받는 질문으로 동적화 가능.
- `plain`: **톤 적용 없이** 일반 AI 답변 (RAG·owner_tone 미사용). 모델 Haiku 가능.
- `mine`: **사장님 말투로** 답변 — 기존 prepare-reply 파이프라인(owner_tone RAG retrieval) 그대로 1회 호출. 품질 중요하니 **Sonnet** 권장(prepare-reply 라우팅과 동일).
- 둘 다 캐시.

## 4. 폴백 (중요 — 앱이 안 깨지게)

- 코퍼스 부족(`sampleCount < 30`) / 임베딩 미설치(`embeddings_available=false`) / LLM 실패 시:
  ```json
  { "deviceId":"owner-anon", "analyzed": false, "sampleCount": 12, "learnRatePct": 2,
    "traits": [], "example": null, "editCount": 0 }
  ```
  → 앱은 hero 의 **% 와 문자 수만** 표시하고, traits/비교는 **"서버 분석 준비 중"** placeholder 를 유지합니다. (가짜 안 만듦)
- 즉 `analyzed:false` 여도 `sampleCount`/`learnRatePct` 는 항상 채워주세요.

## 5. 완료 시 알려줄 것 (SYNC append)

- 엔드포인트 배포됨 / 캐시 정책 / `analyzed` 임계값 / `learnRatePct` 공식(500 목표 유지했는지).
- 그러면 안드로이드가 `ToneLearnProtoSection` 의 placeholder 2곳(traits·example)과 hero % 를 이 응답으로 교체합니다. (앱 측 작업은 안드로이드 Claude 담당)
