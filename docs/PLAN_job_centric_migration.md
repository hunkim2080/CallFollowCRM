# 고객 정보 → 건(件) 단위 전환 — 연결부 전수 점검 (2026-09-18)

> 코드를 직접 읽고 적었다. 추측은 "확인 못 함"으로 표시했다. 파일:줄 은 2026-09-18 현재 main(36adb38b 이후 작업트리) 기준.
> 기준 문서: `docs/PLAN_repeat_jobs.md` (Stage A/B/C 이력), `docs/SYNC.md` 2026-09-11 ~ 09-17.

---

## 1) 한 장 요약 (사장님용)

지금 앱은 **"고객 카드 한 장 = 시공 한 건"** 이라는 옛 그림 위에, 9월 11일부터 **"건 장부(jobs)"** 를 덧대어 쓰고 있습니다.
비유하면 — 옛날엔 손님 한 명당 **수첩 한 쪽**에 날짜·주소·돈을 다 적었는데, 이제는 손님 한 명당 **전표 여러 장**(건마다 한 장)을 따로 쓰기로 했습니다. 그런데 **수첩 쪽(customers)이 아직 살아 있고**, 화면 절반은 수첩을, 절반은 전표를 보고 있습니다. 그래서 두 곳이 어긋나면 사고가 납니다(9/17 "잔금 받았는데 미수 알람" 사고가 정확히 이것).

건 단위로 완전히 옮길 때 **제일 위험한 것 5개**:

1. **💸 돈이 두 번 세어진다 (지금 이미 그렇다).**
   정산 상단 "이번 달 받은 돈", 마감 브리핑 "오늘/이번 달 입금", 리포트 매출, 현금흐름 달력이 **수첩(customers) 돈 + 전표(jobs) 돈을 그냥 더한다.** 9/11 이후 수첩과 전표에 **같은 돈이 양쪽에 다 적혀 있으므로**(v49 복사 + v53 보정 + mutate 미러) 대표 건 입금이 **2배로** 잡힌다. 전환 전에 먼저 고쳐야 할 1순위. (§2-④, §5-①)

2. **💸 '예약 취소'가 다른 건의 돈을 지운다.**
   고객 정보에서 날짜를 지우면(취소) → 대표 건 날짜만 비우고 → 미러가 **다음 건**을 대표로 잡은 뒤 → "취소 = 백지" 규칙으로 돈을 전부 null 로 만드는데, 그 null 이 **다음 건(예: 1차) 전표에도 찍힌다.** 2차를 취소했더니 1차 잔금 기록이 사라지는 구조. (§2-⑨)

3. **💸 두 번째 일정을 잡으면 첫 건 돈이 덮인다(조건부).**
   일정 탭에서 2차를 등록할 때 addJob 뒤에 customers 총액·계약금을 또 쓰는데, 이게 **대표 건(=날짜가 더 가까운 1차)** 전표에 미러링된다. 2차 날짜가 1차보다 뒤면 1차 총액이 2차 값으로 바뀐다. (§2-⑧)

4. **📷 PC 사진 캘린더는 "손님당 한 줄"이라 1차·2차가 섞인다.**
   서버 표 `web_schedule_feed` 의 기본키가 (사장님, 고객번호) — 한 손님에 한 날짜만 저장된다. 사진도 **고객번호(끝8자리)로만 묶고** 날짜·건 정보가 없다. 그래서 PC 에서 1차·2차 사진이 한 통에 섞이고, "앞 절반=시공 전 / 뒤 절반=시공 후" 자동 추정도 깨진다. **서버를 고쳐야만** 건별로 나뉜다. (§3)

5. **📅 구글 캘린더·본폰 미러·D-1 홈 카드는 대표 건 하나만 안다.**
   구글 캘린더 이벤트 id 가 **고객 표**에 한 칸(`workCalendarEventId`)뿐이라, 2차를 잡으면 1차 이벤트가 **2차 날짜로 옮겨진다**(새 이벤트가 안 생김). 본폰 미러·홈 "내일 시공 안내" 카드도 같은 이유로 2차는 안 보인다. (§2-⑦, ⑧, ⑤)

그 외: 고객 정보의 **현장 사진은 어느 건인지 표시가 안 붙는다**(새로 올린 사진의 jobId 가 항상 null) → 건 탭을 만들어도 사진이 건별로 안 갈린다. 완료 버튼(홈 히어로)은 **수첩에만** 찍히고 전표엔 안 찍혀 건 탭의 "완료" 표시가 틀릴 수 있다. 두 건의 알림은 **고객 id 로 같은 알림 번호**를 써서 하나가 다른 하나를 덮는다.

---

## 2) 연결부 표

기호: **M** = customers 미러(대표 건)만 봄 · **J** = jobs 봄 · **M+J** = 둘 다 더함(=이중)

### 데이터 층 (모든 화면의 뿌리)

