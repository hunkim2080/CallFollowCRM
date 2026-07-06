# SERVER HANDOFF — 시공접수서 버그 3건 (2026-07-06, 사장님 보고)

android 세션에서 진단. 3건 모두 **서버(cowork) 작업 필요**. 앱 쪽은 정상이거나 서버 계약 확정 후 배선.

---

## ① [P0] 시공일 날짜 -1 버그 (원인 확정)

**증상:** 사장님이 접수서에 시공일 16일을 고르면, 고객 폼/확인에 **15일**로 뜸. (재현: 3회 일관)

**원인:** `server/main.py` 의 `_workdate_to_epoch_ms(year, month, day)` (약 line 14963):
```python
dt = datetime.datetime(year or 2026, month, day, 0, 0, 0)      # naive (tz 없음)
return int((dt - datetime.timedelta(hours=9)).timestamp() * 1000)
```
- `- timedelta(hours=9)` 로 **손으로 9시간 빼고**, `.timestamp()` 가 **naive dt 를 서버 로컬(KST)로 해석**하며 **또 9시간을 더함** → 이중 보정.
- 맥미니 서버 TZ = Asia/Seoul(KST) 이므로: `1/16 00:00` → `1/15 15:00 KST` 로 저장 → 날짜 렌더 시 **1월 15일**. (즉 -9시간 → 전날)
- (서버가 UTC 였다면 우연히 맞았음. KST 라서 깨짐.)

**고칠 함수 (TZ 무관하게 안전):**
```python
_KST = datetime.timezone(datetime.timedelta(hours=9))
def _workdate_to_epoch_ms(year, month, day):
    if not month or not day:
        return 0
    try:
        dt = datetime.datetime(year or 2026, month, day, 0, 0, 0, tzinfo=_KST)
        return int(dt.timestamp() * 1000)
    except (ValueError, OverflowError):
        return 0
```
→ `scheduled_at_ms` 가 정확한 KST 자정이 됨. 폼/확인 날짜 정상.
**주의:** 이 함수로 만든 기존 `scheduled_at_ms` 데이터는 이미 -9h 저장돼 있음. 새 발급부터 정상. (필요시 마이그레이션은 사장님과 상의)

앱 확인: 앱은 `Calendar.getInstance(Asia/Seoul).set(y, m-1, d)` (IntakeSyncManager) 로 **정상** 변환. work_day 정수도 정확히 16 전송. 서버 함수만 문제.

---

## ② [P1] 접수서 재발행 시 링크가 매번 새로 생성

**증상:** 날짜 등 잘못 입력해 재발행하면 `POST /api/quote/issue` 가 **매번 새 token/url** 생성 → 고객·사장님 둘 다 "어느 링크 눌러야?" 혼란. 사장님 요청: **링크는 하나, 내용만 갱신**.

**제안(서버):** issue 를 **upsert** 로.
- `POST /api/quote/issue` 에 같은 고객(devicePhone + customerPhone)의 **아직 제출 안 된(submitted_at NULL)** 폼이 있으면 → **그 token 을 재사용**하고 필드만 UPDATE, **같은 url 반환**.
- 이미 제출된 폼이 있으면 → 새로 생성(제출본 덮어쓰기 방지).
- 앱은 변경 최소 — 서버가 phone 기준 dedup 하면 앱은 그대로 호출만. (또는 res 에 `reused: true` 내려주면 앱이 "기존 링크가 갱신됐어요" 안내 가능)

**앱 쪽(선택):** 재발행 시 "이미 보낸 접수서가 갱신됐어요 — 고객은 같은 링크에서 새 내용을 봐요" 토스트. 서버 계약 확정 후 배선.

---

## ③ [P1] 접수서 메모란 (특이사항) 추가

**요청:** 사장님이 접수서에 **특이사항 메모**(예: "현관 비번 1234#", "2층 화장실만")를 적을 칸.

**서버:**
- `intake_forms` 에 `owner_memo TEXT` 컬럼 추가.
- `QuoteIssueRequest` 에 `ownerMemo: str = ""` 필드 추가 → INSERT/UPDATE 저장.
- 고객 폼(`/q/{token}`, `/intake/{token}` HTML)에 값 있으면 **"특이사항" 섹션** 표시(프로토 톤).
- (선택) `/api/quote/submissions` 응답에 echo → 앱 타임라인 카드에도 표시.

**앱:** EstSheet 의 "accept"(시공접수서) 모드에 메모 입력칸 추가 → `issueQuote(...)` 에 `ownerMemo` 전달. (IntakeFormRepository/ChatViewModel/EstSheet 배선 — 서버 필드명 `ownerMemo` 확정 시 android 세션에서 처리.)

---

## 우선순위
①(날짜) = P0, 한 함수 수정으로 즉효. ②③ = P1, 사장님 편의. 서버 배포 후 SYNC 에 남겨주면 android 가 ②③ 앱측 배선.
