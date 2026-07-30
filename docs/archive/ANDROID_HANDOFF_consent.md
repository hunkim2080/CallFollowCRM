# ANDROID HANDOFF — 개인정보 동의 (가입 화면 통합) · 2026-07-06

> 서버측 완료 (추가97, cowork). SKT AI메시지 동의 구조 벤치마킹 (사장님 지시).
> ⚠️ 문안은 초안 — 정식 출시 전 전문가/표준양식 검토 예정. 앱은 구조만 맞추면 됨.

---

## 가입 화면 (ANDROID_HANDOFF_signup_auth.md 의 화면에 추가)

전화번호 입력 → 인증번호 입력 **사이 또는 인증 성공 직후**에 동의 바텀시트 (SKT 스타일):

```
시공막내를 사용하기 위해 동의해 주세요.
  ☑ (필수) 개인정보 수집·이용 동의            [>]  ← 탭하면 웹뷰
  ☐ (선택) 서비스 품질 향상을 위한 수집·이용 동의 [>]
              [ 동의하고 시작하기 ]
```

- 필수 미체크 = 버튼 비활성. 선택은 미체크로도 진행 가능.
- [>] 링크: `https://api.si0in.kr/consent/required` / `/consent/optional` (웹뷰 또는 커스텀탭)
- 방침 전문: `/privacy`

## 동의 기록 API (동의하고 시작하기 누를 때 호출)

```
POST /api/consent
{ "phone": "01012345678", "docType": "required",         "agreed": true }
{ "phone": "01012345678", "docType": "optional_quality", "agreed": true|false }
→ { ok, docVersion: "2026-07-06", recordedAtMs }
```
- 두 docType 각각 한 번씩 호출 (선택 미체크면 agreed:false 로도 기록 — "안 했다"는 기록도 영수증).
- 상태 확인: `GET /api/consent/status?phone=...` → consents.required / consents.optional_quality (null = 기록 없음).

## 설정 화면

- "개인정보 수집·이용 (품질 향상)" 토글 = optional_quality 동의/철회 (POST agreed:true/false).
- **기존 toneUploadConsented 를 이 동의로 통합** — 톤 학습 업로드는 optional_quality agreed=true 일 때만.
- 링크 3종 (필수 동의문 / 선택 동의문 / 처리방침) 설정 하단에.

## 기존 사용자 (마이그레이션)

- 이미 쓰고 있는 베타 사장님들: 다음 앱 실행 시 동의 바텀시트 1회 노출 (status API 로 기록 없으면).
- toneUploadConsented=true 였던 사용자는 optional_quality 사전 체크 상태로 보여주기.

— cowork (2026-07-06)
