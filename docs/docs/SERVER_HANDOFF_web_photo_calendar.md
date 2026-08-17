# SERVER_HANDOFF — 시공막내 웹 사진 캘린더 (읽기전용 뷰어)

작성: 2026-08-13 · 안드로이드 Claude(데스크탑) → 코워크(Mac mini 서버)
프로토(시각 SoT): https://claude.ai/code/artifact/7c06efeb-3f7a-4430-979b-65020a4d0d75
관련: `docs/SERVER_HANDOFF_mirror.md`(v1) · `docs/SERVER_HANDOFF_mirror_v2.md` · `docs/SERVER_HANDOFF_collab_expansion.md`

---

## 0. 한 줄 목적

**PC(노트북) 웹에서 시공 캘린더를 열어 → 날짜/현장 클릭 → 그 현장 사진(사장님·팀원·협업 사장이 올린 것)을 보고 → 블로그용으로 골라 다운로드.** 로그인 = 웹에 뜬 QR을 **로그인된 시공막내 폰으로 스캔**(폰=열쇠). **철저히 읽기 전용** — 수정/삭제 엔드포인트 자체를 만들지 않는다(리소스·사고 최소).

사장님 확정 사항:
- 화질 = **1280px 통일, 화질 선택 토글 없음, 선택 없이 바로 다운**. (한 장 ~400KB)
- 전/후 = **올린 시간순 자동 추정**(앞=전·뒤=후). 앱 태그 불필요.
- 부위 = 사장님이 **직접 관리하는 목록**(추가·삭제·순서), 줄눈 전용 아님. **다운로드 파일명에만** 반영(서버 사진 안 건드림).
- 다운로드 파일명 = `시공일(YYYYMMDD)_아파트명_부위_번호`.
- ⚠️ 평형(84A)·재질(폴리우레아/케라폭시)은 **박람회 계약서 전용** 필드 → 일반 시공엔 없음, 넣지 않는다.

---

## 1. 이미 있는 것 (재사용 — 지어내지 말 것)

| 블록 | 무엇 | 위치(grounded) |
|---|---|---|
| **사진 저장** | `team_site_photos` (owner_phone, customer_phone, share_id, member_id, label, image_data_url=base64 ~1MB컷, uploaded_at_ms) | `main.py:786-825` |
| **고객 사진 조회** | `GET /api/site-photos?owner_phone=&customer_phone=` (2-way: owner/member) | `main.py:19440-19535` |
| **협업 사진 조회** | `GET /api/shared/photos` (3-way: owner/**partner**/member) | `main.py:14716-14773` |
| **일정 스냅샷** | `mirror_snapshots.snapshot_json` = items[{date,name,address,phone,completed,total,collab,…}] | 스키마 `main.py:364-372` · 앱 push `MirrorRepository.kt:191-232` |
| **QR 페어(v2)** | QR = URL `…/mirror?code={code}&k={auto_secret}`, 스캔+쿠키 = 자동수락 | `SettingsScreen.kt:1248-1250` · `main.py:22521-22601` |
| **읽기전용 PWA + PIN 게이트 + 60초 JSON 새로고침** | `GET /mirror/{token}` + `POST /mirror/{token}/unlock` + `GET /api/mirror/data/{token}` | `main.py:22032-22069`, `21637-21658` |
| **QR 생성(앱)** | `QrGen.bitmap(text, sizePx)` (zxing) | `QrGen.kt:14-34` |
| **서버 공개 베이스** | `https://api.si0in.kr` | `main.py:16385-16388` |

---

## 2. 채워야 할 구멍 (설계 결정 필요) ⚠️

1. **서버에 고객/일정 테이블이 없다.** 고객 이름·주소·시공일은 앱 로컬 Room DB에만 있고, 서버엔 `mirror_snapshots`(미러 켠 사람만) + `shared_sites`(협업만)에만 조각으로 있음. → **캘린더의 시공일·아파트명 출처를 정해야 함** (§4-A 참고).
2. **"사진 있는 고객 목록"을 주는 엔드포인트가 없다.** `/api/site-photos`는 customer_phone을 이미 알아야 함. → `SELECT DISTINCT customer_phone …` 신규 필요.
3. **사진↔고객 조인이 전화번호 문자열 기반이라 손실**: 사진 `customer_phone`(정확 or 뒤8자리) vs 미러 `phone`(하이픈 포함, customer id 없음). → **숫자만 정규화해서 조인.**
4. **시공 종류(카테고리)·아파트명·동호수 필드가 서버 어디에도 없다.** 미러엔 free-text `address` 하나뿐. → 프로토의 "🏷️ 시공종류"·"아파트명"은 §4-A 결정에 달림.
5. **`/api/site-photos`가 협업 사장(PARTNER)을 "팀원"으로 오분류**(PARTNER 분기 없음, `main.py:19519-19532`). → 협업/`share_id` 사진은 **`/api/shared/photos` 를 쓰거나** PARTNER 파싱을 복제.
6. **미러 v2 세션 = 180일 고정 쿠키, idle timeout 없음, 기본 비번게이트 없음.** 사장님이 원한 **QR 60초 만료 + 30분 자동 로그아웃 + 폰 원격 로그아웃**은 **현재 미러엔 없음 → 신규로 구현**(§5).