| # | 무엇 | 읽는/쓰는 곳 | M/J | 건 2개 이상이면 지금 동작 | 바꿔야 하나 · 어떻게 |
|---|---|---|---|---|---|
| D1 | 대표 건 미러 재계산 | `JobRepository.recomputeMirror` `data/repository/JobRepository.kt:213-227` | J→M | **일정 4칸만** 복사(날짜·시간·일수·주소). 돈·완료·메모는 안 복사. 대표 = 오늘 이후 가장 가까운 건, 없으면 최근 건 | ✅ 필수. 돈 5칸 + `workCompletedAt` 도 대표 건에서 복사하도록 확장. 방향은 **항상 jobs→customers 한 방향**으로 |
| D2 | 돈 미러 (반대 방향) | `CustomerRepository.mutate` → `mirrorMoneyToRepresentativeJob` `CustomerRepository.kt:34-45, 65-84` | M→J | customers 돈이 바뀌면 **대표 건** 전표에 덮어씀. 대표가 아닌 건은 못 건드림 | ✅ 필수. **제거**(D1 이 한 방향으로 바뀌면 이 역방향은 핑퐁·덮어쓰기 원인). 돈 쓰기는 `jobId` 를 받는 새 함수로 |
| D3 | 완료 처리 | `CustomerRepository.updateWorkCompletedAt` `:262-263` | M | customers 에만 찍힘. jobs.workCompletedAt 은 **아무 화면도 안 씀**(마이그레이션 복사·archive 만) | ✅ 필수. jobId 기반으로. "잔금 받으면 자동 완료" 규칙은 JobEntity 에 `isWorkDone` 계산 프로퍼티 추가(CustomerEntity :79 와 동일 규칙) |
| D4 | 주소·메모 쓰기 | `updateAddress :286`, `updateMemo :194` | M | customers 만. jobs.address 는 addJob/sync 때만, **jobs.memo 는 v50 마이그레이션 외엔 아무도 안 씀**(`grep jobDao.update` 결과 JobRepository 3곳뿐, memo 갱신 없음) | ✅ 필수. 건 메모/건 주소 편집기 → `jobDao.update`. **customers.memo 를 '고객 공통 메모'로 남길지 폐기할지는 사장님 결정 필요**(§8) |
| D5 | 일정 쓰기(옛 경로 7곳) | `syncRepresentativeFromCustomer` `JobRepository.kt:156-190` — 호출: `IntakeSyncManager:71`, `ChatViewModel:1762,1818,1829`, `CustomerDetailViewModel:460,480,524` | M→J | customers 날짜를 **대표 건에 덮어 옮김**. 날짜 null 이면 대표 건만 unschedule(:163) 후 미러가 다음 건을 대표로 잡음 | ✅ 필수. 호출부 7곳을 전부 `jobId` 명시 경로로 교체 후 이 함수 폐기 |
| D6 | 등록(새 건) | `JobRepository.addJob :109-140` | J | 같은 고객·같은 날 중복 가드(:120). 새 건에 돈 넣음 | 유지 |
| D7 | 사진 저장 | `SitePhotoRepository.addFromUri :26-43` | — | `SitePhotoEntity(customerId, filePath, createdAt)` 만 → **jobId 항상 null** (:38-40) | ✅ 필수. `addFromUri(customerId, jobId, uri)` |
| D8 | 사진 조회 | `SitePhotoDao.observeByCustomer :236-237` | — | customerId 로만 → 건 무관하게 전부 | ✅ 필수. `observeByJob(jobId)` + `jobId IS NULL` 옛 사진 처리 규칙 필요 |
| D9 | DB 스키마 | `AppDatabase.kt:70 version=53`, jobs 표 `:784-800`, `MIGRATION_49_50 :878-919`(memo·jobId 칸+백필), `51_52 :958-985`, `52_53 :992-1017` | — | 스키마는 이미 건 단위 가능. 부족한 칸: **jobs.calendarEventId 없음**(구글 캘린더 건별용) | 구글 캘린더를 건별로 하려면 v54 `ALTER TABLE jobs ADD COLUMN calendarEventId TEXT`(nullable=안전). 그 외 스키마 변경 불필요 |

### 화면·기능

