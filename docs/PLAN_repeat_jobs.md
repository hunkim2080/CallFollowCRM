# 재방문 / 추가 시공 (시공 건 = jobs) — 설계·진행 (2026-07-20)

> 실전 케이스: 한 고객이 한 번 시공받고 만족 → **다른 날, 다른 장소에 시공을 하나 더** 요청.
> 설계: Fable 5 논의. 프로토(ringgo-redesign.html) = **시공 건 중심**(`jobs[day]` 배열, 건별 sid/주소/금액/정산)인데,
> 앱은 **"고객당 시공 1건"**(CustomerEntity 단일 컬럼)으로 구현돼 두 번째가 첫 번째를 덮어씀 → 그 어긋남을 메꾸는 작업.

## 사장님 결정 (2026-07-20)
- **패턴**: "첫 시공 끝난 뒤 나중에 또" (동시 2건 미리 예약은 드묾) → Phase 1 최소본으로 충분.
- **진행**: 전표 서랍(jobs 테이블) 만들기.

## Phase 1 — 완료 (commit 8ae4a31, DB v42)
- `JobEntity`/`JobDao`/`JobRepository` 신설. `jobs` 테이블 = CustomerEntity 시공 필드와 1:1. **CREATE만**(기존 데이터 이동 없음 = 마이그레이션 위험 최소).
- **규칙** (`JobRepository.archiveCompletedBeforeNewSchedule`): 일정 등록 **직전**, 현재 고객 시공이 이미 `workCompletedAt != null`(완료)이면 → 그 완료 건을 jobs로 보관 + CustomerEntity 시공 필드 전부 리셋 → 등록 폼이 새 건 값을 채움. 완료 전이면 무동작(기존처럼 그 건 편집).
- 훅: `ScheduleAddViewModel.submit` (일정 직접 등록). 표시: 고객 상세 **일정·정산 탭 "지난 시공 N건"** (`CustomerDetailScreen`).
- 단위테스트 3건 통과(완료→보관·리셋 / 미완료→무동작 / 시공일없음→무동작).
- **캘린더·정산은 그대로 CustomerEntity(현재 건)** 를 읽음 → 지난 건은 이력에만.

### Phase 1 한계 (알고 넘어감)
- 첫 시공 **완료 전에** 두 번째를 잡으면 여전히 덮어씀(동시 2건 미지원) — 사장님 패턴상 드묾.
- 접수서 read-back(IntakeSyncManager)·완료/입금 경로는 아직 archive 훅 미연결(일정 직접 등록만). 필요 시 확장.
- 정산 왜곡(과거 매출 재계산이 현재 건 기준) 미해결 — Phase 2에서.

### ⚠️ 남은 검증/확인
- **실기 마이그레이션 검증 대기** — 폰 분리 상태라 v41→v42 실기 오픈(크래시 없음) 미확인. **폰 연결 시 최우선**. (SQL은 정적 검증 완료 + 검증된 job_crew 패턴과 동일)
- **사이트/사용자 미배포** — 실기 검증 후 배포.
- **CustomerDetailScreen 수정 사장님 확인** — 옛 잠금목록의 `CustomerDetailActivity.kt`는 현존 X(실제는 CustomerDetailScreen.kt). 이 화면 수정 허용 여부 확인.

## Phase 2 — 정식 (보류, 필요 시)
프로토대로 job-centric 완성: 캘린더/정산/고객상세가 jobs를 읽게(날짜별 여러 건, 건별 정산). 마이그레이션으로 기존 단일 시공을 job 1건 이관. 미러(dual-write) 유지로 기존 화면 무중단 전환. 품 큼.

## 사장님께 물을 것 (프로토가 답 안 주는 결정 — Phase 2/정교화 시)
1. 고객 상세에서 지난 시공 표시: 지금 "지난 시공 N건" 목록으로 충분? 더 필요?
2. 첫 건 완료 전 두 번째 예약(동시 2건)이 실제로 자주 생기면 → Phase 2 앞당김.
3. 완료된 시공을 캘린더 과거 달에 남길지(프로토 jobsArchive = 남김).
4. 정산 건별 분리 확정 / 고객 단위 합산 뷰 필요?
5. "단골" 자동 전환 규칙(누적 2건부터 자동? 수동?).
6. 두 번째 접수서에 이전 건 흔적(단골 할인 등) 표기? (프로토 기본 = 안 넣음)

관련 파일: `CustomerEntity.kt`, `JobEntity.kt`, `JobRepository.kt`, `ScheduleAddViewModel.kt`, `ScheduleViewModel.kt`, `SettlementCalc.kt`, `CustomerDetailScreen.kt`, `design-preview/ringgo-redesign.html`(openAddSchedule/submitSchedule/openQuote).

---

## Phase 2 확정 설계 (2026-07-28 사장님 결정) — "같은 사람 미리 두 날짜"

> 사장님: **"같은 사람이 두 날짜를 미리 잡는 경우도 많아."** → Phase 1 한계 ③(첫 시공 완료 전 두 번째 예약 시 덮어씀)이 실사용 빈발.
> 결정: **프로토대로 '건 중심' 정식 구현**. "반만" 옵션 없음 — 정산(미수금)이 틀리면 사장님이 가장 싫어하는 돈 버그라, 정산까지 맞추는 게 작업의 대부분이라 절반 버전이 무의미. **며칠짜리.**
> 시점: **홍보용 온보딩 마무리 후 바로 착수**(2026-07-28 이 시점엔 설계만 못박고 손은 온보딩으로).

