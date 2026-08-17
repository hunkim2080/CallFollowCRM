# SERVER HANDOFF — 협업 사장(B) 확장 + 일당 모집

- 작성: 2026-06-13 · android Claude → 맥미니 서버 Claude
- 출처(SoT, verbatim): `design-preview/collab-sites-proto.html` (사장님 승인 완료)
- 기존 토대: `docs/SERVER_HANDOFF_collab_sites.md` (§27 협업 endpoint 6종) + `push_tokens`(FCM) + 팀 웹뷰
- 원칙: 모든 신규 FCM 은 **data-only**(앱이 한국어 문구/탭 처리). 기존 `push_tokens` 사용. 개인정보 벽 유지(고객 번호·대화·타 고객 절대 미노출).

이 문서는 "협업 사장이 왜 이 앱을 쓰는가"를 끝까지 굴리기 위한 서버 작업 묶음이다. 대부분이 서버 의존이라 **이 핸드오프가 크리티컬 패스**.

---

## A. 일당 (daily wage) — 작음, 먼저

- 프로토: `a-share`(A 입력칸 "그날 일당"), `b-list`/`b-detail` 보라 태그 `일당 25만`, `a-after` 일당 표시.
- `POST /api/shared/invite` 에 **`daily_wage`**(정수, 만원 단위 — 예 25) 추가 저장. (선택값, 없으면 null)
- `GET /api/shared/with-me`, `/api/shared/owner-events` 응답 각 항목에 `daily_wage` 포함.
- 앱은 값 있으면 보라 태그 표시, 없으면 숨김(graceful). **앱은 이미 graceful 반영 예정** → 서버가 echo만 하면 바로 뜸.

### A-2. 출근 시간 (`time_label`) — 작음, A 입력 (2026-06-13 앱 추가)
- 앱(CollabShareSheet)이 "출근 시간" 칩(오전 7~오후 2시)을 추가 → invite payload 에 **`time_label`**(문자열, 예 `"오전 9시"`)를 보냄. 동시에 `scheduled_at_ms` 에 그 정시를 박아서 보냄.
- `POST /api/shared/invite` 에 `time_label`(Optional[str]) 저장 + `/api/shared/with-me`·`/api/shared/by-me` 응답에 echo.
  - 앱 `SharedSite.timeLabel` 이 이미 이걸 받아 목록/상세에 표시함(`· 09:00` 자리). 서버가 echo만 하면 바로 뜸.
- §D(2h 출동 알림)의 `time_label` 과 동일 값 사용 — 1 데이터 1 출처.

## B. 업체별 히스토리 + 누적 수입 (B 화면) — 중간

- 프로토: `b-list` 의 `[현장순 / 업체별]` 토글, `b-biz`(업체 1곳 요약 + 현장 내역).
- **앱 현황(2026-06-13)**: `[현장순/업체별]` 토글 + 업체별 행/상세를 **앱이 이미 구현** — 단, 집계는 **현재 with-me 로 로드된 현장만** 로컬 그룹핑(받은 일당 = 완료 현장 일당 합). 서버 §B `partners`/`history` 가 오면 **전체 이력 기준으로 교체** 예정(윈도우 밖 과거 현장까지 합산). 그 전까지는 로컬 근사치.
- 신규 `GET /api/shared/partners?phone=B` → 나를 부른 사장님별 집계
  ```
  { "partners": [ { "owner_phone","owner_name","count","total_wage","paid_total","last_at_ms" } ] }
  ```
- 신규 `GET /api/shared/history?phone=B&owner_phone=A` → 그 업체와 한 현장 내역
  ```
  { "sites": [ { "title","scheduled_at_ms","daily_wage","paid": true|false } ] }
  ```
- `total_wage` = 완료(completed)된 협업의 `daily_wage` 합. `paid` = A가 `markPaid` 한 건(`b-biz` 의 "입금✓").
- 벽: 금액은 **A↔B 일당만**. 그 현장 고객·매출 절대 미포함.

## C. 영구 보존 — 중요

- 프로토: `a-after` "🗂 이 기록은 계속 남아요 … 3개월 뒤 고객이 또 연락해도 …".
- 협업 해제해도 **사진·메모·진행 기록 삭제 금지**. A의 고객 기록에 영구 연결 보존(§27 보존 원칙 재확인 + 명시).
- 이유: 몇 달 뒤 고객 재연락 시 A가 이 기록 보고 응대.

## D. 출동 2시간 전 알림 (B에게) — geofence 무관, 푸시만