| # | 무엇 | 읽는 곳 | M/J | 건 2개 이상이면 지금 동작 | 바꿔야 하나 · 어떻게 |
|---|---|---|---|---|---|
| ① 사진 | 고객 정보 현장 사진 | `CustomerDetailViewModel.sitePhotos :59-60`(observe(customerId)) · `CustomerDetailScreen :942-954` | — | 모든 건 사진이 한 그리드. 건 탭을 골라도 안 갈림 | ✅ 선택 건의 사진만 + 미분류(jobId null) 처리 |
| ① 사진 | 서버 업로드 | `OwnerPhotoUploadManager.uploadPending :39-75` → `SitePhotoServerRepository.uploadOwnerPhoto :114-133` | — | 보내는 키 = `owner_phone, customer_phone, image, label` 뿐(:121-126). **건·날짜 정보 없음** | ✅ 서버와 함께(§3) `work_date`(또는 job 식별자) 추가 |
| ① 사진 | PC 웹 캘린더 | §3 참조 | — | 고객당 1줄·사진 고객당 1통 | ✅ 서버 변경 필수 |
| ② 일정 | 일정 탭·달력 목록/막대 | `ScheduleViewModel.state :43-58` (jobs→CustomerEntity 복사본) · `ScheduleScreen :412 laneKeyOf`, `:1287-1306`, `:1380-1403` | J(일정) / M(돈·완료) | 건마다 한 줄 ✅. 그러나 복사본의 **돈·완료는 customers 값**(:50-56 은 일정 4칸만 copy) → 카드 하단 정산줄 `ScheduleScreen:1074 rowOf(customer)` 와 "완료" 태그 `:1106 customer.workCompletedAt` 이 **대표 건 값**으로 모든 건에 똑같이 뜸 | ✅ 복사본에 job 의 돈 5칸·workCompletedAt 도 copy (한 줄 수정) |
| ② 일정 | 밀어서 빼기/되돌리기 | `ScheduleViewModel.unschedule :329-342`, `restoreSchedule :344-355` | J | `jobAt(customer,day)` 로 그 건만 ✅. 폴백(:337-341)은 customers 만 | 유지. 폴백은 D5 정리 후 삭제 |
| ② 일정 | 팀 배정·일당 | `assignmentsByCustomer :115-118`, `jobCrewByCustomer :129-131`; `TeamAssignmentEntity :25-26`, `JobCrewEntity :26-27` = (customerId, dayStartMs) | 날짜키 | 이미 (고객,날짜) 키라 건과 1:1 로 맞음 ✅ | 유지. 단 **예약 취소가 `deleteForCustomer`** (`CustomerDetailViewModel:528`) 로 **모든 건**의 배정을 지움 → 그 건 날짜만 지우게 |
| ② 일정 | 협업 요청 기준일 | `ScheduleViewModel:402 baseMs = workDayStarts.min ?: customer.scheduledWorkDate` | M(폴백) | 복사본이라 그 건 날짜 ✅ | 유지 |
| ② 일정 | 팀원 스냅샷 push | `ScheduleViewModel:535-556` (`state.value.all` 복사본에서 찾음) | J | `all.find { it.id == a.customerId }` (:539) → **같은 고객 건이 2개면 첫 번째 것만** | ✅ (customerId, dayStartMs) 로 매칭 |
| ② 일정 | 간단 일정 | `simple_events` (`ScheduleViewModel:85-98`) | 별도 표 | 고객·건 무관 | 영향 없음 |
| ② 일정 | 챗 '내 일정' 시트 | `ChatViewModel:192 scheduledJobs = customers.filter{scheduledWorkDate}` → `ChatScreen:1416 MyScheduleSheet(jobs=…)` · `MyScheduleSheet:92-95, 338-342` | **M** | 대표 건만 점·목록. 2차 안 보임 | ✅ jobs 로 |
| ③ 브리핑 | 마감 브리핑 화면 | `ClosingBriefViewModel:39-42 combine(customers, jobs)`, `:58-66 paidSum/monthPaidSum = cs 합 + jobHistory 합`, `:86 내일 시공 = cs`, `:101 못 받은 돈 = cs.workCompletedAt` | **M+J**(돈) / M(내일·미수) | **입금이 이중 합산**(§5-①). 내일 시공·미수는 대표 건만 | ✅ 돈 = jobs 만(고객은 건 없는 옛 데이터만). 내일·미수 = jobs |
| ③ 브리핑 | 마감 브리핑 알림 | `ReminderWorker.showDailyBriefNow :110-139` (customers) | M | 내일 시공 수·입금 수 대표 건 기준 | ✅ jobs 로 |
| ④ 정산 | 정산 목록·미수 합계 | `SettlementViewModel.rows :45-66` (customers만) · `state :68-86` · `SettlementScreen:477,524,529,575-586` | **M** | **고객당 한 줄** = 대표 건 돈. 1차 미수·2차 완납이면 목록엔 customers 에 마지막으로 써진 값 하나만 → 1차 미수 안 보임(PLAN 문서 "정산 화면의 건별 합산 = 남은 것") | ✅ 필수. `rows` = jobs(+건 없는 고객) · `SettleItem` 에 jobId · 토글 `setDepositPaid/setBalancePaid :239-269` 는 jobId 로 |
| ④ 정산 | "이번 달 받은 돈"·전월비·목표 | `receivedInMonth :190-202` (customers 합 + jobs 합) | **M+J** | **이중 합산** — v49(:853-875) 가 돈까지 jobs 로 복사 + v53(:992-1017)·`mirrorMoneyToRepresentativeJob` 이 대표 건 돈을 customers 와 같게 유지 → 같은 입금이 두 번 | ✅ 1순위. jobs 만 더하고, jobs 행이 하나도 없는 고객만 customers 로 |
| ④ 정산 | 현금흐름 달력 | `SettlementViewModel.cashItems :206-215` → `CashFlowCalc.buildItems :64-152` (customers 루프 :72-109 **+** jobs 루프 :115-132, 중복 제거 없음) | **M+J** | 대표 건 계약금/잔금이 **같은 날 두 줄**(같은 이름·같은 금액). `CashFlowCalcTest` 에 customers+jobs 겹침 테스트 없음(grep 결과 0) | ✅ 1순위. 순수함수라 테스트 먼저(§4 Step 0) |
| ④ 정산 | 미수 경과일 | `SettlementCalc.overdueDays :155-160` (CustomerEntity) | M | 대표 건 완료일/시공일 기준 | ✅ JobEntity 오버로드 |
| ⑤ 홈 | 오늘 시공 히어로 | `HomeViewModel.todayJobs :723-737` (customers) · `HomeScreen:1680,1745,1912-1937` | **M** | 오늘 건이 대표가 아니면(예: 오늘 1차 진행 중인데 내일 2차가 있으면 대표=오늘 이후 가장 가까운 = 오늘이라 대부분 OK) — 단 **같은 날 두 현장**은 하나만 | ✅ jobs 로 (ScheduleViewModel 과 같은 복사본 패턴 재사용) |
| ⑤ 홈 | 완료·잔금 버튼 | `markJobCompleted :1116`, `markJobCompletedBalancePaid :1128-1139`, `markBalanceReceived :1145-1152`, `undo… :1120,1156` → 전부 `customerRepository.update…` | M | 수첩에만 완료 찍힘(D3). 잔금은 mutate 로 대표 건에 미러 | ✅ jobId 로 |
| ⑤ 홈 | 다음 시공 미리보기 | `nextJobs :755-762` | M | 고객당 하나 | ✅ jobs |
| ⑤ 홈 | 미수 카드 합계 | `outstandingTotal :316-318`, `outstandingCount :321-323` | M | 대표 건 돈만 | ✅ jobs |
| ⑤ 홈 | 이번 주 시공 수 | `thisWeekScheduledCount :300-306` | M | 고객당 1 | ✅ jobs |
| ⑤ 홈 | "시공 잡힘=상황 종료" 필터 | `scheduledCustomerSuffixes :190-195` | M | 고객 단위 판단이라 무관 | 유지 가능(고객 단위 의미) |
| ⑤ 홈 | 내일 시공 안내 카드(D-1 문자) | `scheduleReminders :575-600` → `ScheduleReminderCalc.compute :58-72` (customers) | **M** | 대표 건이 내일이면 뜸. **같은 고객이 오늘 다일 공사 중 + 내일 다른 현장**이면 내일 것 안 뜸 | ✅ jobs 로. 발송 로그 키 `(ruleId, customerId, day)` 는 날짜 포함이라 그대로 가능 |
| ⑤ 홈 | 통화 요약 컨텍스트 | `HomeViewModel:1345`, `ChatViewModel:1451-1452,1549-1550`, `ConversationAiRepository:154,169` | M | 서버에 대표 건 날짜 1개 전달 | 유지(서버 스펙 변경 없이) |
| ⑥ 알람 | D-1 알림 | `ReminderWorker.checkInstallD1 :301-343` → `JobReminderCalc.d1Due :189-211` | J ✅ | 건별 ✅. **단 알림 id = `famId(FAM_D1, customerId)`** (`NotificationHelper:521`) → 같은 고객 두 건이 같은 날 밤이면 뒤 알림이 앞을 덮음 | notifId 에 jobId 포함 |
| ⑥ 알람 | 잔금 미수 알림 | `checkBalanceDue :253-298` (`rowOf(job)`, dedup `settlej:<jobId>`) | J ✅ | 건별 ✅. 알림 id `famId(FAM_SETTLE, customerId)` (:589) 같은 문제 | notifId 에 jobId |
| ⑥ 알람 | 오늘의 현장 상시 알림 | `refreshTodaySites :146-173` (customers) | M | 같은 고객 두 현장이면 하나 | ✅ jobs |
| ⑥ 알람 | 정기 문자 회차 | `checkRecurringDue :177-214`, `RecurringDueCalc:48` | M | 대표 건 시공일 기준 | 사장님 결정(§8): 정기문자 기준일 = 마지막 완료 건? 건마다? |
| ⑥ 알람 | A/S 오늘 | `checkAsToday :221-250` (`asScheduledDate`) | 고객 필드 | A/S 는 건 밖(무료·별개) | 영향 없음(이번 범위 밖) |
| ⑦ 구글 캘린더 | 이벤트 생성/갱신 | `CalendarSyncManager.syncAll :143-162` (`store.scheduledCustomers()`), `buildEvent :278-345` (c.scheduledWorkDate :279, totalAmount :291,318, memo :321, address :287,336) · 이벤트 id 저장 `DefaultCalendarSyncStore:46-58` = **customers.workCalendarEventId** · 자동 동기화 지문 `CalendarAutoSync:87-103` (customers) | **M** | 고객당 **이벤트 1개**. 대표가 1차→2차로 바뀌면 **같은 이벤트가 2차 날짜로 이동**(1차 이벤트 사라짐). 제목의 `[총금액]` 도 대표 건 | ✅ 건별 이벤트. `jobs.calendarEventId` 칸(v54) + 지문 키 `(jobId)` + 자동동기화 지문에 jobs 포함 |
| ⑧ 본폰 미러 | 일정·돈 스냅샷 | `MirrorSyncManager.pushNow :61-130` (ownItems :74-90 customers; cashItems :97 `buildItems(customers, manual, emptyList(), today)` **jobs 안 넘김**; moneyRows :104-125) | M | 일정 고객당 1건. 돈은 이중 아님(jobs 안 더함) | ✅ ownItems·receivables = jobs. cashItems 는 D-④ 수정본 재사용 |
| ⑨ 접수서 | 웹 접수 → 앱 | `IntakeSyncManager :55-100`: 주소 `c.address.isNullOrBlank()` 일 때만(:60), **날짜 `c.scheduledWorkDate == null` 일 때만**(:67-75 → `syncRepresentativeFromCustomer`), 메모 비었을 때만(:80-82), 총액·계약금 0일 때만(:86-88, :98-100) | M | **이미 건이 있는 고객의 두 번째 접수서는 날짜·주소·금액이 전부 무시**되고 `intake_events` 카드(:106-121)만 남는다 | ✅ 접수서 날짜가 기존 건과 다르면 **새 건(addJob)**, 같으면 그 건 보강. 자기 건에만 채우기 |
| ⑨ 고객 정보 | 예약 취소 = 백지 | `CustomerDetailViewModel.updateScheduledWorkDate :504-560`: `:521 customers 날짜 null` → `:524 syncRepresentativeFromCustomer`(대표 건 unschedule → **미러가 다음 건을 대표로**) → `:528 deleteForCustomer` → `:538-542 돈 5칸 null` → `mutate` 미러가 **새 대표(다른 건)** 전표 돈을 null 로 | M→J | **2차 취소 시 1차 전표의 돈이 지워질 수 있음**(코드 경로상 확정; 실기 재현은 확인 못 함) | 🔴 1순위. 취소는 `jobId` 대상: 그 건만 unschedule/삭제 + 그 건 돈만 백지 + 그 날짜 배정만 정리 |
| ⑨ 고객 정보 | 돈 카드(총액·계약금·잔금·받음) | `CustomerDetailViewModel :329-380, 450-453` → customers · 화면 `CustomerDetailScreen :734-743 (c.totalAmount…, rowOf(c))` | M | 대표 건 돈만 편집 가능. 지난 건 패널 `PastJobPanel :4039-4080` 은 **읽기 전용** | ✅ 선택 건 편집으로 |
| ⑨ 고객 정보 | 건 탭 | `CustomerDetailScreen :702-727 (repJobId = 대표 날짜와 같은 건)`, `JobTabsRow :3959-3999` | J | 탭 ✅. "완료" 판정 `job.workCompletedAt :3976` 은 D3 때문에 대표였던 건이 완료돼도 안 찍힘 → 밀려난 뒤 "예정/지남"으로 보임 | D3 해결되면 자동 수정 |
| ⑨ 고객 정보 | 새 시공 ＋ | `addNewJob :87-105` | J | 새 건 INSERT ✅ (주소·돈 null) | 유지 |
| ⑩ 협업 | 협업 현장 초대 | `ScheduleViewModel:395-420`, `SharedSiteRepository:183-197 scheduled_at_ms` | 복사본 | 그 건 날짜로 초대 ✅. `prefs.collabAssignments` 는 `customerId|…`(`WebFeedSyncManager:66-73`) = **고객 키** | 협업 배정 키에 날짜/jobId 추가 검토(예약 취소 :533-535 가 고객 전체를 지움) |
| ⑪ 통계 | 리포트 매출·미수 | `ReportViewModel :44,55, 83-104` (customers 루프 + jobHistory 루프), `:200-207 미수`, `:232-235` | **M+J** | **매출 이중 합산**. 시공 수 `:92` 고객당 1 | ✅ jobs 만 |
| ⑪ 통계 | 통계·다녀온 현장 | `StatsViewModel:66-81`, `VisitedViewModel:65-69, 94-101` (revenue = totalAmount 합), `SettingsViewModel:153-157` | M | 건 수 과소, 매출 대표 건만 | ✅ jobs |
| ⑫ 백업 | 내보내기/복원 | `DataBackup.export :68-125`(전 테이블 + site_photos 파일 :109) · `import :183-247`(INSERT OR REPLACE, PK 유지 → `site_photos.jobId`↔`jobs.id` 연결 보존) · `backfillJobsFromCustomers :427-450`(NOT NULL memo 채움 ✅) · `restoreFiles :576-609` | 전 테이블 | 건 단위 데이터도 그대로 왕복 ✅ | 유지. **v54 에 칸 추가하면 backfill INSERT 의 컬럼 목록도 같이**(NOT NULL 이면 특히) |
| ⑬ 기타 | 고객 목록 정렬·태그 | `CustomersScreen:85-86,139,345-351`, `CustomerStatusTag:41-46,161` | M | 고객 단위 표시 | 유지 가능(고객 태그 = 대표 건 상태) — 단 D1 이 돈·완료까지 미러하면 자동 정확 |
| ⑬ 기타 | 자동 카테고리 | `AutoCategoryClassifier:51-56, 85-90` (`isWorkDone`, 계약금) | M | 대표 건 기준 | D1 확장 후 자동 |
| ⑬ 기타 | 전화 오는 사람 카드 | `IncomingCallOverlay:135-138, 222-223, 294-308` | M | 대표 건 | 유지(대표 건 미러가 정확해지면 OK) |
| ⑬ 기타 | 신규 고객·견적 독촉·통화 후 판단 | `NewLeadsViewModel:214`, `EstimateFollowupCalc:46`, `CallStateReceiver:296` | M | "시공일 있음" 불리언 | 유지 |
| ⑬ 기타 | 지오펜스 | `GeofenceManager:102-104`(`site_${c.id}`), `GeofenceBroadcastReceiver:59` | M | 고객당 1 펜스, 대표 건 주소 | ✅ 건별(요청 id 에 jobId) — 우선순위 낮음 |
| ⑬ 기타 | KPI 백필 | `CallFollowCrmApplication:148-150` | M | 1회성 | 무관 |
| ⑬ 기타 | PC 웹 피드 | `WebFeedSyncManager.pushNow :53-107` (customers :75-92, memo :90) | M | §3 | §3 |

