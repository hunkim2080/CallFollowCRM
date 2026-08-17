# SERVER HANDOFF — FCM 즉시 푸시 (앱 꺼져 있어도 알림)

작성: 2026-06-12 · 안드로이드 Claude → 맥미니 Claude + 사장님
배경: 사장님 요청 "협업 요청/진행 알림이 **앱 꺼져 있어도 즉시** 뜨게(카톡처럼)".
현재: 협업 요청/진행 알림은 **폴링**(앱 켜짐 루프 + ReminderWorker 주기). 앱 완전 종료 시엔 즉시 안 옴.
해결: **FCM(Firebase Cloud Messaging)** 도입. 3박자(사장님 콘솔 + 서버 + 앱)가 맞아야 동작.

> ⚠️ 이건 폴링을 **대체가 아니라 보강**. FCM 실패/미설정이어도 기존 폴링 알림은 그대로 동작(안전망).

---

## 0. 큰 그림 (누가 뭘)

```
[보내는 사장 A] --invite--> [서버 api.si0in.kr] --FCM push--> [받는 사장 B 폰] --탭--> 수락화면
                                  ↑                                    ↓
                          B의 FCM 토큰 보관              FirebaseMessagingService 가 받아
                          (앱이 등록해 둠)                showCollabInvite 알림 띄움
```

- **사장님(콘솔)**: Firebase 프로젝트 생성 → `google-services.json`(앱용) + 서비스계정 키(서버용) 발급. (Claude 가 콘솔 가입 대행 불가)
- **서버(맥미니)**: ① 토큰 등록 endpoint ② invite/event 발생 시 FCM 전송.
- **앱(안드로이드 Claude)**: `google-services.json` 받으면 → FCM 의존성/서비스 + 토큰 등록 + 수신 처리. (이미 알림 표시 함수 `NotificationHelper.showCollabInvite` 등 존재 → 재사용)

---

## 1. 사장님이 할 일 (Firebase 콘솔 — 한 번만)

1. https://console.firebase.google.com → 프로젝트 생성(이름 아무거나, 예: "ringgo").
2. 좌측 "Android 앱 추가":
   - **패키지 이름: `com.detailline.callfollowcrm`** (정확히 이대로. 안 맞으면 푸시 안 옴)
   - 앱 닉네임/SHA: FCM 만 쓰면 SHA 불필요(비워도 됨).
3. **`google-services.json` 다운로드** → 안드로이드 Claude 에게 전달(이 파일은 `app/` 에 들어감. keystore 처럼 git 에 안 올림 → `.gitignore` 등록 예정).
4. 좌측 ⚙️ → 프로젝트 설정 → "서비스 계정" 탭 → **새 비공개 키 생성**(JSON) → **맥미니 서버 Claude 에게 전달**(서버가 FCM 보낼 때 인증용. 절대 git/앱에 X, 맥미니에만).

> 비용: FCM 푸시는 **무료**. (Firebase 프로젝트 생성도 무료.)

---

## 2. 서버(맥미니)가 만들 것

### 2.1 FCM 토큰 등록 endpoint
앱이 기기별 FCM 토큰을 서버에 올림. 전화번호(bizPhone) ↔ 토큰 매핑 저장(한 사람이 폰 여러 대 가능 → 토큰 list).

```
POST /api/push/register
body: { "phone": "01012345678", "token": "<fcm_token>", "platform": "android" }
→ 200 { "ok": true }
```
- `phone` = phoneKey(숫자만). 같은 phone 의 토큰들을 누적(중복 토큰은 upsert, 오래된 토큰 정리).
- 토큰은 갱신될 수 있음(앱이 onNewToken 마다 재등록) → upsert.
- (선택) `POST /api/push/unregister` 로그아웃/토큰무효 시.

### 2.2 invite 발생 시 FCM 전송
`POST /api/shared/invite` 처리 시, **route=="inapp"**(상대가 가입 사장)이면 그 **partner_phone 의 토큰들**로 FCM **data 메시지** 전송:

```
data: {
  "type": "collab_invite",
  "share_id": "<share_id>",
  "owner_name": "<보내는 사장 이름>",
  "title": "<현장 라벨, 예: 강동 천호동 현장>"
}
```
- **반드시 `notification` 블록 없이 `data` 만** 보낼 것. (앱이 직접 `showCollabInvite` 로 표시 → 일관된 한국어 문구/탭 동작. notification 블록 쓰면 OS가 멋대로 표시해 탭 동작이 달라짐.)
- Android priority `high`.
- 고객 전화번호·대화 **절대 미포함**(협업 벽 — owner_name + title 만).
- 전송 실패(토큰 무효 등)는 무시/정리. invite 자체는 성공 처리(푸시는 보강).

### 2.3 (확장, 선택) 진행/입금 이벤트도 FCM
- `/api/shared/progress`(출발/도착/완료) → owner_phone 토큰으로 `type:"collab_event"` data 푸시(step/partner_name/title/account).
- `/api/shared/paid` → partner 토큰으로 `type:"collab_paid"`.
- (앱은 이미 폴링으로 이것들 처리 중 → FCM 오면 즉시, 안 오면 폴링이 잡음. data payload 키만 맞춰주면 앱이 동일 알림 함수 재사용.)

### 2.4 라이브러리
- `firebase-admin` (Python) 권장. 서비스계정 JSON 으로 init → `messaging.send()` / `send_multicast()`.
- Python 3.9 호환 확인(서버 제약). `firebase-admin` 최신은 3.9 OK.

---

## 3. 앱(안드로이드 Claude)이 할 일 — `google-services.json` 받으면 착수

1. `app/build.gradle.kts`: `com.google.gms.google-services` 플러그인 + `firebase-bom` + `firebase-messaging` 의존성. 루트 gradle 에 plugin classpath.
2. `app/google-services.json` 배치 + `.gitignore` 등록(keystore 처럼 비공개).
3. `RingGoFcmService : FirebaseMessagingService`:
   - `onNewToken(token)` → `POST /api/push/register`(현재 bizPhone 있을 때).
   - `onMessageReceived(msg)` → `msg.data["type"]` 분기:
     - `collab_invite` → `NotificationHelper.showCollabInvite(ctx, share_id, owner_name, title)` (이미 있음 ✓)
     - `collab_event` → `showCollabEvent(...)` (이미 있음 ✓)
   - AndroidManifest `<service>` 등록 + POST_NOTIFICATIONS 권한(이미 있음).
4. 앱 시작/로그인 시 현재 토큰 1회 `register`(onNewToken 못 받은 케이스 대비).
5. 폴링은 그대로 둠(안전망).

**의존성: 위 1~2 는 `google-services.json` 없으면 빌드가 깨짐 → 파일 받기 전엔 앱 착수 불가.**

---

## 4. 진행 순서 (병렬 가능)
1. 사장님 → Firebase 프로젝트 + `google-services.json`(앱 Claude 에게) + 서비스계정 키(서버 Claude 에게).
2. 서버 Claude → §2.1 토큰 endpoint + §2.2 invite 푸시. (서비스계정 키 받으면 착수, SYNC 회신)
3. 앱 Claude → `google-services.json` 받으면 §3. (서버 endpoint 스펙 위와 일치 가정)
4. 양쪽 끝나면 폰 2대로 검증: A invite → B 폰 꺼진 상태에서도 "협업 요청 왔어요" 즉시 알림 → 탭 → 수락.

## 5. 요약 (맥미니 할 일)
- `POST /api/push/register`(+선택 unregister) — phone↔token list.
- `/api/shared/invite` route=inapp 시 partner 토큰으로 **data-only** FCM(`type:collab_invite, share_id, owner_name, title`).
- (선택) progress/paid 도 동일 패턴.
- `firebase-admin` + 서비스계정 키(맥미니에만). → 끝나면 SYNC 회신.
