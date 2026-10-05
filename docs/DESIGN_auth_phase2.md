# 보안 2단계 설계 — POST 본인확인(#6) + 나머지 (2026-10-05)

> 1단계(값싼 것)는 배포 완료: app-backup·shared/monthly 보호목록 추가 · 접수서 덤프 차단 ·
> IP 리미터. 이 문서는 **홍보/정식출시 전 필수**인 2단계 SoT 다.
> 출처 감사: 페이블 보안감사 2026-10-05 (세션 95248a01). 관련 메모 [[project_auth_login_no_verification]].

---

## 0. 바탕 사실 (이걸 모르면 설계가 틀어진다)

- **라이브는 `AUTH_ENFORCE=1` 이 이미 켜져 있다**(plist). → `_AUTH_PROTECT_PREFIXES`(server/main.py ~6076)
  에 든 경로는 **토큰 없으면 미들웨어가 401**, GET 은 **쿼리 phone == 토큰 phone** 까지 강제(403).
- **앱은 모든 서버 호출에 `Authorization: Bearer <세션토큰>` 을 이미 붙인다** — `ai/Net.builder()`
  (PushRegisterRepository·TeamRepository·MirrorRepository·SharedSiteRepository·BackupRepository 전부 사용).
  각 앱은 **자기 번호로 로그인** → 자기 토큰을 보낸다.
- 따라서 **#6 은 서버만 고치면 된다. 앱 변경 불필요.** (옛 토큰 없는 앱은 AUTH_ENFORCE=1 때문에 이미 막혀 있다.)

## 1. 뚫린 자리 (#6 구조결함)

미들웨어는 **GET 만** 쿼리 phone 을 토큰과 대조한다. **POST 는 body 를 못 읽어** '유효 토큰 보유'까지만 본다
(미들웨어 주석도 인정: *"POST 는 body phone 을 미들웨어에서 못 읽으므로 '유효 토큰 보유'까지만"*).
→ **로그인 한 번 한 사람(무료 가입)이 남의 번호를 body 에 넣어 남 이름으로 행동**할 수 있다.

`_session_phone_from_header` 는 **미들웨어에서만** 쓰이고 **어느 핸들러도 body 를 토큰과 대조하지 않는다**(grep 확인).

## 2. 핵심 설계 — 핸들러에서 "호출자 == 당사자" 강제

### 2-A. 공용 헬퍼 (한 곳, 두 벌 금지)
```python
def _require_caller(request: Request, *allowed: Optional[str]) -> str:
    """로그인 토큰의 phone 이 allowed 중 하나와 일치해야 통과. 아니면 401/403.
    allowed 는 '이 행동을 할 자격이 있는 번호'(서버가 DB 에서 뽑은 권위 값)."""
    tok = _session_phone_from_header(request.headers.get("authorization"))
    if not tok:
        raise HTTPException(401, "로그인이 필요합니다")
    allow = {_norm_phone(a) for a in allowed if a}
    if _norm_phone(tok) not in allow:
        raise HTTPException(403, "본인만 할 수 있어요")
    return _norm_phone(tok)
```
> ⚠️ **body 의 phone 을 믿고 거기에 맞추면 안 된다.** 공격자가 body 에 아무 번호나 넣으니까.
> `allowed` 는 **공유 기록(DB)에서 뽑은 owner_phone / partner_phone** 이어야 한다.
> 지금 핸들러들은 이미 share_id 로 레코드를 찾아 body phone 과 대조하는 "벽"이 있다 — 그 **레코드 값**을 쓴다.

### 2-B. 엔드포인트별 바인딩 표 (⭐ 블라인드 금지 — 당사자가 다르다)

