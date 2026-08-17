# 서버 핸드오프 — 시공접수서 "즉시 회신"(제출 순간 FCM 푸시)

작성: 2026-06-27 · android(Claude Code) → cowork(맥미니)
관련: 사장님 요청 — "고객이 접수서 작성 완료와 동시에 바로 회신오게. 지금은 60초 기다리고 옴."

---

## 배경 / 현재 동작

- 지금: 앱이 `GET /api/quote/submissions` 를 **60초 폴링**(`IntakeSyncManager`). 그래서 고객이 제출해도
  **최대 60초 뒤**에야 사장님 폰에 반영/알림. 게다가 폴링은 **앱이 켜져 있을 때만** 돎(앱 꺼져 있으면 더 늦음).
- 목표: 고객이 제출하는 **그 즉시** 사장님 폰에 알림 + 카드 반영(앱 꺼져 있어도).
- 해법: 제출 저장 직후 **사장님 번호로 FCM data 푸시**(`type=intake_submitted`) 한 방. 앱이 그걸 받아 즉시 sync.

## 앱 측 (완료 — 이번 커밋)

- `RingGoFcmService.onMessageReceived` 에 `"intake_submitted"` 케이스 추가.
- 받으면 곧장 `intakeSyncManager.sync()` 호출 → 기존 폴링과 **완전히 동일한 경로**로
  고객 카드 반영(주소·시공일·총금액·계약금) + `showIntakeSubmitted` 알림 + 채팅 타임라인 카드까지 처리.
- token 중복 가드(`intakeImportedTokens`)가 있어 **푸시 + 폴링이 겹쳐도 이중 알림 없음**. 폴링은 안전망으로 유지.
- 즉, 서버는 **데이터를 다 실어 보낼 필요 없이 "콕 찔러주기"만** 하면 됨(앱이 알아서 submissions 를 다시 당겨감).

---

## cowork 측 할 일 (서버) — 한 곳, 작게

### 위치
`intake_form_submit` (POST 제출 핸들러, `server/main.py` 약 line 11987).
지금 코드:
```python
con.execute(
    "UPDATE intake_forms SET submitted_at_ms = ?, payload_json = ? WHERE token = ?",
    (now, json.dumps(payload, ensure_ascii=False), req.token),
)
con.commit()
print(f"[intake-form/submit] token={req.token} phone={phone} → submitted")
return {"ok": True, "submitted_at_ms": now, "phone": phone}
```

### 추가
`con.commit()` 직후(같은 `with db_conn()` 안 또는 뒤에서) **owner_phone** 을 읽어 FCM 발송:

```python
# 사장님 폰으로 즉시 푸시 — 앱이 받아 60초 폴링 안 기다리고 바로 sync (type=intake_submitted).
owner_phone = con.execute(
    "SELECT owner_phone FROM intake_forms WHERE token = ?", (req.token,)
).fetchone()
owner_phone = (owner_phone[0] if owner_phone else None) or phone  # owner_phone 없으면 발급 시 phone(=사장님) 폴백 확인 필요
```
```python
# commit 이후, with 블록 밖에서 발송(기존 _send_fcm_data_to_phone 패턴과 동일)
if owner_phone:
    _send_fcm_data_to_phone(owner_phone, {
        "type": "intake_submitted",
        "token": req.token,
        "customer_phone": contact_phone,   # (선택) 로깅/표시용
    })
```

- **반드시 data-only** (notification 블록 없이) — 기존 협업 푸시와 동일 규칙(앱이 한국어 문구·동작 직접 처리).
- FCM 값은 전부 string (기존과 동일).
- 실패해도 제출 응답엔 영향 없게(기존 `_send_fcm_data_to_phone` 가 내부에서 예외 삼킴 — 확인만).

### ⚠️ 확인 포인트 1개
`intake_forms.owner_phone` 가 **사장님(발급자) 번호**가 맞는지(= `push_tokens.phone` 과 같은 키).
- 앱 폴링은 `devicePhone = bizPhone`(사장님 번호)로 `GET /api/quote/submissions` 조회함 → 즉 submissions 는 **owner_phone 기준**으로 사장님에게 묶임.
- 그래서 푸시도 **owner_phone** 으로 보내면 됨. `_send_fcm_data_to_phone` 는 그 번호의 push_tokens 를 찾아 발송.
- 만약 발급 시 `owner_phone` 가 비어있는 옛 토큰이 있으면 → 위 폴백(`or phone`)이 맞는지 한 번 검증.

---

## 일부러 안 하는 것
- ❌ 푸시에 제출 전체 데이터(주소·금액 등) 싣기 → 불필요. 앱이 sync() 로 정식 데이터를 당겨가는 게 정합성↑(폴링과 동일 소스).
- ❌ 폴링 제거 → 유지(안전망). FCM 미도달(토큰 만료/도즈)이어도 다음 폴링이 잡음.

## 검증
1. 앱에서 접수서 발급 → 고객 링크로 제출.
2. 서버 로그 `[fcm] {owner_phone} type=intake_submitted ...` 확인.
3. 사장님 폰에 **수 초 내** "📋 {이름}님이 시공접수서를 작성했어요" 알림 + 채팅 타임라인 카드(폴링 안 기다리고).
4. 앱 꺼진 상태에서도 알림 오는지(FCM 백그라운드) 확인.
