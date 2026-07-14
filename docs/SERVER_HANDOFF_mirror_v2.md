# SERVER 핸드오프 — 본폰 미러 v2 "공유 신청/수락" 방식 (android → cowork)

작성: android · 2026-07-14 · **사장님 확정**. Fable 5 설계 패널 + 사장님 refine.
기존 v1(issue/pair/pin, docs/ANDROID_HANDOFF_mirror_app.md)을 **대체**. v1은 새 앱 실기검증까지 존치 후 제거.

## 왜 바꾸나 (사장님 결정)
v1의 "업무폰A 코드 발급 → 업무폰B 입력"이 너무 헷갈림. 새 방식은 **협업 현장 요청(수락/거절) 시스템과 동일 컨셉** — 이미 있는 부품 재활용.

## 개념 (한 줄)
> **본폰(빈 달력)이 업무폰의 고정 코드를 입력해 "공유 신청" → 업무폰이 수락 → 공유. 규칙은 항상 "업무폰이 코드 만들고, 본폰이 넣는다."**

- **본폰** = api.si0in.kr/mirror 고정 주소의 **빈 달력**(웹, 앱 없음). 아무나 열 수 있음(누가 링크 발급 안 해줘도 됨). 처음 열면 "본폰 번호" 1회 입력(= 신청자 이름표).
- **업무폰** = 안드로이드 앱. owner_phone. **고정 공유 코드**(안 바뀜)를 앱에 항상 표시.
- **비번**: 기본 없음(옵션). 보안은 "수락 안 하면 안 보임"으로 확보.

## 데이터 모델 (서버)
```
mirror_codes(
  owner_phone TEXT PRIMARY KEY,   -- 업무폰(_norm_phone)
  code        TEXT UNIQUE,         -- 고정 6자리(서버 생성, 충돌 안 나게). 안 바뀜
  label       TEXT,                -- 본폰 달력에 보일 이름(예: 디테일라인)
  tint        INTEGER,             -- 색 index
  updated_at_ms INTEGER
)
mirror_shares(
  id          INTEGER PRIMARY KEY,
  owner_phone TEXT,                -- 어느 업무폰(mirror_codes.owner_phone)
  home_phone  TEXT,                -- 신청한 본폰 번호(_norm_phone, 이름표·미검증)
  status      TEXT,                -- 'pending' | 'accepted' | 'rejected'
  created_at_ms INTEGER,
  decided_at_ms INTEGER            -- 수락/거절 시각
)
-- 스냅샷: v1의 mirror_sources 재사용(owner_phone 키). token 컬럼 불용.
mirror_sources(owner_phone PK, label, tint, snapshot_json, money_json, updated_at_ms)
-- 본폰 정체성 = home_phone 서명 쿠키. (선택) mirror_home(home_phone PK, pin_hash, salt)
```
- **UNIQUE(owner_phone, home_phone)** on mirror_shares (같은 조합 재신청은 기존 row 갱신). Python 3.9 → `Optional[...]` 표기.

## 엔드포인트 — 업무폰(앱)이 호출
1. `POST /api/mirror/mycode` `{owner_phone, label, tint}` → `{code, label, tint}`
   - 고정 코드 조회/생성(idempotent). 없으면 새 6자리 만들어 저장, 있으면 그대로 + label·tint 갱신. 앱이 "내 공유 코드: 000000" 표시용으로 호출.
2. `GET /api/mirror/shares?owner_phone=X` → `{pending:[{id, home_phone, created_at_ms}], accepted:[{id, home_phone, since_ms}]}`
   - 수락 대기 신청 + 현재 공유중 목록. 앱이 폴링(포그라운드 + 워커).
3. `POST /api/mirror/respond` `{owner_phone, share_id, accept}` → `{ok}`
   - accept=true → status='accepted'(그 본폰이 이 업무폰 일정을 봄). false → 'rejected'.
4. `POST /api/mirror/disconnect` `{owner_phone, share_id}` → `{ok}`
   - 공유중 해제 → 그 본폰 달력에서 이 사업장이 빠짐(status='rejected' 또는 row 삭제).
5. `POST /api/mirror/snapshot` `{owner_phone, label, items, money}` → 기존 유지.
   - items[]에 이미 추가됨(2026-07-14): `total`(원, 총금액), `phone`(하이픈 포함), date/time/days/name/address/memo/completed. money={todayIn,unpaid,unpaidCount}.

## 본폰(웹) — 서버가 렌더 + 본폰 전용 엔드포인트
6. `GET /mirror` — **빈 달력 페이지**. 쿠키(home_phone) 없으면 "본폰 번호 입력" → 서명 쿠키 set. 화면에 **[일정 공유 코드 입력]** 칸.
7. `POST /api/mirror/join` `{home_phone, code}` → `{ok, status, label}`
   - 그 code의 owner_phone에 **pending share 생성**(이미 accepted면 그대로, rejected였으면 재신청 rate-limit). 본폰엔 "○○에 공유를 신청했어요(수락 대기)" 표시.
8. `GET /api/mirror/board?home_phone=X` (쿠키 필수) → accepted된 owner_phone들의 snapshot/money를 **합산**해 통합 캘린더 데이터. 60초 새로고침.
   - 표시 개선(2026-07-14 SYNC): 현장별 **총금액**(items.total), 주소 탭 → **지도앱**(T맵/네이버/카카오, 주소 문자열 검색 URL), 전화 **하이픈**.
9. (선택) `POST /api/mirror/home-pin` `{home_phone, pin}` — 본폰 열기 비번 설정(기본 없음).

## 상태 흐름
```
본폰: /mirror 열기 → 번호 입력(1회) → [코드 입력]에 업무폰 코드 → POST /join → "신청함(대기)"
업무폰(앱): GET /shares 폴링 → pending 뜸 → 알림 "010-xxxx가 일정 공유 신청 [수락][거절]"
           → POST /respond(accept) → 공유 시작
본폰: /board 가 그 업무폰 일정 포함해 렌더(수락 후)
업무폰: "📅 010-xxxx와 공유중" + [일정 공유 해제하기] → POST /disconnect
```

## 보안
- 코드 유출돼도 **수락 안 하면 무해**(신청만 뜸). 업무폰(사장님 기기)에서만 수락.
- join rate-limit(IP당 10회/10분). **거절(rejected)한 home_phone 재신청 차단**(도배 방지).
- home_phone = 미검증 이름표(수락 화면에 "누가 신청했나" 보여주기용). 실제 게이트 = 업무폰 수락 + 본폰 쿠키.
- 본폰 열기 비번 = 선택(기본 없음). 켜면 /board 에 비번 게이트.

## 마이그레이션
- v1 엔드포인트(issue/pair/pair/code/revoke) + `GET /mirror/{token}` = **존치**(현재 사장님 폰 배포본 호환). 새 앱 실기검증 후 제거 예정.
- 새 앱은 v2만 사용.

## 앱쪽 상태 (이 커밋)
- MirrorRepository = mycode/shares/respond/disconnect (+ snapshot 유지). issue/pair/revoke 제거.
- 미러 설정 화면 = 옵트인 토글 + "내 공유 코드" + 수락/거절 + 공유중/해제. 신청 폴링(포그라운드+워커) → 알림.
- owner_phone = AppPreferences.bizPhone.