---

## 3. QR 로그인 — 방향이 미러와 반대 (신규, 하지만 부품 재사용)

미러: **폰이 QR을 보여주고** 뷰어가 스캔. 이번 사진 뷰어: **노트북(웹)이 QR을 보여주고, 로그인된 폰이 스캔**(= WhatsApp Web 식 "기기 로그인"). 방향만 반대, 부품(서명 쿠키·auto_secret·PWA)은 그대로.

흐름:
```
1) 노트북: GET /web/login  → 서버가 login_ticket 발급(60초 만료) → 페이지에 QR 표시
      QR = https://api.si0in.kr/web/authorize?t={ticket}
2) 폰(시공막내, 이미 로그인=owner): QR 스캔 → 앱이 열림 →
      POST /api/web/authorize { ticket, owner_phone[, session_token] }
      → 서버: ticket 유효(60초내)+미사용 확인 → ticket 을 owner_phone 에 바인딩+authorized
3) 노트북: GET /api/web/login/status?t={ticket} 를 폴링(2초) →
      authorized 되면 서버가 web 세션 쿠키 발급(서명, owner_phone 담음) → 캘린더로 이동
```
- **폰=열쇠**: ticket은 폰이 스캔+인증해야만 authorized. 폰 없으면 노트북은 영원히 pending → 로그인 불가. ✔
- 폰의 owner 증명 = 현재 미러처럼 `owner_phone` 직접(현행 일관). **더 강하게 하려면** 앱에 이미 배선된 세션 토큰(EncryptedSharedPrefs Bearer, commit 08d174e / `project_auth_session_token_wiring`)을 같이 실어 서버가 검증 — AUTH_ENFORCE 켜는 시점에 승격. (지금은 owner_phone 로 시작해도 무방, 읽기전용+본인 사진만이라 위험 낮음.)
- 세션 쿠키 서명 = 미러 v1 패턴 복제(`_mirror_cookie_value` = hmac_sha256(secret, payload), `main.py:21645-21649`).

---

## 4. 신규 엔드포인트 (읽기 전용만)

### 4-A. 캘린더/현장 목록 — **먼저 결정: 데이터 출처**

프로토 좌측 = 월 캘린더(시공 있는 날·📷 표시) + "이 달 시공 현장" 날짜별 목록(최근순: 날짜·아파트명·이름·시공종류·📷장수·완료여부).

**옵션 A (제로 서버작업, 미러 재사용):** `mirror_snapshots.snapshot_json` 그대로 사용.
- 장점: 앱 추가 작업 0. 단점: **미러 켠 사장님만** 됨 + 시공종류 없음 + 아파트명=address free-text + 조인 손실(§2-3).

**옵션 B (권장, 앱이 전용 피드 push):** 앱이 사진 뷰어 전용 경량 스냅샷을 서버에 push.
- 항목당: `{ customer_digits(숫자만), name, apartment(아파트명), dong_ho(동호수), work_date(YYYY-MM-DD), category(시공종류), completed(bool), photo_count }`.
- 장점: 조인 깔끔(customer_digits)·시공종류·아파트명 정확·미러 무관. 단점: 앱쪽 작업(내 담당) + 서버에 `web_schedule_feed` 테이블 1개.
- ⭐**추천 = B.** 프로토가 요구하는 필드(시공종류·아파트명·동호수)를 정직하게 채우려면 B가 맞음. 앱 push는 내(안드로이드)가 담당.

> ✅ **결정(2026-08-13 사장님): B안 확정.** 앱이 `web_schedule_feed` 를 서버에 push, 서버는 이 피드로 캘린더/목록을 그린다. 미러 의존 없음.
>
> **B안 서버측 신규 테이블(코워크):**
> ```sql
> CREATE TABLE IF NOT EXISTS web_schedule_feed (
>   owner_phone     TEXT NOT NULL,
>   customer_digits TEXT NOT NULL,   -- 숫자만(조인키). team_site_photos.customer_phone 와 숫자정규화 매칭
>   name            TEXT,
>   apartment       TEXT,            -- 아파트명
>   dong_ho         TEXT,            -- 동호수
>   work_date       TEXT,            -- 'YYYY-MM-DD' 시공일
>   category        TEXT,            -- 시공종류(줄눈/청소/필름…)
>   completed       INTEGER DEFAULT 0,
>   updated_at_ms   INTEGER NOT NULL,
>   PRIMARY KEY (owner_phone, customer_digits)
> );
> ```
> 앱 push 엔드포인트: `POST /api/web/schedule-feed { owner_phone, items:[{customer_digits,name,apartment,dong_ho,work_date,category,completed}] }` (upsert, 미러 snapshot 과 같은 "덮어쓰기" 방식). `photo_count` 는 서버가 team_site_photos 조인으로 계산(피드에 안 넣음).

