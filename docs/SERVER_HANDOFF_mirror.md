# SERVER HANDOFF — 본폰 "일정 미러 링크" (읽기전용 뷰어)

작성: 2026-07-13 · android(데스크탑 Claude) → cowork(맥미니 서버)
근거 설계: Fable 5 논의 결론(사장님 승인 = MVP 진행). 아래는 서버가 만들 부분.

## 배경 (사장님 요구)
- 사업자 2개 = 업무폰 2대. 두 업무폰의 일정을 **본폰(개인폰) 하나에서 전부** 보고 싶다.
- **읽기전용(뷰어)** — 수정 불가. 본폰엔 **앱 설치 불필요**(링크/PWA로 충분).

## 핵심 사실 (코드 확인됨)
- 일정 원본은 업무폰 로컬 Room DB `customers`(scheduledWorkDate 등)에만 있음. 서버엔 조각(팀배정분/협업/접수서)만.
- 따라서 **업무폰 → 서버로 일정 스냅샷 push** 조각이 필요(앱쪽에서 구현 예정, 아래 §앱 참고).
- **재사용 대상 = 팀원 웹뷰**: `/team/member/{token}` (main.py ~19362) 가 이미 "서버에 박힌 일정 스냅샷 JSON → 모바일 HTML 렌더 + PWA manifest(~19533)". 이걸 사장님 멀티사업장 버전으로 복제-수정하는 게 이 핸드오프의 핵심.

## 서버가 만들 것

### 1) 테이블 `mirror_links`
- `token TEXT PRIMARY KEY` (URL-safe, 팀원 토큰과 동일 생성 방식)
- `main_key TEXT` — 본폰(뷰어) 식별자. MVP엔 랜덤. (여러 사업장을 한 링크에 묶는 그룹키)
- `created_at_ms INTEGER`, `revoked INTEGER DEFAULT 0`
- 별도 `mirror_sources`: `token TEXT, owner_phone TEXT, label TEXT, snapshot_json TEXT, updated_at_ms INTEGER` (한 링크에 사업장 N개 → 각 업무폰의 최신 스냅샷 통째 저장. 팀원 스냅샷과 동일하게 "덮어쓰기" 방식, diff 없음)
- (Python 3.9 — 타입표기 `Optional[str]` 쓸 것. `str | None` 금지 = 502)

### 2) 엔드포인트
- `POST /api/mirror/issue` — body: owner_phone, label(상호). 본폰용 링크 최초 발급(없으면 생성) + 그 사업장을 source로 등록. resp: { token, url }.
- `POST /api/mirror/pair` — 6자리 코드로 두 번째 업무폰을 기존 token에 합치기. (코드 발급/검증: 뷰어가 코드 표시 → 업무폰B가 POST. SMS 불필요 — SOLAPI 꺼져있음)
- `POST /api/mirror/snapshot` — body: token(또는 owner_phone), label, snapshot_json. 해당 source 최신 스냅샷 덮어쓰기 + updated_at_ms 갱신. (팀원 schedule-snapshot 과 동일 컨셉)
- `GET /mirror/{token}` — **읽기전용 HTML** 렌더(팀원 웹뷰 복제 + 아래 차이). manifest 포함.

### 3) `/mirror/{token}` 화면 (팀원 웹뷰에서 변형)
- 버튼/수정 UI **전부 제거**(순수 뷰어).
- 상단 칩: [전체] [사업장1 라벨] [사업장2 라벨] — 사업장별 색점.
- 오늘 섹션 고정 + 월 캘린더 + 날짜 탭 → 그날 현장 카드(고객표시명 · 시간 · 주소 · N일짜리 · 완료✓).
- **"마지막 업데이트: 사업장1 3분 전 · 사업장2 1시간 전"** (각 source updated_at_ms) — 미러 신뢰의 핵심.
- 60초 자동 새로고침(또는 당겨서 새로고침).

### 스냅샷 JSON 스펙 (앱이 보낼 형태 — 합의 필요)
```json
{
  "label": "디테일라인",
  "owner_phone": "010...",
  "items": [
    {"date":"2026-07-15","minutes":600,"days":2,"name":"홍길동","address":"...","completed":false}
  ]
}
```
- **프라이버시 기본값(사장님 미확정 — 아래 질문)**: 이름·주소·시간·완료여부만. 금액/메모/전화번호는 기본 제외. (팀원 웹뷰도 사생활 이유로 메모 제외 — ScheduleViewModel.kt 414 주석)

## 앱쪽(데스크탑 Claude, 다음 세션) — 참고
- 더보기 → [본폰에서 일정 보기] → POST issue → 링크 문자/카톡 공유.
- `MirrorRepository`(TeamRepository 복제) + Application 범위 `observeScheduled()` 구독 → 30초 디바운스 + 해시 비교 시 POST snapshot. + WorkManager 12h 백업 push.
- 두 번째 사업장 = 6자리 코드 입력 화면.

## 사장님 미확정(진행하며 확인) — 서버/앱 공통
1. 본폰에 일정만? 아니면 미수금·오늘 입금 요약도? (2차 확장 후보)
2. 고객 전화번호·메모도 노출? (기본 = 미노출 권장)
3. 링크 보안: 링크만으로 열람 OK vs 뷰어 4자리 비번 1회. (본폰 분실 대비)
4. 개인정보처리방침(si0in.kr/privacy): 미러링은 전체 고객 이름·주소가 서버 상주 → 위탁/보관 문구 한 줄 검토 필요(cowork).