- 프로토: `b-remind` 위쪽 푸시.
- 확정된 협업 현장의 `scheduled_at_ms − 2h` 시점에 B에게 FCM:
  `type=collab_remind, share_id, title, time_label, daily_wage, owner_name`
- 멘트(앱 생성): "오늘은 OO 현장으로 출동하는 날이에요! 출발할 때 [출발] 버튼을 눌러 사장님께 출발을 알려주세요 🚗"
- 스케줄링 주체: **서버 크론 권장**(정확). 앱 ReminderWorker 백업 가능(앱이 with-me 폴링으로 시간 앎). 둘 중 하나만 발송되게 dedup(같은 share_id+날짜).

## E. 3km 자동 도착 (geofence) — 앱이 감지, 서버가 양쪽 푸시

- 프로토: `b-detail`(도착 버튼 **없음**, 자동), `a-arrive`(A가 "거의 도착" 받음), `b-remind` 아래 푸시(B가 "알려드렸어요" 확인).
- 흐름: 앱(B)이 **출발 탭** → 그때부터 위치 추적 시작(그 전엔 미추적) → 현장 3km geofence 진입 감지 → `POST /api/shared/progress {step:"arrived", auto:true}`.
- 서버: arrived(auto) 수신 시 **A에게 "거의 도착해가요"** FCM + **B에게 "사장님께 알려드렸어요"** 확인 FCM.
- `progress` endpoint 이미 있음 → `auto` 플래그 + 양쪽 푸시 분기만 추가. 수동 "도착" 버튼은 앱에서 제거(자동만).
- **앱 현재 상태(2026-06-13):** 앱은 아직 **수동 도착 버튼** 유지 중(출발→도착→완료 3버튼). §E 푸시 분기가 서버에 들어오면 앱이: ① 출발 탭 시 위치추적 시작 ② 3km geofence 진입 시 `progress{step:"arrived", auto:true}` 전송 ③ 수동 도착 버튼 제거(프로토대로 2버튼). **서버가 arrived(auto)→A "거의 도착"+B "알려드렸어요" 푸시만 먼저 만들어 주면** 앱이 geofence 붙임. (위치 권한·지오펜스는 앱, 푸시 분기는 서버)
- A 쪽 수신 문구는 앱이 생성(`a-arrive`): "박지훈 사장님이 거의 도착했어요 · 현장 3km 진입".

## F. 증거 사진 — 기존 site_photos 재사용

- 프로토: `b-detail` "📸 현장 사진 · 증거용", `a-after` "… 증거용 … 분쟁에서 보호".
- 개념: 시공 전·작업 중 상태(기존 하자) 사진 = "원래 그랬다" 증거. 기존 site_photos 흐름(`SERVER_HANDOFF_site_photos.md`)에 협업 현장 연결 + **보존(C)** 적용. 새 저장소 불필요, 라벨/연결만.
- **앱이 소비할 endpoint (이 모양으로 주면 앱이 바로 붙임):**
  - `POST /api/shared/photo` `{ share_id, partner_phone, image_base64, taken_at_ms? }` → `{ photo_id }`  (B 업로드)
  - `GET /api/shared/photos?share_id=` → `{ photos: [{ photo_id, url|image_base64, uploader: "owner"|"partner", taken_at_ms }] }`  (A·B 둘 다 조회)
  - 벽: share_id 로만 접근. 고객 번호/대화/타 고객 사진 절대 미포함.
- **앱 현재 상태(2026-06-13):** A 공유후카드(`CollabAfterCard`)에 "증거사진 보기 자리" 골격 들어갈 준비 됨(서버 photos 오면 grid 채움). B(`SharedSiteScreen` 상세) 업로드 UI 는 위 `POST` 나오면 착수. 그 전까지 앱은 사진 섹션 미표시(빈 껍데기 안 만듦).

## G. 일당 모집 (broadcast → 지원 → 선택) ★ 신규 대형

프로토: switch 그룹 `📣 일당 모집` — `m-compose`(A 작성), `m-push`(B 모집 알림), `m-detail`(B 작업내용+수락), `m-applicants`(A 지원자 순위+선택), `m-result`(B 확정/미선정).

데이터 2종: **recruit**(모집 공고) + **recruit_application**(지원).

- `POST /api/recruit/create`
  `{ owner_phone, date, place, work, daily_wage, partner_phones:[...] }` → `{ recruit_id }`
  각 partner 에게 FCM `type=recruit_invite, recruit_id, owner_name, date, place, work, daily_wage`.
  멘트: "강동 서사장님이 함께할 사장님을 찾아요 / 6월 18일(수)·인천 송도·줄눈·일비 25만원" · [작업내용 보기][바로 수락]