---

## 3) PC 웹 사진 연동 (서버 근거)

### 지금 구조 — "고객당 한 줄, 사진은 고객 번호로 한 통"

| 항목 | 근거 | 사실 |
|---|---|---|
| 피드 표 | `server/main.py:409-424` | `web_schedule_feed(owner_phone, customer_digits, name, apartment, dong_ho, work_date, category, completed, memo, updated_at_ms)` **PRIMARY KEY (owner_phone, customer_digits)** (:422) → 고객당 **한 행**. |
| 피드 push | `:28113-28154` | 앱이 보낸 items 로 `DELETE` 후 `INSERT OR REPLACE`(:28122, :28138). 앱 쪽(`WebFeedSyncManager:75-92`)도 customers 를 돌아 고객당 1건만 보냄 → 2차 날짜는 애초에 서버에 안 감. |
| 사진 표 | `:1097-1135` | `team_site_photos(photo_id, token, member_id, owner_phone, label, image_data_url, image_path, note, uploaded_at_ms)` + `customer_phone`(:1118) + `share_id`(:1130). **날짜·건 칸 없음.** |
| 사진 업로드 | `:21412-21470` (`/api/site-photo/owner-upload`) | 저장 키 = owner_phone, customer_phone(or share_id), label, note, uploaded_at_ms (:21453-21467). 앱도 그것만 보냄(`SitePhotoServerRepository:121-126`). |
| 사진 묶기 | `_web_pkey :27980-27983`, `_web_photo_bucket :28012-28063` | 키 = **전화번호 끝 8자리**. 협업 share 사진은 `web_feed_shares` 로 고객키에 합침(:28040-28060). 시간 오름차순 정렬(:28061). |
| 달력 점 | `/api/web/calendar :28273-28293` | 피드의 work_date 로 날짜별 jobCount, 고객키가 bucket 에 있으면 hasPhoto. |
| 현장 목록 | `/api/web/sites :28296-28323` | 피드 행마다 `photo_count = len(bucket[고객키])` → **모든 건 사진 수 합**. |
| 현장 상세 | `/api/web/site/{customer_digits} :28326-28385` | `photos_raw = bucket[고객키]` 전부(:28350). **`ba_guess`: 앞 절반 before / 뒤 절반 after**(:28354) — 건이 섞이면 무의미. |
| 다운로드 파일명 | `:28541-28560` | `feed[고객키].work_date + apartment` (:28547-28558) → 모든 사진이 **피드에 남은 한 날짜**로 이름 붙음. |

