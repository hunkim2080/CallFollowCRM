# 서버 핸드오프 — 상담함/문자함 분류 Phase 2 (Haiku) (2026-07-11)

앱은 Phase 1(로컬 1차 분류 + 2탭 UI)을 이미 배포했다. 서버(cowork)는 **애매(UNSURE) 건만** Haiku 로 2차 정정하는 엔드포인트를 추가한다.

## 배경
- 기본 문자앱이 되며 고객 상담(상담함)과 일반 문자(문자함=광고·인증·대표번호)가 섞임.
- 앱 로컬 분류(`InboxClassifier`): 저장고객/답장이력=상담함, 대표번호(15xx)/영숫자/짧은코드/isLikelyAd=문자함, **애매=상담함**(precision 최우선).
- 로컬이 "애매(UNSURE)"로 둔 것(낯선 010 + 광고/서비스 아님)만 Haiku 가 본다. → 호출량 소수, 비용 미미.

## 신규 엔드포인트 `POST /api/thread/classify`
⚠️ **Python 3.9 — PEP 604 금지. `Optional[str]` 사용** (main.py get_type_hints 502 방지).

요청:
```json
{
  "device_phone": "0101234...",
  "suffix": "12345678",
  "address": "010-...",
  "is_saved_customer": false,
  "has_owner_reply": false,
  "messages": [ {"role":"customer|owner","body":"...","ts": 1720000000000} ]  // 최근 ≤5, 시간순
}
```
응답:
```json
{ "bucket": "consult" | "general", "confidence": 0.0, "reason": "짧은 사유" }
```

### 프롬프트 원칙 (precision 최우선 — 앱과 동일 철학)
- "시공(줄눈/도배/타일 등) 문의·상담일 가능성이 조금이라도 있으면 **consult**."
- 광고/스팸/인증번호/은행·택배 알림/세일즈처럼 **명백히 고객 상담이 아닐 때만 general**.
- 애매하면 consult (general 은 확신 ≥0.9 일 때만).
- 저장고객(is_saved_customer)·답장이력(has_owner_reply)=true 면 **무조건 consult** (앱이 안 보내지만 안전장치).

### 모델·캐시·비용
- 모델: Haiku 4.5 (분류에 이미 사용 중).
- 캐시: suggestions_cache 식 SQLite, 키 = `suffix + 본문해시`. 같은 내용 재요청 0.
- 배치 변형 `POST /api/thread/classify-batch` (재접속/백필, 최대 20건) 권장.

## 앱 측 후속 (안드로이드, 이 핸드오프와 별개로 진행)
- `ai/ThreadClassifyRepository.kt` 신설(PhaseOneApiRepository 패턴).
- SmsReceiver/홈 ON_RESUME 에서 UNSURE 스레드만 큐잉 → classify 호출 → 결과를 `threadBucketRepository` 에 HAIKU source 로 반영(BucketPolicy 가 우선순위 처리, OWNER 는 못 덮음).
- confidence≥0.9 general 만 자동 강등. 강등 시 상담함 하단 "문자함으로 옮긴 N건 — 확인 ▸" 복구 줄(사장님 결정: 자동 이동+알림).
- `classifiedBodyHash` 로 중복 호출 방지. 서버 다운/오프라인이면 스킵(상담함 유지).

## 참고 코드 (앱, 이미 배포됨 — commit 9f2707e)
- `domain/inbox/InboxClassifier.kt`, `BucketPolicy.kt`, `NonCustomerHeuristics.kt`
- `data/repository/ThreadBucketRepository.kt` (classifyLocal / moveToConsult / moveToGeneral)
- `data/local/entity/ThreadBucketEntity.kt` (thread_buckets, DB v40)
