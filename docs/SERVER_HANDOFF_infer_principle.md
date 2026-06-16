# 서버 핸드오프 — `/infer-principle` (원칙 발견 Phase 2, 2026-06-17, android→cowork)

## 배경 (사장님 비전)
말투/사례 위 **3번째 학습 층 = "사장님이 *왜* 이렇게 답했는가" (판단 원칙)**.
사장님이 막내 추천과 **다르게** 답을 보냈을 때, 막내가 그 답에서 **한 줄 원칙**을 추론해
챗스크린 카드로 **"이게 사장님 원칙이에요?" ⭕/❌/나중에** 로 묻는다. ⭕면 원칙집에 저장되고,
그 원칙은 이후 prepare-reply 의 "응대 원칙"으로 주입된다(Phase 1 = 이미 배선됨).

- 컨셉 시안: `design-preview/proto-principle-discovery.html` (사장님 승인 완료)
- Phase 1(엔진): 원칙 저장/관리 + prepare-reply 주입 — **앱·서버 배선 끝**(서버는 `principles[]` 받아 시스템 프롬프트에 주입).
- Phase 2(이 문서): **추론 엔드포인트 1개만 추가하면** 발견 카드가 살아남.

## 앱이 호출하는 시점 (이미 구현됨, commit 참조)
`ChatViewModel.maybeInferPrinciple()` — 사장님이 답장 **발송 성공 직후**, 아래 게이트를 다 통과할 때만 호출:
1. 추천이 있었고(suggestions 비어있지 않음),
2. 추천 확신 `scenario_confidence >= 0.6` (사례 적으면 자연히 안 물음 = 콜드스타트 자동 억제),
3. 사장님이 보낸 답이 추천과 **편집거리 12자 이상** 다름(거의 같으면 패스),
4. **하루 최대 2회**(앱이 prefs 로 제한),
5. 이미 ❌ 했거나 보유 중인 원칙이면 앱이 카드 안 띄움.

→ 즉 **서버는 "정말 새 원칙이 있을 때만 principle 을 주고, 아니면 null"** 만 잘 해주면 됨. (빈도 제어는 앱이 함)

## 엔드포인트
`POST /infer-principle`  (동기 — 앱이 결과를 기다림. 앱 타임아웃 read 20s / call 25s)

### Request (JSON)
```json
{
  "customerMessage": "안녕하세요, 새 아파트 입주하는데 거실·욕실 줄눈 견적 알려주실 수 있을까요?",
  "aiSuggestion": "거실 줄눈은 35만원, 욕실은 개당 8만원이에요 😊 시공 원하시면 일정 잡아드릴게요!",
  "ownerReply": "신축이시군요! 신축은 마감 상태를 직접 봐야 정확해서, 제가 방문해서 정확히 잡아드릴게요 😊 이번 주 언제 편하세요?",
  "scenario": "price_inquiry",
  "existingPrinciples": [
    "가격만 빠르게 묻는 손님엔 가격 경쟁 대신 품질·AS를 먼저 설명한다",
    "단골/소개 손님은 계좌부터 편하게 안내한다"
  ],
  "deviceId": "dev-xxxx",
  "ownerTrade": "줄눈"
}
```
- `scenario`, `deviceId`, `ownerTrade` 는 없을 수 있음(optional).
- `existingPrinciples` = 그 폰에 이미 켜진 원칙들(중복 회피용). 비어있을 수 있음.

### Response (JSON)
새로 배울 원칙이 **있을 때**:
```json
{
  "principle": "신축 문의엔 즉답 견적 대신 방문 견적을 먼저 권한다",
  "question": "방금 보니 — 신축 문의엔 바로 가격을 알려주기보다 '방문 견적'을 먼저 권하시네요. 혹시 이게 사장님 원칙이에요?"
}
```
없을 때(애매/일회성/기존과 중복):
```json
{ "principle": null }
```

