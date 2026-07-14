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
  code        TEXT UNIQUE,         -- 고정 코드(서버 생성, 안 바뀜)
  label       TEXT,                -- 본폰 달력에 보일 이름(예: 디테일라인)
  tint        INTEGER,             -- 색 index
  updated_at_ms INTEGER
)
-- ⚠️ code 발급 규칙(사장님 우려 = "다들 같은 /mirror 주소인데 코드 겹치면?"):
--    ① 전역 UNIQUE 필수. 생성 시 충돌 나면 재추출(INSERT OR retry). 절대 중복 발급 금지.
--    ② 길이 = **8자리 이상 권장**(10만 사용자×1~2코드 → 6자리 1M 공간은 빡빡+추측 쉬움). 8자리 숫자(1억) 또는 영숫자면 충분.
--    ③ 순차/예측 가능 금지(랜덤). 추측 스팸은 수락 게이트가 막지만, 코드 공간이 크면 그 시도조차 무의미해짐.
--    (데이터 격리는 코드가 아니라 home_phone 쿠키 + accepted share 로 보장 — 코드는 "누구 업무폰이냐" 지목용 + 수락 트리거일 뿐)
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
   - 고정 코드 조회/생성(idempotent). 없으면 새로 만들어 저장, 있으면 그대로 + label·tint 갱신. 앱이 "내 공유 코드"+QR 표시용으로 호출.
   - **응답에 `qrUrl` 추가(2026-07-15 사장님 확정 = 하이브리드)**: 앱은 이 qrUrl로 QR을 그림. qrUrl엔 **자동수락 시크릿**을 담아라(아래 §7 QR자동수락). 예: `https://api.si0in.kr/mirror?code={code}&k={autoSecret}`. (없으면 앱이 `homeUrl?code=` 로 폴백 → 그 경우 자동수락 안 됨.)
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
   - **`?code=XXXX` 지원(QR용, 사장님 결정 2026-07-15)**: 앱이 업무폰 화면에 `https://api.si0in.kr/mirror?code={code}` QR을 띄움 → 본폰 카메라로 찍으면 이 페이지가 열리며 **코드 입력칸이 자동 채워짐**. (40~50대가 URL 타이핑·네이버 검색 못 하는 문제 해결. URL은 사람이 안 침 = QR로만.) 본폰 번호는 최초 1회만 물어보고 쿠키 기억.
7. `POST /api/mirror/join` `{home_phone, code, k?}` → `{ok, status, label}`
   - **하이브리드 자동수락(2026-07-15 사장님 확정):**
     - **QR로 들어옴(유효한 `k`=autoSecret 동봉) → 즉시 `status='accepted'`** (수락 단계 건너뜀). 이유: QR은 업무폰 화면을 봐야만 찍을 수 있어 이미 승인으로 간주. 사장님 본인 2폰은 원터치.
     - **코드를 손으로 쳐서 들어옴(`k` 없음) → `status='pending'`** (기존대로 업무폰에서 수락 필요). 추측·유출 방어선 유지.
   - **거절 후 재연결 반드시 허용(2026-07-15 버그 수정):** 이전에 rejected여도 재신청/재QR 되게. **영구차단 금지**(사장님이 실수로 거절하거나 나중에 다시 붙이고 싶을 때 막히는 문제). 도배 방지는 짧은 rate-limit/쿨다운으로만.
   - 본폰엔 status에 따라 "연결됐어요"(accepted) / "신청함(수락 대기)"(pending) 표시.
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

## 보안 (하이브리드 기준, 2026-07-15)
- **QR 경로(k 포함) = 자동수락.** QR은 업무폰 화면을 봐야 찍음 = 물리적 승인. autoSecret(k)는 추측 불가능한 랜덤(코드와 별개). qrUrl/코드가 사진으로 유출되면 그 사람도 붙을 수 있으나(1인 사장 본인 데이터), 업무폰 "공유중"에서 언제든 해제.
- **손입력 코드 경로(k 없음) = 수락 필요.** 6자리 코드는 추측 가능성 있어 반드시 게이트. 코드 유출돼도 수락 안 하면 무해.
- join rate-limit(IP당 10회/10분). **rejected여도 재신청 허용**(영구차단 금지 — 재연결 막힘 버그). 도배는 쿨다운으로만.
- home_phone = 미검증 이름표. 실제 게이트 = (QR)autoSecret / (손입력)업무폰 수락 + 본폰 쿠키.
- 본폰 열기 비번 = 선택(기본 없음). 켜면 /board 에 비번 게이트.