신규:
```
GET /api/web/calendar?month=YYYY-MM     → { days:[{date, jobCount, hasPhoto}], … }  (쿠키 필요)
GET /api/web/sites?month=YYYY-MM        → { sites:[{customer_digits, name, apartment,
                                               dong_ho, work_date, category, completed,
                                               photo_count}], … } 최근순
```

### 4-B. 현장 상세 사진
```
GET /api/web/site/{customer_digits}     → { customer:{name,apartment,dong_ho,work_date,category,completed},
                                            photos:[{ photo_id, url(1280), thumb_url(~400),
                                                      uploader_kind(owner|member|partner),
                                                      uploader_name, uploaded_at_ms,
                                                      ba_guess(before|after) }] }
```
- `ba_guess` = 서버가 uploaded_at_ms 시간순으로 앞/뒤 자동 분류(전/후). 앱 태그 불필요.
- **uploader_kind 3-way는 `/api/shared/photos` 로직(`main.py:14752-14763`)을 재사용**하고, 일반 고객 사진은 `/api/site-photos` — 단 §2-5 오분류 주의(PARTNER 분기 복제).
- 협업 사장이 올린 것도 반드시 표시(사장님 명시). uploader_kind=partner 뱃지.

### 4-C. 다운로드 (zip / 단건, 1280 통일)
```
GET /api/web/download?ids=1,2,3&part=거실화장실   (쿠키 필요)
```
- 여러 장 → zip, 파일명 각 `YYYYMMDD_아파트명_부위_NN.jpg`, zip명 `YYYYMMDD_아파트명.zip`.
- `part` = 웹에서 원클릭한 부위(사장님 목록). **파일명에만** 반영, 서버 사진 불변.
- 1장 → 그대로 jpg. **화질 1280 통일**(선택 토글 없음).
- 부위 미선택 사진은 `번호`만.

---

## 5. 보안 (사장님 명시 — 미러엔 없어서 신규)

- **QR 60초 만료**: login_ticket TTL 60초. 만료 시 노트북 QR 자동 갱신.
- **30분 무동작 자동 로그아웃**: web 세션 쿠키에 last_active, `/api/web/*` 호출마다 갱신, 30분 초과 시 401 → 재로그인. (미러의 180일 고정과 다름 — 이 뷰어는 짧게.)
- **폰 원격 로그아웃**: 앱 설정에서 "웹 로그아웃" → `POST /api/web/logout-all?owner_phone=` → 그 owner의 web 세션 전부 무효.
- **읽기 전용**: 수정/삭제/업로드 엔드포인트를 **만들지 않는다.** (사진 삭제는 기존 폰 앱 경로만.)

---

## 6. 담당 분담

| 일 | 담당 |
|---|---|
| 신규 서버 엔드포인트(§3·§4·§5), web PWA 페이지, zip 생성, 세션쿠키 | **코워크(서버)** |
| (옵션 B면) 앱→서버 전용 스케줄 피드 push, 앱 설정 "웹 로그아웃" 버튼, QR 스캔→`/api/web/authorize` | **안드로이드(나)** |
| 프로토 = 시각 SoT | 완료(7c06efeb) |

---

## 7. 저장 전략 (별건, 사용자 증가 대비 — 코워크 판단)

현재: 사진을 `cache.db` 안에 **base64로**(`team_site_photos.image_data_url`). base64 = +33% + DB 비대. **맥미니 디스크 이미 97% 참(2026-08-13 실측, 남은 14GB·사진 아닌 다른 데이터가 421GB)** → 여유 ~2.5만 장뿐.

권장 순서(급하지 않으면 ②③만이라도):
1. (이미 됨) 업로드 1MB 컷 — 원본 안 받음.
2. **base64 → 디스크 파일**(`image_path` 컬럼 이미 있음, DB엔 경로만) → DB 날씬·메모리↓·33% 절약.
3. **목록용 썸네일(~400px)** 생성 → 그리드/목록 로딩 데이터↓.
4. 사용자 많아지면 → **Cloudflare R2**(CF 터널 이미 씀·egress 공짜) 로 파일 이전 → 맥미니 과부하 해소.

---

## 8. ⭐ 결정 현황

1. ✅ **캘린더 데이터 출처 = B (앱 전용 피드 `web_schedule_feed`).** 확정 2026-08-13. → §4-A.
2. 🔵 QR 로그인 폰 증명: **owner_phone 로 시작**(현행 미러와 일관, 읽기전용+본인사진이라 위험낮음). AUTH_ENFORCE 켤 때 세션토큰 검증으로 승격. → 별도 결정 불필요, 기본값으로 진행.
3. 🔵 저장 전략 §7(②base64→파일 ③썸네일): **코워크 판단.** 사진 뷰어 만들면서 ②③를 같이 하면 이득(자연스러운 타이밍). R2(④)는 사용자 증가 후.

**→ 코워크 착수 가능.** 막히거나 스펙 충돌 의심 시 `docs/SYNC.md` 에 "의문" 블록 append.