결론: **건이 2개면 PC 에서 1차·2차 사진이 확실히 섞인다.** 앱을 건 단위로 바꿔도 서버가 이 구조면 그대로다.

### 건별로 나누려면 (서버 변경 목록)

1. **피드를 (고객, 날짜) 키로.**
   - `web_schedule_feed` PK → `(owner_phone, customer_digits, work_date)`. SQLite 는 PK 변경 불가 → **새 표 만들고 복사 후 교체**(db_init 의 `CREATE TABLE IF NOT EXISTS` 패턴에 마이그레이션 블록 추가).
   - `/api/web/site/{customer_digits}` → `/api/web/site/{customer_digits}?work_date=` (또는 `job_key`) 로 건 특정. 기존 경로는 "가장 최근 건"으로 호환.
   - `/api/web/sites` 는 이미 work_date 로 정렬하니 행이 늘어나기만 하면 됨(:28306-28308).
2. **사진에 건 키 추가.**
   - `team_site_photos ADD COLUMN work_date TEXT`(nullable; 기존 `ALTER … try/except` 패턴 :1116-1120 재사용).
   - `owner-upload` 요청 `OwnerSitePhotoRequest` 에 `work_date: Optional[str] = None` (**Python 3.9 → `Optional[str]`, `str | None` 금지**).
   - `_web_photo_bucket` 키 = `(고객키, work_date)`. work_date 없는 옛 사진은 **앱 v50 백필과 같은 규칙**(찍힌 시각 ≤ 인 가장 늦은 건, 없으면 가장 이른 건 — `AppDatabase.kt:900-916`)으로 서버에서 붙이거나, "미분류" 통으로 보여주기(사장님 결정).
   - 팀원/협업 사진(`share_id`)은 `web_feed_shares` 가 이미 (share→고객) 매핑이라 여기에 work_date 도 실어 보내면 됨(`WebFeedRepository.FeedItem.shareIds :58`).
