# SERVER HANDOFF — 협업 증거사진 개별 삭제 (`POST /api/shared/photo/delete`)

작성: android Claude · 2026-07-07 · 담당: cowork(맥미니)

## 왜
사장님이 협업 현장에 올린 증거사진을 **개별 삭제**하고 싶어함. 앱은 이미 붙였음:
- 내가 올린 사진에만 우상단 ✕ → 확인 다이얼로그 → `POST /api/shared/photo/delete` 호출.
- 서버 엔드포인트가 아직 없어서(현재 upload=⑩ `/api/shared/photo`, list=⑪ `/api/shared/photos` 만 존재) 지금 누르면 앱이 "사진 삭제 실패" 토스트만 뜸.

## 앱이 보내는 요청 (이미 배포됨 — `SharedSiteRepository.deletePhoto`)
```
POST /api/shared/photo/delete
{ "share_id": "<share_id>", "photo_id": <int>, "partner_phone": "<요청자 phone, 숫자만>" }
```
- `partner_phone` = **삭제 요청자(=업로더 본인) phone**. upload(⑩)와 같은 필드명.
- 앱은 **내가 올린 사진에만** ✕ 를 보여주므로 요청자는 항상 그 사진의 업로더. 그래도 서버가 검증할 것.

## 저장 구조 (기존 ⑩ upload 참고 — `main.py` ~12493)
사진은 `team_site_photos` 에 있고, 업로더는 `member_id` 로 구분:
- `member_id == "OWNER"` → 업로더 = 그 share 의 `shared_sites.owner_phone` (A)
- `member_id == "PARTNER:{phone}"` → 업로더 = 그 `{phone}` (B)

## 구현 (제안 — 그대로 붙여도 됨)
`/api/shared/photos` (⑪) 바로 아래에 추가:

```python
# ─── ⑪-bis POST /api/shared/photo/delete ───  (2026-07-07 사장님 — 개별 삭제)
# 협업 증거사진 삭제. 올린 본인만. (앱은 내가 올린 사진에만 ✕ 노출)
class SharedPhotoDeleteRequest(BaseModel):
    share_id: str
    photo_id: int
    partner_phone: str            # 요청자(=업로더) phone

@app.post("/api/shared/photo/delete")
async def shared_photo_delete(req: SharedPhotoDeleteRequest) -> dict:
    share_id = (req.share_id or "").strip()
    requester = _norm_phone(req.partner_phone)
    if not share_id or not req.photo_id or not requester:
        raise HTTPException(400, "share_id, photo_id, partner_phone 필수")
    with db_conn() as con:
        srow = con.execute(
            "SELECT owner_phone, partner_phone FROM shared_sites WHERE share_id = ?",
            (share_id,),
        ).fetchone()
        if not srow:
            raise HTTPException(404, "share_id 없음")
        share_owner = _norm_phone(srow[0])
        prow = con.execute(
            "SELECT member_id FROM team_site_photos WHERE photo_id = ? AND share_id = ?",
            (req.photo_id, share_id),
        ).fetchone()
        if not prow:
            raise HTTPException(404, "photo 없음")
        member_id = prow[0] or ""
        # 업로더 판정
        if member_id == "OWNER":
            uploader = share_owner
        elif member_id.startswith("PARTNER:"):
            uploader = _norm_phone(member_id.split(":", 1)[1])
        else:
            uploader = ""
        if requester != uploader:
            raise HTTPException(403, "권한 없음 (올린 본인만 삭제 가능)")
        con.execute(
            "DELETE FROM team_site_photos WHERE photo_id = ? AND share_id = ?",
            (req.photo_id, share_id),
        )
        con.commit()
    print(f"[shared/photo/delete] share={share_id} photo_id={req.photo_id} by={requester}")
    return {"ok": True}
```

## 검증
```
curl -X POST http://100.86.114.49:8000/api/shared/photo/delete \
  -H 'Content-Type: application/json' \
  -d '{"share_id":"<sid>","photo_id":<id>,"partner_phone":"<업로더번호>"}'
# → {"ok":true}  (남의 사진이면 403, 없으면 404)
```
그다음 앱에서 ✕ → "삭제" → 사진이 목록에서 사라지고 "사진을 삭제했어요" 토스트.

## 주의
- 물리 삭제(DELETE). 소프트 삭제 필요하면 알려줄 것(앱은 그대로 동작).
- PEP 604 금지 — `Optional[str]` 스타일 유지(이 엔드포인트엔 해당 필드 없음).