- `GET /api/recruit/with-me?phone=B` → 내가 받은 모집들(상태 포함).
- `POST /api/recruit/apply` `{ recruit_id, partner_phone }` → 지원. **applied_at(순번) 기록**(1·2·3등 = 수락 빠른 순).
- `GET /api/recruit/applicants?recruit_id` (owner) →
  `[{ partner_phone, partner_name, applied_at, rank, past_count, past_total }]`
  (`past_*` = B의 그 owner 와의 history = §B 재사용 → "함께한 적 7번·받은 일당 175만" 표시용)
- `POST /api/recruit/select` `{ recruit_id, selected_phones:[...] }`
  - 선택자 → **협업 현장 확정 자동 생성**(= shared invite 를 accepted 상태로 만들거나 동등 처리) + FCM `type=recruit_confirmed`. 멘트: "6/18 송도 현장, 함께하기로 확정됐어요!" 이때 **정확한 주소 공개**.
  - 미선택자 → FCM `type=recruit_rejected`. 멘트: "먼저 지원한 분들과 함께하게 됐어요. 다음 현장에 꼭 함께해요! 🙏"
- 개인정보:
  - 모집 단계엔 **정확한 주소 비공개**(`place` = 대략 "인천 송도"만). 확정 후 full addr.
  - 지원자끼리 서로 안 보임. owner 만 지원자 목록 조회.
  - 고객 정보(번호·대화)는 어느 단계에서도 미노출.

## H. A 쪽 "이 현장 누가 함께?" (캘린더 협업 표시)

- 프로토: `a-after`("협업 중 · 박지훈 사장님"), 일정 카드 배정줄.
- A가 자기 현장에 협업 사장을 배정(invite)하면 A 캘린더/일정 카드에 **그 사장 이름 + 수락 여부**가 떠야 함.
- 앱 현재(commit d14044b): A가 invite 보낼 때 **로컬로** "🤝 이름"만 기록·표시(prefs `collab_assignments`). **상대 수락 여부는 모름**(로컬 한계).
- 서버 필요: A 의 **내보낸 협업 목록** — `GET /api/shared/by-me?phone=A` →
  `[{ share_id, partner_name, partner_phone, scheduled_at_ms, status: pending|accepted|declined, title }]`
  (또는 `owner-events` 에 accepted/declined 포함.) 앱이 이걸 받아 "🤝 이름 · 요청함/함께" 정확히 표시 + 거절 시 제거.
- **★ 즉시 알림(2026-06-13 사장님 지적 — 지금 A가 수락 알림을 못 받음):** B 가 `/api/shared/respond` (accept=true) 누르면 서버가 **A 에게 FCM 발송**. 기존 `collab_event` 재사용 → `type=collab_event, step=accepted, share_id, partner_name, title`(account 없음). **앱은 이미 수신·문구 준비됨**(commit 예정, "🤝 협업 수락 · OOO님이 수락했어요"). 서버는 invite→B, progress→A, paid→B 는 보내는데 **respond(accept)→A 만 빠져 있음** → 이것만 추가.

---

## 우선순위 제안 (작은 것 → 큰 것) — ※ 2026-06-13 진행 갱신
1. ✅ **A**(일당 echo) · **H**(by-me + 수락 푸시) — 서버 완료(commit 61b3aad 등), 사장님 reload 대기
2. ✅ **B**(업체별 partners/history) — 서버 완료(commit 911d6f2), 앱 연결 완료(9b3761e)
3. ⬜ **C**(영구 보존 명시) — 코드보다 정책 확인. 협업 해제/완료 후에도 사진·메모·진행 삭제 금지 보장.
4. ⬜ **F**(증거사진 photo POST/GET) · **E**(3km arrived(auto) 양쪽 푸시) — **다음 cycle 1순위**. 위 §F/§E "앱이 소비할 endpoint" 모양대로 주면 앱이 즉시 붙임.
5. ⬜ **D**(2h 출동 알림 크론)
6. ⬜ **G**(모집 시스템 — 가장 큼)

## 앱 측 동시 진행(이번 커밋)
- 일당: A 입력(CollabShareSheet) + invite payload `daily_wage` + B 표시(보라 태그) — **graceful**(서버 echo 전엔 안 뜸).
- 출발 버튼 멘트 보강.
- 나머지(업체별 화면·모집 5화면·geofence·2h 알림)는 위 endpoint 준비되면 앱 작업 착수.

질문/충돌 있으면 `docs/SYNC.md` 에 "의문" 블록 + 사장님 알림.