## 마이그레이션
- v1 엔드포인트(issue/pair/pair/code/revoke) + `GET /mirror/{token}` = **존치**(현재 사장님 폰 배포본 호환). 새 앱 실기검증 후 제거 예정.
- 새 앱은 v2만 사용.

## 앱쪽 상태 (이 커밋)
- MirrorRepository = mycode/shares/respond/disconnect (+ snapshot 유지). issue/pair/revoke 제거.
- 미러 설정 화면 = 옵트인 토글 + "내 공유 코드" + 수락/거절 + 공유중/해제. 신청 폴링(포그라운드+워커) → 알림. QR은 [본폰 추가]로 기본 접힘.
- owner_phone = AppPreferences.bizPhone.

## 2026-07-15 추가 요청 (본폰 뷰어 = cowork · 실사용 중 발견)
### A. ★버그: 업무폰 [공유 해제] 눌러도 본폰 화면에 계속 보임
- 재현: 업무폰 앱에서 `POST /api/mirror/disconnect {owner_phone, share_id}` 호출(공유중 목록에서 해제) → 업무폰 UI에선 빠짐. **그런데 본폰(GET /mirror 뷰어)엔 그 사업장 일정이 계속 보임.**
- 앱쪽은 정상(disconnect 호출 + shares 재조회로 업무폰 목록 갱신됨). **서버 board/disconnect 문제로 추정.**
- 확인 포인트: ① disconnect 가 share.status 를 accepted→해제로 실제 바꾸는지 ② `GET /api/mirror/board` 가 **accepted 상태인 owner 만** 합치는지(해제된 건 제외) ③ 본폰 뷰어가 캐시(localStorage/쿠키)로 옛 스냅샷을 계속 그리는 건 아닌지(해제 시 즉시 반영 or 다음 refresh 때 사라지게) ④ 해제된 owner 의 mirror_snapshots 를 board 가 무시하는지.
- 기대: 업무폰 해제 → 본폰에서 그 사업장 일정이 (늦어도 다음 60초 refresh에) 사라짐.

### B. 본폰 뷰어 "일정 공유 코드 입력" 칸 = 기본 접힘 (사장님 진짜 요청)
- 사장님 원문: "일정 공유 코드 입력은 한 번 쓰던지, 폰이 더 늘어나면 쓰는 건데 **너무 큰 자리를 차지**한다. 디자인 개선 필요." → 이건 **본폰(GET /mirror) 화면의 코드 입력 박스**를 가리킴(업무폰 아님).
- 요청: 본폰 달력 화면에서 코드 입력 박스를 **평소엔 접어두고**, 캘린더/일정을 위주로 크게. **[+ 사업장 추가]** 같은 작은 버튼/링크만 두고, 누르면 코드 입력칸 펼침(첫 연결·폰 추가 때만 씀).
- 즉 본폰 화면 = 통합 캘린더가 주(主), 코드 입력은 접힌 보조. (업무폰 앱도 같은 취지로 QR/코드를 [본폰 추가]로 접어둠 — 참고.)

### C. 본폰 뷰어 달력 = 좌우 스와이프로 월 이동 (사장님 요청)
- 사장님: "이 달력도 업무폰 일정처럼 휙~ 넘기는(제스처) 기능 없어?" → 앱 일정 캘린더는 좌우 스와이프로 월 넘김. 본폰 뷰어 달력도 동일하게.
- 요청: 본폰 월 캘린더에서 **손가락 좌 스와이프 → 다음 달 / 우 스와이프 → 이전 달**. (기존 `< >` 버튼은 유지, 스와이프 추가.) 웹 touchstart/touchend(또는 pointer) 로 가로 드래그 감지, 세로 스크롤과 충돌 안 나게 임계치(예: |dx|>50 && |dx|>|dy|). 월 전환 시 board 데이터는 이미 다 받아둔 것 재렌더(추가 fetch 불필요).