- `principle` (필수 의미): ⭕ 누르면 **그대로 저장**되는 한 줄. **선언형**, 일반화된 규칙, 고객 이름/특정 금액 없이.
- `question` (선택): 카드에 보일 질문체. 없으면 앱이 `"방금 보니, 사장님은 이렇게 응대하시네요: \"{principle}\" 이게 사장님 원칙이 맞아요?"` 로 감쌈. **가능하면 question 도 주면 카드가 자연스러움**(시안처럼 관찰 + "혹시 이게 원칙이에요?").
- `principle` 이 없거나 null 이면 카드 안 뜸. **확신 없으면 null 을 줘라(헛스윙 < 침묵).**

## LLM 프롬프트 설계 (권장)
역할: "사장님의 답장 습관에서 *재사용 가능한 판단 원칙*을 찾아내는 분석가."

입력으로 (고객 메시지 / 막내가 추천했던 답 / 사장님이 실제로 보낸 답)을 주고 판단:
1. 추천과 실제 답의 **차이가 '의도(전략)'에서 비롯됐는가**, 아니면 단순 말투/오타/길이 차이인가?
   - 말투·표현만 다르면 → `null`. (그건 말투 학습 소관)
2. 의도 차이라면 그 의도를 **일반화된 한 줄 원칙**으로. ("이 고객"이 아니라 "이런 상황의 고객엔")
3. `existingPrinciples` 중 **의미가 겹치면 → null**(중복 저장 방지).
4. 너무 특수해서 다음에 또 적용될 일 없으면 → `null`.

원칙 문체 규칙:
- 평서문 한 줄, 25자~45자 권장. 주어 생략 가능("~한다" 체).
- 고객 실명/특정 금액/날짜 제외(일반화).
- 사장님 1인칭 관점("내가 ~한다")보다 규칙형("~엔 ~한다")이 prepare-reply 주입에 좋음.

좋은 예: `"신축 문의엔 즉답 견적 대신 방문 견적을 먼저 권한다"`
나쁜 예: `"김신축 고객에게 35만원 대신 방문하기로 했다"` (특정/일회성 → null 이어야)

## 모델 (project_model_routing.md 기준)
- 빈도 낮음(하루 최대 2회/사용자) + 짧은 출력 → **Haiku 4.5 권장**(비용 최소).
- 품질 부족하면 Sonnet 으로 승격 검토. (분류+짧은 추론이라 Haiku 로 충분할 듯)
- `MODEL_PRICING_USD_PER_M` 에 이미 단가 있음 → llm_usage_log 에 기록 권장(엔드포인트명 태깅).

## 출력 강건성 (중요 — 과거 사고 참고)
- Gemini thinking 토큰 truncation 사고(reference: 무난답변 버그)처럼 **JSON 파싱 실패 시 빈 답** 나오지 않게,
  Anthropic JSON mode/툴 강제 또는 엄격 파서 + `{"principle": null}` fallback.
- 앱은 `principle` 키 없거나 null 이면 조용히 카드 스킵(에러 토스트 없음) — 서버가 5xx 줘도 앱은 silent.

## 앱 측 현재 구현 (참고)
- `SuggestionRepository.inferPrinciple(...)` → `ServerSuggestionRepository` 가 `POST {BASE_URL}/infer-principle`.
- 응답 파싱: `principle`(String, null/blank=카드 없음) + `question`(String?, optional).
- ⭕ → `PrincipleRepository.add(text, source="discovered")` (Phase 1 테이블 `principles`).
- ❌ → 앱이 그 후보를 `dismissedPrincipleCandidates`(prefs)에 넣어 재질문 안 함.
- BASE_URL = `api.si0in.kr` (AppConfig).

## 검증 (서버 붙으면)
```bash
curl -s -X POST https://api.si0in.kr/infer-principle \
 -H 'content-type: application/json' \
 -d '{"customerMessage":"신축 입주 줄눈 견적요","aiSuggestion":"거실 35만원이에요","ownerReply":"신축은 방문해서 봐야 정확해요, 한번 들를게요","scenario":"price_inquiry","existingPrinciples":[]}'
# 기대: {"principle":"신축 문의엔 방문 견적을 먼저 권한다","question":"..."}
curl ... (ownerReply 가 추천과 거의 같을 때) # 기대: {"principle":null}
```