### 목업 = 프로토 그대로 (지어내지 말 것, §0)
프로토가 이미 완전한 건 중심 설계임 = 이게 목업이자 스펙. 출처 지목:
- `jobs = { 날짜: [건,건,...] }` 배열([:3028](../design-preview/ringgo-redesign.html#L3028)), `jobsOn(d)`([:3036](../design-preview/ringgo-redesign.html#L3036)), `jobsCovering(d)`(멀티데이 span, [:3038](../design-preview/ringgo-redesign.html#L3038)), `computeLanes()`(겹침 lane, [:3040](../design-preview/ringgo-redesign.html#L3040)).
- 한 날 여러 건 UI: "이 날 일정 더 추가"([:3106](../design-preview/ringgo-redesign.html#L3106)), 빈 날 "이 날 일정 등록"([:3108](../design-preview/ringgo-redesign.html#L3108)).
- 등록/수정: `openAddSchedule(day, idx)`([:3448](../design-preview/ringgo-redesign.html#L3448)) — idx 있으면 그 건 편집, 없으면 새 건. `submitSchedule()`([:3546](../design-preview/ringgo-redesign.html#L3546)), `delSchedule(day, idx)`([:3568](../design-preview/ringgo-redesign.html#L3568)).
- 정산: `settle[]` 를 **건(`sid`)별**로 잡음(고객별 아님). `delSchedule` 이 그 sid 정산도 같이 삭제.
- 월별 보관: `jobsArchive[calMon]`([:3041](../design-preview/ringgo-redesign.html#L3041), [:3045](../design-preview/ringgo-redesign.html#L3045)) — 지난 달 완료 건 그대로 남김.

### 구조 = "미러(mirror)" 방식 (기존 화면 무중단이 핵심)
- **jobs 테이블 = 모든 시공 건(과거·현재·미래)의 SoT.** 지금은 완료 보관만 하는데, Phase 2에선 예정/진행 건도 여기 들어옴.
- **CustomerEntity 시공 필드 = 그 고객의 "대표 건" 미러**(= 가장 가까운 예정 건; 예정 없으면 가장 최근 건). CustomerEntity.scheduledWorkDate 를 읽는 **~10개 리더는 무변경**으로 대표 건을 계속 봄: 홈 히어로/HomeViewModel, 챗/ChatViewModel, 통화 前 카드/IncomingCallOverlay, 접수서/IntakeSyncManager, 미러/MirrorSyncManager, 브리핑/ClosingBriefViewModel 등.
- **쓰기(등록·완료·입금·잔금)는 특정 job 대상** → 매번 그 고객 대표 건 재계산 후 CustomerEntity 미러 재기록. (불변식: jobs 가 바뀌면 항상 미러 재계산)

### 바뀌는 곳 (must — 여기가 작업 대부분)
1. **마이그레이션 v42→v43** — 각 고객의 현재 활성 시공(`scheduledWorkDate != null`)을 jobs 행으로 INSERT(이미 보관된 완료건과 중복 방지). **CREATE + COPY 만, 기존 컬럼 유지 → 무손실.** (v41→v42 job 패턴 검증됨, 동일 방식) ⚠️ 실기 오픈 크래시 검증 필수(데이터 안전 지뢰).
2. **달력/ScheduleViewModel·ScheduleScreen** — jobs 읽어 한 날 여러 건 + 멀티데이 lane. (지금 CustomerEntity 단건 읽음)
3. **정산/SettlementViewModel·SettlementCalc** — 건별 합산으로. ❗**돈 경로 = 순수함수 단위테스트 필수**(미수금·매출이 모든 건 합산). 절반 불가 이유가 여기.
4. **알람/ReminderWorker** — D-1·잔금 알람을 **건별로** 발사(두 번째 날짜도 울려야 함).
5. **등록/ScheduleAddViewModel.submit** — `updateScheduledWorkDate`(덮어쓰기) 제거 → **새 job INSERT** + 미러 재계산. `archiveCompletedBeforeNewSchedule` 훅은 흡수/폐기.

### 단계 배포 (각 단계 폰 검증 후 다음)
- **Stage A**: jobs=SoT + 미러 + 마이그레이션 + 등록=INSERT(덮어쓰기 제거) + 달력이 jobs 읽기. → "두 날짜 저장·달력 노출" 달성.
- **Stage B**: 정산 건별(테스트) + 알람 건별. → "미수금·알람 정확" 달성.
- **Stage C**: 고객상세 건 목록 UI(프로토) + 멀티데이 lane 렌더 폴리시.

### 프로토 따르는 결정(이미 답 나옴 — 다시 안 물음)
- 완료 건 과거 달에 남김(jobsArchive). 정산 건별 분리(고객 합산 뷰는 추후 필요 시). 두 번째 접수서에 이전 건 흔적 안 넣음.
- 여전히 물을 것: "단골" 자동 전환 규칙(누적 2건부터 자동?) — Stage C 때.