3. **앱 쪽.**
   - `WebFeedSyncManager` items 를 jobs 로(건마다 1 item, 해시 :94-96 에 work_date 이미 포함).
   - `OwnerPhotoUploadManager` 가 `p.jobId` → 그 건 `scheduledWorkDate` 를 `work_date` 로 동봉. jobId null 인 옛 사진은 앱에서 v50 규칙으로 먼저 붙인 뒤 올리기(`pendingUpload` :249-250 순서 유지).
4. **호환.** 서버 먼저 배포(새 칸·새 파라미터는 전부 optional) → 앱 나중. 옛 앱은 work_date 없이 올려도 "미분류"로 동작.
5. 배포는 `reference_ringgo_server_live_hotfix` 절차(py_compile → 백업 → swap → 200 검증). **사장님 승인 후에만.**

---

## 4) 순서 있는 작업 계획 (중간에 데이터가 안 깨지게)

원칙: **① 돈 이중 합산·덮어쓰기부터 막는다(순수함수+테스트) → ② 쓰기 경로를 전부 jobId 로 → ③ 미러를 한 방향(jobs→customers, 전 필드)으로 → ④ 읽기 화면을 하나씩 jobs 로 → ⑤ 사진(앱+서버) → ⑥ 캘린더·미러·접수서.** customers 표는 끝까지 **지우지 않는다**(§5-③).

### Step 0 — 회귀 테스트 그물 (코드 변경 없이 테스트만)
- `CashFlowCalcTest`, 새 `SettlementAggregationTest`: "같은 돈이 customers 와 jobs 양쪽에 있을 때 한 번만 센다" → 지금 코드에서 **실패해야 정상**(버그 증명).
- `JobRepositoryStageATest` 에 "2차(더 늦은 날) 등록 후 1차 총액 불변", "2차 취소 후 1차 돈 불변" 2건 추가 → 지금 코드에서 실패.
- 폰 확인: 없음(테스트만).

### Step 1 — 돈 집계 단일화 (이중 합산 제거) — DB 변경 없음
- 새 순수함수 `MoneyLedger.rowsOf(customers, jobs)`: **jobs 행이 있는 고객은 jobs 만**, 없는 고객(옛 데이터·날짜 없는 돈)만 customers. `SettlementViewModel.receivedInMonth`, `CashFlowCalc.buildItems`, `ClosingBriefViewModel.build`, `ReportViewModel`, `MirrorSyncManager.cashItems` 가 이걸 쓴다.
- 폰 확인: 정산 탭 상단 "이번 달 받은 돈"이 **정산 목록의 받은 돈 합과 같은지**(지금은 대표 건 입금이 있으면 더 크다). 현금흐름 달력 같은 날 같은 이름 잔금이 **한 줄**인지. 마감 브리핑(더보기→마감 브리핑) 오늘 입금이 정산과 같은지.

