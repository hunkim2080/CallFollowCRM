# SERVER DONE — 웹 사진 캘린더 (읽기전용 뷰어) · 서버측 완료

cowork(Mac mini) → android(데스크탑) · 2026-08-13
원 핸드오프: `docs/SERVER_HANDOFF_web_photo_calendar.md` (B안 확정본)
서버 구현: `server/main.py` (파일 끝 "웹 사진 캘린더" 섹션) + DB 테이블 3종

---

## 서버가 만든 것 (전부 읽기전용 · TestClient 통과)

DB: `web_schedule_feed`(owner+customer_digits PK), `web_login_tickets`(60초), `web_sessions`(30분 idle).

| 엔드포인트 | 용도 | 인증 |
|---|---|---|
| `POST /api/web/schedule-feed` | **앱이 push** (아래 계약) | owner_phone(body) |
| `GET /api/web/login/ticket` | 노트북이 QR용 티켓 발급 | 없음 |
| `POST /api/web/authorize` | **폰이 QR 스캔 후 호출** (아래 계약) | owner_phone(body) |
| `GET /api/web/login/status?t=` | 노트북 폴링 → 승인 시 쿠키 발급 | 없음 |
| `GET /api/web/calendar?month=YYYY-MM` | 달력(일별 jobCount·hasPhoto) | 쿠키 |
| `GET /api/web/sites?month=YYYY-MM` | 그 달 현장 목록(+photo_count) | 쿠키 |
| `GET /api/web/site/{customer_digits}` | 현장 상세 사진(전/후·uploader) | 쿠키 |
| `GET /api/web/photo/{photo_id}` | 사진 바이트 | 쿠키 |
| `GET /api/web/download?ids=&part=` | zip/단건(파일명 YYYYMMDD_아파트_부위_NN) | 쿠키 |
| `POST /api/web/logout-all?owner_phone=` | **앱 '웹 로그아웃'** | owner_phone |
| `GET /web/login`, `/web`, `/web/authorize` | 웹 페이지(QR로그인·뷰어·앱안내) | 쿠키(/web) |

웹 로그인 흐름 = 노트북이 QR 표시 → **폰이 스캔** → 앱이 `POST /api/web/authorize` → 노트북 폴링이 승인 감지 → 쿠키 발급 → `/web` 이동. (폰=열쇠)

---

## 🔴 앱(android) 담당 — 3가지

### 1) 스케줄 피드 push (캘린더 데이터 소스)
서버엔 고객/일정 테이블이 없음 → 앱이 뷰어용 경량 스냅샷을 덮어쓰기 push.
```
POST https://api.si0in.kr/api/web/schedule-feed
{ "owner_phone": "01012345678",
  "items": [
    { "customer_digits": "01055551234",  // 숫자만. team_site_photos.customer_phone 와 끝8 매칭
      "name": "김OO", "apartment": "행복아파트", "dong_ho": "101-1203",
      "work_date": "2026-08-10",          // YYYY-MM-DD 시공일
      "category": "줄눈", "completed": true } ]
}
```
- **덮어쓰기**: 매 push 시 그 owner 의 기존 피드 전체 삭제 후 items 재삽입(미러 snapshot 방식).
- `photo_count` 는 넣지 마세요 — 서버가 team_site_photos 조인으로 계산.
- push 시점 제안: 미러 스냅샷 push 와 같은 타이밍(일정 변경/앱 재개 등).

### 2) QR 스캔 → authorize
QR 내용 = `https://api.si0in.kr/web/authorize?t={ticket}`. 앱이 이 URL 스캔 시(딥링크 or 카메라):
```
POST https://api.si0in.kr/api/web/authorize
{ "ticket": "{t}", "owner_phone": "01012345678" }   // 이미 로그인된 owner
```
- 200 = 승인 완료 → 노트북이 자동 로그인. 티켓 만료(60초 초과)면 410 → 사용자에게 "웹에서 새 QR" 안내.
- (선택) `session_token` 도 실어 보내면 향후 AUTH_ENFORCE 시 서버가 검증. 지금은 owner_phone 만으로 동작.

### 3) 설정 "웹 로그아웃" 버튼
```
POST https://api.si0in.kr/api/web/logout-all?owner_phone=01012345678
```
→ 그 owner 의 웹 세션 전부 무효(폰 원격 로그아웃). 30분 무동작 자동 로그아웃은 서버가 이미 처리.

---

## 참고 / 남은 판단(cowork)
- **부위 목록**: 프로토의 "사장님이 관리하는 부위 목록"은 서버 저장 안 함(파일명에만 반영). 현재 뷰어는 부위를 **자유 입력칸**으로 뒀음 — 사장님이 목록형(칩) 원하면 알려주면 프론트만 보강.
- **화면 톤**: 프로토(7c06efeb)를 직접 못 열어서, 뷰어는 앱 토스블루 톤으로 **기능 완성** 상태. 시각 세부는 사장님 확인 후 조정 가능.
- **저장 전략(§7)**: 현재 base64(image_data_url) 그대로 서빙. base64→파일·썸네일 최적화는 **이번엔 미적용**(디스크 97% 이슈는 별개 — 빌드찌꺼기 청소 `docs/REPO_SLIMMING.md` 로 우선 확보 권장). 사용자 늘면 ②③④ 순차 적용.
