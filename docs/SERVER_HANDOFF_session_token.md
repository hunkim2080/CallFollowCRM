# 서버 → 앱 회신 · 세션 토큰 계약 (보안 §B-1 / §D-2)

작성: cowork · 2026-07-30 · **서버 발급 구현·검증 완료 · 배포 대기**
근거: docs/SECURITY_HANDOFF_2026-07-30.md §B-1·§D. 사장님 결정: "토큰 발급부터 먼저(SOLAPI 나중)".

## 토큰 계약 (android 배선용)
- **발급 위치**: `POST /api/auth/verify-code` — 인증 성공(status=`member` 또는 `enrolled`) 응답에 필드 추가:
  - `sessionToken` (string) — 서버 서명 opaque 토큰.
  - `sessionTokenExpMs` (long) — 만료 epoch ms.
  - (기존 필드 `status`/`freeUntilMs`/`freeDays` 그대로 = 하위호환. `waitlisted` 는 토큰 없음 = 미인증.)
- **형식**: `"<phone11>.<expMs>.<sig>"` (예 `01012345678.1730000000000.ab12...`). 서버만 서명/검증. 앱은 **저장만**(파싱·신뢰 X).
- **만료**: 90일. 갱신 = 재인증(verify-code 다시). refresh 토큰 없음(단순).
- **요청에 실을 때**: `Authorization: Bearer <sessionToken>` 헤더. (관리자 토큰과 같은 컨벤션, 값만 세션토큰)

## 앱이 할 것 (§D-4)
1. verify-code 성공 시 `sessionToken` 저장 (EncryptedSharedPreferences 권장).
2. 이후 **모든 데이터 요청에 `Authorization: Bearer <sessionToken>` 부착.**
3. 401/403(토큰 만료·없음) 받으면 재로그인(OTP) 유도.
4. `SMS_SIGNUP_ENABLED` 전환은 **SOLAPI(§D-1) 켜진 뒤**(그전엔 OTP 문자 못 감).

## 서버 남은 것 (순서)
- **§B-2 (다음)**: 데이터 GET/POST 엔드포인트에 `Depends` 인증 = 헤더 세션토큰 검증 + `요청 phone == 토큰 phone` 강제.
  - **⚠️ 앱이 토큰을 부착하기 전에 이걸 켜면 전 기능이 401 로 깨짐.** → **앱이 토큰 부착 배포 완료 후** 서버 enforce 켜는 순서. 또는 `AUTH_ENFORCE` 스위치(기본 off)로 배포해두고 앱 준비되면 on.
  - 재사용 헬퍼(서버 준비됨): `_verify_session_token(token)`, `_session_phone_from_header(authorization)`.
- **§D-1 SOLAPI**: 사장님이 SOLAPI API키/시크릿/발신번호를 plist 에 추가해야 인증문자 발송 가능(최우선 병목, cowork 가 대신 못 넣음).
- **§B-3 레이트리밋 · §B-4 /docs 비활성**: 후속.

## 검증
- TestClient: 토큰 발급/검증/변조거부/만료거부/Bearer파서/verify-code 응답 sessionToken 라운드트립 + 하위호환 ALL OK.
- 미배포: bash server/deploy_phase1.sh

---

## 추가 (2026-07-30) — §B-2 인증 강제 미들웨어 + §B-4 (배포 완료·스위치 OFF)
- **§B-2**: `AUTH_ENFORCE=1`(env) 일 때만 작동하는 인증 미들웨어. 소유주 전용 경로(감사 §B-2 목록: shared/team/quote/intake-form/site-photos/labor/mirror(shares·snapshot·mycode·respond·disconnect)/push·register/owner-tone + 경로 phone: suggestions·customer-persona)에:
  - 유효 세션토큰 없으면 **401**, 요청 phone(쿼리/경로) ≠ 토큰 phone 이면 **403**(IDOR 차단).
  - POST body phone 은 미들웨어에서 못 읽어 '유효토큰 보유'까지(익명 차단). 완전 소유권은 앱 성숙 후 엔드포인트별 보강.
  - **공개/고객/뷰어(expo, mirror/data, intake 공개폼, download, healthz 등)는 목록에 없어 무영향.**
- **§B-4**: /docs·/redoc·/openapi.json 비활성(엔드포인트 목록 노출 차단) — 무조건 적용.
- **켜는 순서(중요)**: 앱이 `sessionToken` 저장 + 전 요청 Bearer 부착을 배포 완료 → 그 다음 서버 plist 에 `AUTH_ENFORCE=1` 넣고 재기동. 그 전에 켜면 전 기능 401.
- 검증: OFF=기존 무변화 / ON=무토큰401·남의번호403·공개경로 통과. TestClient 14 ALL OK.
