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
