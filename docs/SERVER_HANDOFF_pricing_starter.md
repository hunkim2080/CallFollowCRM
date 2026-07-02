# SERVER HANDOFF — 가격 온보딩 스타터 (POST /pricing/starter)

대상: 맥미니 서버(cowork). 배경/전체 기획: `docs/PLAN_price_onboarding.md`.
앱측 현재 상태(2026-07-02, 안드로이드 Claude): **온보딩 15개 업종(줄눈·실리콘코킹·도배·장판마루·타일·욕실리모델링·페인트·필름·방충망·중문샷시·에어컨·입주청소·누수방수·도어현관·조명전기) 전부 앱에 내장**(DefaultTradeTemplates, isEstimated=true) → 서버 없이 즉시/오프라인 동작. **따라서 이 서버 엔드포인트는 "직접 입력한 커스텀(내장에 없는) 업종" 롱테일만 담당**하면 된다. 우선순위 낮아졌음(내장 15개로 대부분 커버). 엔드포인트 생기면 앱이 커스텀 업종일 때만 호출하는 클라이언트를 붙인다.

---

## 목적
신규 사장님이 온보딩에서 업종만 고르면, 그 업종의 흔한 견적 항목 + 대략 시세를 서버가 만들어 가격표를 '추정값'으로 미리 채운다. 빈 표로 시작해 이탈하는 걸 막는다. (완벽할 필요 없음 — AI 답변이 쓸만해질 최소 가격정보)

## 엔드포인트 (2개, 기존 suggestions_cache 패턴 재사용)
- `POST /pricing/starter` — 앱이 `{ownerTrade, ownerRegions, deviceId}` 전송, 즉시 202/200 (fire-and-forget). 서버는 백그라운드로 생성 후 캐시.
- `GET /pricing/starter/{deviceId}` — 앱이 폴링/조회. 준비 안 됐으면 `{status:"pending"}`, 되면 `{status:"ready", items:[...]}`.
  (동기 단일 엔드포인트로 만들어도 무방 — 앱은 어느 쪽이든 맞춤. 단순하면 POST 하나로 즉시 items 반환도 OK.)

## 입력 JSON
```json
{ "ownerTrade": "에어컨", "ownerRegions": ["서울"], "deviceId": "..." }
```

## 출력 JSON (엄격)
```json
{ "status": "ready",
  "items": [
    { "title": "벽걸이 에어컨 설치",
      "priceWon": 100000,        // ⚠️ 반드시 '원 단위' 정수, 만원 배수 권장
      "unit": "FLAT",            // "FLAT"(정액/개당) | "PYEONG"(평당)
      "category": "COMMON",      // "NEW"(신축) | "OLD"(구축) | "COMMON"(공통)
      "confidence": 0.7 }        // 0~1
  ] }
```

## ⚠️ 최우선 계약 — priceWon 은 '원 단위'
- 앱 `PricingItemRepository.insert(price=priceWon)` 로 **그대로** 저장한다. 앱은 ×10000 을 하지 않는다.
- 즉 30만원짜리는 `priceWon: 300000` 으로 줘라. `30` 이나 `30만` 같이 주면 안 된다.
- 앱에 단위테스트(PricingItemFormatTest)로 이 계약을 못박아 뒀다. 어기면 견적 금액이 10000배 틀어진다.
- `unit`/`category` 문자열은 대문자 그대로(FLAT/PYEONG, NEW/OLD/COMMON). 모르면 unit=FLAT, category=COMMON(보수적 기본값).

## 줄눈은 LLM 부르지 말고 하드코딩 (정확도 검증됨, 비용 0)
`ownerTrade` 에 "줄눈" 포함 시, 아래 18항목을 그대로 반환(앱 DefaultPricingItems 와 동일). 단 앱이 줄눈은 이미 로컬 시드하므로 실제로는 거의 안 불릴 것 — 그래도 일관성 위해 서버도 동일 데이터 보유 권장.
```
신축(NEW), FLAT:
  욕조 있는 화장실 바닥 1곳 = 400000
  샤워부스 있는 화장실 바닥 1곳 = 450000
  샤워부스 벽 3면 = 350000
  욕조벽 3면 = 350000
  화장실 전체 벽 (추가 시) = 700000
  세탁실 (폴리우레아) = 150000
  베란다 (폴리우레아) = 150000
  현관 = 50000
  거실 타일 = 1500000
구축(OLD), FLAT:
  욕조 있는 화장실 바닥 1곳 = 500000
  샤워부스 있는 화장실 바닥 1곳 = 550000
  샤워부스 벽 3면 = 350000
  욕조벽 3면 = 350000
  화장실 전체 벽 (추가 시) = 700000
  세탁실 (폴리우레아) = 150000
  베란다 (폴리우레아) = 150000
  현관 = 100000
  거실 타일 = 1500000
```

## 타 업종 = Claude Sonnet 4.6 1콜
system 프롬프트 골자:
```
너는 한국 인테리어/시공 견적 데이터 생성기다.
항목명은 초보 사장님이 고객에게 그대로 읽어줄 수 있는 자연어.
가격은 2025~2026 한국(수도권) 시세의 '보수적 중앙값'을 원 단위 정수(만원 배수)로.
확신 없으면 항목을 만들지 마라. 없는 항목/가격을 지어내지 마라.
```
user: `업종: {ownerTrade}, 지역: {regions}. 이 업종에서 가장 흔한 견적 항목 6~12개를 위 JSON 스키마로.`
서버 후처리: confidence<0.4 버림, priceWon 만원 배수로 반올림, unit 모르면 FLAT.

## 기술 메모
- Python 3.9 → `Optional[...]` 사용(PEP604 `X | None` 금지, get_type_hints 502 유발).
- 캐시 키 = deviceId (재과금 방지), suggestions_cache 방식 재사용.
- 비용: 사용자당 1회, 입력~300/출력~600 토큰 ≈ $0.01 미만. 온보딩 1회성이라 총량 무시 가능.

## 앱측 후속(엔드포인트 준비되면 안드로이드 Claude가)
- `StarterPricingRepository`(POST/GET) 추가 + OnboardingScreen 에서 비-줄눈 업종일 때 호출 → 응답 items 를 `insert(isEstimated=true)`.
- 지금은 줄눈 로컬 시드만 라이브. 이 문서 계약대로 엔드포인트 나오면 배선.