### Step 2 — 쓰기 경로 전부 jobId 로 + 미러 한 방향 — DB v54(데이터 치유만)
- `JobRepository` 에 `updateMoney(jobId, …)`, `setWorkCompleted(jobId, at)`, `updateAddress(jobId)`, `updateMemo(jobId)`, `cancel(jobId)` 추가. 각각 끝에 `recomputeMirror`.
- `recomputeMirror` 확장: 돈 5칸 + `workCompletedAt` + address 를 대표 건에서 복사. 대표 규칙에 **"완료(잔금 받음 포함)된 건은 미래여도 대표 아님"** 추가할지 사장님 결정(§8).
- `CustomerRepository.mirrorMoneyToRepresentativeJob` **삭제**, `syncRepresentativeFromCustomer` 호출 7곳 교체 후 삭제.
- 호출부 교체: `CustomerDetailViewModel :329-380, 450-453, 457-560`(취소 경로는 그 건만), `HomeViewModel :1116-1157`, `SettlementViewModel :239-269`, `ChatViewModel :1756-1832`(챗 "입금했습니다" 카드도 = 어느 건인지 고르기 — 건이 1개면 자동), `ScheduleAddViewModel :133-136` (customers 돈 재기록 3줄 삭제 — addJob 이 이미 넣음), `IntakeSyncManager :60-100`.
- **MIGRATION_53_54(치유)**: `UPDATE jobs SET workCompletedAt = customers.workCompletedAt WHERE jobs.workCompletedAt IS NULL AND 같은 날짜` (v53 :992-1017 과 동일 패턴, `runCatching`). 스키마 변경 없음. NOT NULL 칸 안 건드림.
- 폰 확인: 고객 정보 → 건 탭 2개 만들고 **2차에 총액 입력 → 1차 탭 총액 그대로**인지. 2차 예약 취소 → 1차 탭 돈·날짜 그대로인지. 홈 히어로 [완료] → 건 탭에 "완료" 뜨는지. 정산에서 잔금 받음 토글 → 그 건만 바뀌는지. **앱 재설치(신규 폰) 후 첫 실행 크래시 없음**(v54).

### Step 3 — 읽기 화면을 jobs 로 (한 화면씩 커밋)
순서: 정산 목록(④) → 홈 히어로·다음 시공·미수 카드(⑤) → 브리핑 화면·알림(③) → 일정 복사본에 돈·완료 copy(②) → 통계·리포트(⑪) → 오늘의 현장 알림·정기문자(⑥) → 챗 내 일정 시트·지오펜스.
- 공통 패턴: `ScheduleViewModel.state :43-58` 의 "jobs → CustomerEntity 복사본" 을 함수로 빼서 재사용하되, **돈 5칸·workCompletedAt 도 copy**.
- 알림 id: `famId(FAM_D1/FAM_SETTLE, customerId)` → jobId 로.
- 폰 확인(각 화면): 같은 고객 1차(지난·잔금 미수)·2차(내일) 를 만들어 두고 — 정산 목록에 **두 줄**, 홈 미수 카드에 1차 금액, 홈 "내일 시공" 에 2차, 마감 브리핑 "못 받은 돈"에 1차, 통계 "다녀온 현장"에 1차, 일정 탭 카드 하단 정산줄이 건마다 다른지.

### Step 4 — 사진 건별 (앱 → 서버 순으로 배포는 서버 먼저)
- 서버(§3 1~2) 먼저: 새 칸·새 파라미터 optional → 옛 앱 무해. py_compile 통과·200 검증.
- 앱: `addFromUri(customerId, jobId, uri)`(선택 건), `observeByJob`, jobId null 옛 사진은 앱 실행 시 1회 v50 규칙으로 붙임(`MIGRATION_49_50 :900-916` SQL 재사용), 업로드에 work_date 동봉, `WebFeedSyncManager` 건별 push.
- 폰 확인: 1차 탭에서 사진 올리고 2차 탭으로 → 안 보이는지. PC `si0in.kr/web` 달력에서 같은 고객 두 날짜가 **따로** 뜨고 각 현장에 그 건 사진만, 다운로드 파일명 날짜가 건마다 맞는지.

### Step 5 — 구글 캘린더·본폰 미러·접수서
- v55: `ALTER TABLE jobs ADD COLUMN calendarEventId TEXT`(nullable). `CalendarSyncManager`/`DefaultCalendarSyncStore` 를 job 단위로(이벤트 id·지문 키 = jobId). 기존 `customers.workCalendarEventId` 는 대표 건 것으로 **1회 이관**(같은 날짜 건에 복사) 후 읽지 않음. 자동동기화 지문(`CalendarAutoSync:46`) 에 jobs Flow 추가.
- 본폰 미러 ownItems·receivables = jobs.
- 접수서: 날짜가 기존 건과 다르면 addJob.
- 폰 확인: 2차 등록 → 구글 캘린더에 **이벤트 2개**(1차 그대로). 2차 날짜 수정 → 2차 이벤트만 이동. 본폰 뷰어에 두 날짜. 접수서 두 번째 제출 → 건 탭 하나 더.

### Step 6 — 화면 다듬기
- 건 1개면 탭 숨김(이미 `CustomerDetailScreen:714` 조건 있음 — "1건이어도 ＋ 입구는 남긴다" 9/17 결정과 충돌하니 사장님 재확인), 선택 건 편집 가능(PastJobPanel 읽기 전용 해제), 멀티데이 lane.

---

## 5) 건드리면 안 되는 것 / 함정 (이 저장소에 기록된 사고)

① **돈 이중 합산은 이미 진행 중인 사고다.** `SYNC.md 2026-09-11`(v49 "각 고객 예정 시공을 jobs 로 COPY") 시점부터 `SettlementViewModel:190-202`, `CashFlowCalc:72-132`, `ClosingBriefViewModel:62-66`, `ReportViewModel:100-104` 가 customers+jobs 를 그냥 더한다. 이전엔 archive 가 customers 를 리셋(`JobRepository:68-82`)해서 겹치지 않았지만, v49 이후 대표 건은 양쪽에 다 있다. → Step 0 테스트로 못 박고 Step 1 에서 고친다. (`feedback_symptom_dampening`: 한도·중복방지로 누르지 말고 **집계 출처를 하나로**.)

