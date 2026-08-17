# FCM data 메시지 서명 스킴 (감사#1 — 위조 방지)

서버가 보내는 모든 FCM **data-only** 메시지에 Ed25519 서명을 붙인다.
앱은 **공개키로 검증만** 하므로 앱에 비밀이 없다 → 난독화(R8) off 여도 위조 불가.
(HMAC 대칭키는 앱에 비밀이 박혀 추출·위조 가능 → 채택 안 함.)

## 서버 (구현 완료 · cowork)
- `_send_fcm_data_to_phone` 이 전송 직전 모든 payload 에 자동 서명 부착.
- 키: Ed25519 개인키 1회 생성 후 `server_kv.fcm_ed25519_priv` 보관. 공개키만 노출.
- 붙는 필드(모두 string):
  - `sig` = base64( Ed25519 서명 )
  - `exp` = 만료 epoch **ms** (발송시각 +5분)
  - `sig_alg` = `"ed25519"`
- 공개키 배포: `GET /api/fcm/pubkey` → `{ "ok": true, "alg": "ed25519", "pubkey_b64": "<raw 32B base64>" }`

## 서명 대상(정규화 문자열) — 앱이 동일하게 재구성
1. 받은 `data` 에서 **`sig`, `exp`, `sig_alg` 3개 키를 제외**한 나머지를 (key 오름차순) 정렬.
2. 각 항목을 `"key=value"` 로, 줄바꿈(`\n`)으로 이음.
3. 마지막에 `"\nexp=<exp>"`(위 exp 값 그대로) 붙임.
4. UTF-8 바이트로 인코딩 → 이게 서명 대상 `msg`.

예) data = {type:collab_invite, share_id:abc123, owner_name:막내, title:응암동 현장, sig:…, exp:1786930631155, sig_alg:ed25519}
→ msg =
```
owner_name=막내
share_id=abc123
title=응암동 현장
type=collab_invite
exp=1786930631155
```

## 앱 검증 절차 (android 배선)
1. 공개키 1회 취득: `GET /api/fcm/pubkey` → `pubkey_b64` 캐시(또는 빌드 임베드). Ed25519 public key = base64decode(32바이트).
2. FCM data 수신 시:
   - `sig`/`exp`/`sig_alg` 없으면 → **미서명**: 민감 동작(서버동기화·계좌표시 등) 하지 말고 무시하거나, 인증된 API(Bearer)로 pull-confirm 후만 표시.
   - `now_ms > exp` → 만료 → 폐기.
   - 위 규칙으로 `msg` 재구성 → `pub.verify(base64decode(sig), msg)`.
   - **검증 성공한 것만** 알림 표시/동작. 실패 → 무시(폴링 안전망이 진짜 이벤트는 잡음).
3. 롤아웃: 서버는 이미 서명해서 보냄(구앱은 sig 무시 = 무해). 앱이 검증 배선 배포되면 자동 적용.

## 검증(cowork 완료)
- 라운드트립: 서명→pubkey→검증 통과, title/share_id 변조 시 검증 실패, exp 미래. Ed25519(cryptography, 기존 의존성).
- 실패 안전: cryptography 없거나 서명 예외 시 **미서명으로 폴백**(FCM 안 끊김) → 앱은 미서명을 pull-confirm/무시로 처리.

_문의: 필드명·정규화 규칙 조정 필요하면 SYNC 로. 앱 검증 배선은 android._