| 엔드포인트 | 부르는 사람 | 토큰이 일치해야 할 번호(레코드 기준) | 지금 보호목록? |
|---|---|---|---|
| `shared/invite` | A(공유자) | `owner_phone` | ✅ |
| `shared/paid` | A | 레코드 `owner_phone` | ✅ |
| `shared/cancel` | A | 레코드 `owner_phone` | ❌ **추가** |
| `shared/respond` | **B(받은이)** | 레코드 `partner_phone` | ❌ **추가** |
| `shared/progress` | **B** | 레코드 `partner_phone` | ✅ |
| `shared/end` | 양쪽 | 레코드 `owner_phone` **또는** `partner_phone` | ❌ **추가** |
| `shared/comment` | 참여자 | site 의 `owner_phone` 또는 `partner_phone` | ❌ **추가** |
| `push/register` | 본인 | body `phone` (= 토큰 phone 이어야) | ✅ |
| `team/member/invite` | A(owner) | body `owner_phone` | ✅(team/) |
| `team/member/{id}` DELETE | A | 그 멤버의 `owner_phone` | ✅(team/) |
| `mirror/snapshot` | 업무폰 본인 | body owner phone | ✅ |

- **respond/progress 가 함정**: B 가 부르므로 `partner_phone` 에 묶는다. owner 에 묶으면 **B 의 수락이 막힌다.**
- **end/comment 는 양쪽 허용**: `_require_caller(request, rec.owner_phone, rec.partner_phone)`.
- **push/unregister**: body 가 **FCM 토큰 하나뿐**(phone 없음). FCM 토큰은 그 기기만 아는 값이라 바인딩 대상이 없다 →
  이번 범위에서 제외(열거 불가, 위험 낮음). 기록만.

### 2-C. 보호목록 추가 (토큰 존재 보장 — 익명 차단)
`_AUTH_PROTECT_PREFIXES` 에 추가: `shared/respond`·`shared/cancel`·`shared/end`·`shared/comment`.
(이미 든 것: invite·progress·paid·reschedule·update-address·photo·push/register·team/·mirror/*)

### 2-D. 구현 순서
1. `_require_caller` 헬퍼 추가(= `_session_phone_from_header` 아래).
2. 각 핸들러: 레코드(share/ site)를 찾은 **직후**, body phone 대조 "벽" 과 **나란히** `_require_caller(request, <레코드 당사자>)` 호출.
   핸들러에 `request: Request` 없으면 `request: Request = None` 로 추가(기존 관례, progress/comment 는 이미 있음).
3. 보호목록 2-C 추가.
4. **각 엔드포인트 3연 검증**(curl): 본인 토큰 200 · 남 토큰 403 · 토큰 없음 401.
5. §3 절차 배포 + `smoke.sh` 10/10 + repo 동기화.

### 2-E. 위험 / 롤백
- 위험: 어떤 정상 흐름이 '남 토큰'으로 부르면 403 로 깨진다. → 2-B 표를 레코드 기준으로 정확히 묶으면 안 깨진다
  (A 는 A 토큰, B 는 B 토큰을 보냄). **배포 전 3연 검증으로 본인 200 을 반드시 확인.**
- 롤백: 백업 `main.py.bak-*` 즉시 복원(§3).

## 3. 나머지 (#6 다음, 독립적)

- **#7 유료 LLM 무인증** — `_ensure_beta_whitelist(phone)` 가 phone 비면 통과. `/api/expo/ocr/*` 는 gate 아예 없음.
  fix: LLM/STT 엔드포인트에 세션 토큰 필수 + 레이트리밋을 **인증된 owner** 기준으로. (비용/문자폭탄과 같은 결)
- **#8 web studio SSRF** — `/api/web/tone-url`·`tone-analyze` 가 임의 URL fetch(127.0.0.1·Tailnet 도달).
  fix: 호스트 resolve 후 사설/루프백/링크로컬 거부 + naver/instagram/threads 화이트리스트.
- **#9 ADMIN_TOKEN localStorage** — `_admin_pass` 가 생 토큰을 api.si0in.kr localStorage 에 심음 + `?token=` URL.
  fix: 짧은 수명 admin 세션값만 심거나 sm_admin 쿠키로; `?token=` 은 쿠키 로그인 후 폐기; admin 을 별도 origin 으로.
- **#12 expo contract secret 선택적** — `k`/`secret` 보낼 때만 검사. fix: `if not k or not compare_digest(...)` 로 필수화.

## 4. 분담
- **#6 + #7 서버쪽 토큰강제**: 서버(코워크 부재 시 android §1 예외). **앱 변경 불필요**(Bearer 이미 감).
- #8/#9/#12: 서버. #9 의 '별도 origin' 은 큰 작업이라 사장님 결정 필요.