② **마이그레이션 = @Entity 와 한 글자도 다르면 앱이 안 켜진다.**
- `AppDatabase.kt:1047 fallbackToDestructiveMigrationFrom(1, 2)` — 그 외는 실패 시 **IllegalStateException 크래시 루프**(삭제 대신 크래시. 의도된 것, 2026-07-19).
- `SYNC 2026-09-17 09:45` 실사고: v52 backfill 이 `jobs.memo` 를 안 채워 **`NOT NULL constraint failed` 런치 크래시**. 원인: **새로 깐 폰의 jobs.memo 엔 DEFAULT 가 없다**(Room 이 @Entity 로 CREATE 하면 `@ColumnInfo(defaultValue)` 없는 칸은 DEFAULT 없음; ALTER 로 붙인 폰만 DEFAULT '' 있음). → **INSERT 는 NOT NULL 칸을 전부 명시**(`COALESCE(c.memo,'')`), 치유성 SQL 은 `runCatching`. `MIGRATION_51_52 :958-985`, `DataBackup.backfillJobsFromCustomers :427-450` 이 그 교정본.
- `MIGRATION_50_51 :942-945` 주석: CREATE 에 DEFAULT 쓰지 말 것(엔티티에 없는 기본값), 인덱스 이름 = `@Index(name)` 과 일치(`reference_room_entity_index_must_match_migration`).
- v54/v55 에서 칸 추가 시 **nullable 만**. NOT NULL 이 꼭 필요하면 `@ColumnInfo(defaultValue)` + `ALTER … NOT NULL DEFAULT` 쌍으로, 그리고 `DataBackup.backfillJobsFromCustomers` INSERT 컬럼 목록도 같이 수정.
- 배포 전 **로컬 sqlite 로 5경우 검증**(9/17 09:45 기록) + **신규 설치 폰 첫 실행** 필수.

③ **customers 시공 칸을 지우면 안 된다.** §2 표의 M 표시 리더 40여 파일(특히 `IncomingCallOverlay`, `CallStateReceiver`, `NewLeadsViewModel`, `EstimateFollowupCalc`, `AutoCategoryClassifier`, `CustomerStatusTag`, `CustomersScreen`, `GeofenceManager`, `CalendarAutoSync`, `ConversationAiRepository`, `DataBackup` 옛 백업 복원 :243-246)이 읽는다. **미러로 유지하되 쓰기는 jobs 만**(한 방향). "기록만 있고 안 쓰는 칸"이 되면 나중에 지운다.

④ **접수서 → jobs 구멍(9/15, v52)의 재발형.** `IntakeSyncManager:67` 가드는 "고객 날짜 없을 때만"이라 두 번째 접수서는 **jobs 에 아무것도 안 넣는다**(§2-⑨). Step 5 전까지 두 번째 접수서는 사장님이 ＋ 새 시공으로 수동 등록해야 함을 인지.

⑤ **알람 dedup 키 형식 변경 사고 방지.** Stage B 가 `d1:`→`d1j:` 로 바꾸며 업데이트 첫날 중복을 막으려고 옛 키도 봤다(`JobReminderCalc:204-207`). 알림 id·키를 또 바꾸면 같은 배려 필요.

⑥ **서버 Python 3.9.** `SYNC 2026-09-17 05:10` "Python 3.9.6 py_compile 통과". `str | None`(PEP 604) 쓰면 502 → `Optional[str]`(`reference_server_python39_no_pep604`). 라이브 서버는 repo 보다 뒤처져 있으므로 **통짜 배포 금물**, 단건 핫픽스 절차.

⑦ **web_schedule_feed 는 push 때마다 전량 DELETE 후 재삽입**(`:28122`). 앱이 건별 item 을 보내기 전에 서버 PK 를 바꾸면 옛 앱 push 가 그대로 동작(고객당 1행)하므로 순서는 서버→앱이 안전. 반대로 앱이 먼저 건별로 보내면 `INSERT OR REPLACE` 가 **같은 고객 두 건을 마지막 것으로 덮는다**(:28138).

⑧ **`remember(key) → onDispose 빈 값 저장`**(`SYNC 09-16 10:05`, 메모 유실). 건 탭 전환 시 메모 상자를 `selectedJobId` 키로 다시 만들면 **같은 사고**가 재현될 수 있다 → `shouldSaveMemo(dirty…)` 패턴(`CustomerDetailScreen:238-257`) 그대로 재사용.

⑨ **예약 취소 = 백지 규칙(2026-08-28 사장님)** 은 "그 건" 기준으로만. 지금 코드(`CustomerDetailViewModel:538-542`)는 고객 단위라 다른 건까지 지운다(§2-⑨).

⑩ **SettlementCalc 는 건드리지 않는다.** `rowOfFields :72-115` 가 돈 규칙 단일 출처(고객·건 동일). 집계(합산) 층만 바꾼다.

⑪ 확인 못 한 것: 실기에서 이중 합산 숫자·취소 시 돈 소실을 **재현하진 않았다**(코드 경로만 확정). Step 0 테스트가 그 증명.

---

### 사장님께 물을 것 (§8 — 지어내지 않음)
1. `customers.memo`(고객 공통 메모)를 남길지, 건 메모만 쓸지. (구글 캘린더 본문·PC 글 만들기 재료가 지금 고객 메모를 읽음)
2. 대표 건 규칙: 완료(잔금 받음)된 건은 미래여도 대표에서 빼나?
3. 정기 문자 회차 기준일 = 마지막 완료 건? 건마다?
4. PC 웹에서 날짜 없는 옛 사진은 "미분류"로 둘지, 찍힌 시각으로 자동 배정할지.
5. 건 1개일 때 탭 숨김(이번 지시) vs 9/17 "1건이어도 ＋ 입구 남김" — 어느 쪽?
