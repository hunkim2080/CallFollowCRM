# SERVER_HANDOFF — 웹 뷰어에 "협업 사장(직원) 사진" 포함

작성: 2026-08-13 · android → cowork
관련: `docs/SERVER_HANDOFF_web_photo_calendar_SERVER_DONE.md` · commit fdc3815(owner 정규화 fix)

---

## 배경 (왜 필요)

사장님 실제 워크플로우 = **본인이 사진 안 올림.** 직원을 **협업 사장(partner)** 으로 등록 → 그 직원이 협업 현장에 사진 올림.
- 그 사진은 `team_site_photos` 에 **`customer_phone = NULL`, `share_id = X`, `member_id = 'PARTNER:{폰}'`** 로 저장됨(협업 업로드 = `/api/shared/photo`).
- 근데 웹 뷰어 `_web_photo_bucket`(main.py:25878)은 **`WHERE owner_phone=? AND customer_phone IS NOT NULL`** → **협업 사진(customer_phone=NULL)을 전부 제외** → 웹에 안 뜸.
- 실측: 고객 121 = share `sh_R6alteLqqo` 에 사진 **10장** 있는데 웹 `/api/web/site` 엔 안 보임.

**서버는 협업 share 의 고객(customer_phone)을 모름** — 협업 시 고객 정보를 파트너에게 숨기려고 `shared_sites` 에 customer_phone 을 안 넣음(벽). 이 매핑은 **앱만** 가지고 있음(collabAssignments).

---

## 앱이 이미 한 것 (배포됨 · 아래 커밋)

`POST /api/web/schedule-feed` 의 각 item 에 **`share_ids`** 추가(그 고객에 연결된 협업 share 목록). 없으면 키 생략.
```json
{ "owner_phone": "01064610131",
  "items": [
    { "customer_digits": "01012345678", "name":"김OO", "apartment":"...", "work_date":"2026-08-15",
      "category":"줄눈", "completed": true,
      "share_ids": ["sh_R6alteLqqo", "sh_eHKtxdK1Zw"] }    // ← 신규(옵션). 이 고객의 협업 share 들
  ]
}
```
- 출처 = 앱 collabAssignments(`customerId|파트너폰|파트너이름|shareId`). 한 고객이 여러 파트너와 협업하면 share 여럿.

---

## 🔴 코워크가 할 것 (서버)

### 1. 피드 push 시 share_id → customer_digits 매핑 저장
`web_schedule_feed_push`(main.py:25938)에서 각 item 의 `share_ids` 를 저장. 매 push = 덮어쓰기(기존 피드처럼).
제안 테이블:
```sql
CREATE TABLE IF NOT EXISTS web_feed_shares (
  owner_phone     TEXT NOT NULL,   -- _norm_phone
  share_id        TEXT NOT NULL,
  customer_digits TEXT NOT NULL,   -- 이 share 가 붙을 고객(끝8 조인용)
  PRIMARY KEY (owner_phone, share_id)
);
```
push 시: `DELETE FROM web_feed_shares WHERE owner_phone=?` 후, item 마다 `for sid in item.share_ids: INSERT (owner, sid, item.customer_digits)`.

### 2. `_web_photo_bucket` 이 협업 사진도 포함 (핵심)
현재 `WHERE owner_phone=? AND customer_phone IS NOT NULL` 에 **협업 경로 추가**:
- (A) 기존: customer_phone 있는 사진 → `_web_pkey(customer_phone)` 키로 버킷(그대로).
- (B) 신규: **share_id 있는 사진** → team_site_photos `WHERE owner_phone=? AND share_id IS NOT NULL` 조회 →
  각 사진의 share_id 를 `web_feed_shares` 로 **customer_digits 조회** → `_web_pkey(customer_digits)` 키로 **같은 버킷에 합침**.
- owner_phone 비교는 fdc3815 정규화 그대로. 협업 사진 owner_phone 은 이미 `_norm_phone`(shared_photo_upload) 이라 매칭됨.
- 정렬: 기존처럼 `uploaded_at_ms ASC`(전/후 자동추정 유지). A+B 합쳐 시간순.

### 3. uploader_kind = partner 표시
협업 사진 member_id = `'PARTNER:{폰}'`. 웹 site 응답 uploader_kind 를 `/api/shared/photos`(main.py:14752-14763) 3-way 로직처럼:
- `OWNER`→owner("사장님") · `PARTNER:`→partner(`_is_registered_owner(폰)` or "협업 사장") · 그 외→member("팀원").
(SERVER_DONE 에 "uploader 3-way" 라고 돼있으니 이미 됐으면 확인만.)

### 4. photo_count / hasPhoto 도 포함
`web_sites`(26087)·`web_calendar` 의 photo_count·hasPhoto 계산에도 위 B(협업 사진)를 포함 → 달력 📷·현장목록 장수에 반영.

---

## 검증 방법
1. 앱이 피드 push(협업 있는 고객 포함) → `web_feed_shares` 채워짐.
2. `GET /api/web/site/{customer_121_digits}` (쿠키) → 그 share 의 협업 사진 10장이 `uploader_kind:"partner"` 로 뜸.
3. `web_sites` 의 그 고객 photo_count = 10.

## 참고
- 앱 커밋: (SYNC 참조). owner-upload(사장님 본인) 백필도 그대로 유지(가끔 직접 올릴 때).
- 실데이터: 사장님(010-6461-0131) collabAssignments 20+ (파트너 = 해시 01021972496 · 디테일라인 01080056674). 고객 121·107·141·162·168 등에 협업 share 붙음.
