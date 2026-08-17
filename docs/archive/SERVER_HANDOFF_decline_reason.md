# SERVER HANDOFF — 협업 거절 사유 (`POST /api/shared/respond` 확장)

작성: android · 2026-07-08 · 담당: cowork(맥미니)

## 왜
B가 협업 요청을 거절할 때 **사유**를 고르게 했음(앱 완료). 요청한 사장님(A)이 "왜 거절했지?"
궁금해하던 것 해소. 앱은 이미 `reason` 을 보냄 — 서버가 받아 저장 + A에게 전달만 하면 됨.

## 앱이 보내는 것 (이미 배포)
```
POST /api/shared/respond
{ "share_id": ..., "partner_phone": ..., "accept": false, "partner_name": ..., "reason": "일정이 있어요. 다음에 함께 해요!" }
```
- `reason` = 거절 사유. accept=false 일 때만, 있을 때만 옴(사유 없이 거절도 가능 → 필드 없음).
- 프리셋 2개 or 기타(직접 입력, 최대 100자).

## 해달라는 것 (main.py `shared_respond` ~11918)
1. `SharedRespondRequest` 에 `reason: Optional[str] = None` 추가. (PEP 604 금지 — Optional[str])
2. 거절 저장 시 사유도 저장 — `shared_sites` 에 `decline_reason TEXT` 컬럼 추가(마이그레이션),
   `new_status == "declined"` 일 때 `decline_reason = ?` 로 UPDATE. (accept면 무시/NULL)
3. **A에게 FCM 전달** — 이미 보내는 `collab_event`(step=declined) data 에 한 필드 추가:
   ```python
   "decline_reason": (req.reason or "").strip()[:100] if not req.accept else "",
   ```
   앱은 이 값을 받아 알림에 표시함(이미 배선 완료):
   "○○님이 '△△' 협업을 거절했어요. — "일정이 있어요"" (없으면 사유 안 붙음, graceful).
4. (선택) `_shared_site_row_to_dict` 에 `decline_reason` echo → A의 by-me 카드/이력에서도 사유 표시 가능.
   앱 by-me 파서는 지금 이 필드 안 읽으니 지금은 FCM(3번)만으로 충분. echo 넣어두면 후속 UI에서 활용.

## 검증
```
# B 거절(사유 포함) → A 폰에 "거절했어요 — "일정이 있어요"" 푸시 뜨는지
curl -X POST .../api/shared/respond -d '{"share_id":"<sid>","partner_phone":"<B>","accept":false,"reason":"너무 멀어요 죄송해요!"}'
```

## 주의
- reason 은 accept=false 전용. accept=true 엔 무시.
- 기존 거절(사유 없음)도 그대로 동작(필드 없으면 빈 문자열).
