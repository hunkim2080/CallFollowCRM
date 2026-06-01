# RING-GO 구현 현황판 (프로토 ↔ 실제 앱)

> **이 문서 = 양쪽 Claude 공유 기준표(SoT).** 프리뷰 `design-preview/ringgo-redesign.html` 에서
> 결정된 디자인·기능이 실제 앱(Android, DB v19)에 얼마나 반영됐는지 영역별로 추적한다.
> 사람이 보기 좋은 인터랙티브 버전 = `design-preview/status-board.html` (같은 데이터).
>
> - 갱신: 2026-06-01 (최초 작성, android Claude)
> - 상태값: ✅ 완료 / 🔶 검수중 / 🔷 진행중 / ⬜ 시작전(프로토에만)
> - **항목 상태가 바뀌면 이 표 + status-board.html 둘 다 갱신**할 것.

## 전체 진행 요약

| 영역 | 완료 | 핵심 빈칸 |
|---|---|---|
| 상담함(홈) | 7/9 | (히어로·다음시공·자동답장·정기문자·견적회신 ✅) 통화요약·팀사진(서버) 남음 |
| 채팅 | 5/7 | (통화구간 ✅) 다듬기(검수)·접수서 단계 남음 |
| 일정 | 5/5 | ✅ 완료 (월달력·셀프등록·시간칩·여러날·채팅 내일정) |
| 정산 | 8/8 | ✅ 완료 (미수금·현금흐름·순이익·월매출·일당 자동차감) |
| 수첩 | 4/4 | ✅ 완료 (일당·거래처·자주쓰는문구·함께한 현장) |
| 견적·접수서 | 4/5 | (사업자정보·평당계산·정식견적서공유 ✅) 시공접수서만 남음 |
| 더보기 | 6/7 | (리포트·정기문자·자동문자 ✅) 팀 관리(서버)만 남음 |
| 온보딩 | 4/4 | ✅ 완료 (권한·업종·막내비서·스토리텔링) |
| 데이터·AI 기반 | 3/3 | (토대 완성) |

전체: **45 완료 / 52 항목 (약 87%)**. 나머지는 진행중/시작전. (2026-06-01 누적 22개 기능. 추가: 견적 회신 리마인드 — 견적 보낸 지 N일 답 없는 고객 챙기기, DB 변경 0. 상담함 7/9)

---

## 상담함 (홈)
- ✅ 미확인·오늘신규·주간예약 카운트 — `HomeViewModel` KPI 3종
- ✅ 문자·통화 타임라인 + 스팸 밀기 — `HomeScreen` swipe→spamSuffixes
- ✅ **오늘 시공 히어로(D-DAY)** — `TodayHeroCard` 다크 히어로 + 주소 + 길찾기 (2026-06-01)
- ✅ **다음 시공 1~3곳 미리보기** — `HomeViewModel.nextJobs`
- ✅ **부재중→자동답장 카드** — 막내가 자동 발송한 첫 인사 기록을 홈 상단 노출(최근 24h, AUTO_SENT/실패). 탭→대화. `HomeViewModel.autoReplies` + `AutoReplyCard`. `MessageHistoryDao.observeRecentAutoReplies`
- 🔷 통화 요약 카드 — `AiSummaryEntity` observe만
- ✅ **정기문자 발송 대기 알림** — `RecurringDueCard`→`RecurringDueScreen`. 시공 후 N일 된 고객 정기문자를 홈 카드+목록으로. [보내기]=채팅 본문 prefill, [넘기기]=회차 제외. 포그라운드 계산.
- ✅ **견적 회신 리마인드** — `EstimateFollowupScreen`(홈 📋 카드). 채팅 견적 작성/공유 시 `ESTIMATE_SENT` 기록 → N일 후 시공일 미등록이면 "한 번 더 여쭤볼까요?". `EstimateFollowupCalc`(테스트 6). DB 변경 0. (접수서 작성 리마인드는 시공접수서=서버 후)
- ⬜ 팀원 현장사진 알림 (팀 관리=서버 후)

## 채팅
- ✅ 메시지 타임라인 + 사진 뷰어 — `ChatScreen`
- ✅ AI 추천답변 칩(의도 분화 v2) — `ReplySuggestions`/맥미니 `POST /prepare-reply`
- ✅ 문자 템플릿 칩 넣기 / ✅ 별표 중요 메시지
- 🔶 다듬기 ✨ — `OllamaRefineRepository` 동작(품질 부족 → Claude 교체 예정)
- ✅ **통화 구간 표시** — `CallSegment`(ChatScreen). `callRecords`(suffix 매칭)와 messages 를 `buildChatTimeline` 으로 시간순 병합(loadMessages 무손상). 타임라인에 "📞 수신/발신/부재중 · N분" 청록 카드. (AI 요약 가져오기는 ↓ "통화 요약 카드"=서버)
- 🔷 채팅에서 견적 작성 (다이얼로그 있음, 접수서/PDF 단계 없음)

## 일정
- ✅ 월 달력 + 시공일 표시 — `ScheduleScreen`(과거+미래)
- ✅ **셀프 일정 등록** — `ScheduleAddScreen`(일정 FAB→폼). 이름·번호·시공일·주소·총금액·계약금+받음.
- ✅ **시공 시간 칩** — `scheduledWorkMinutes`(DB v24). 미정+오전8~오후4 칩. 카드/홈 히어로 "🕐 오전 9시". `DateTimeUtils.formatWorkMinutes`.
- ✅ **시공 기간(여러 날)** — `scheduledWorkDays`(DB v24). 당일/2·3·5일/일주일. `jobCoversDay` 로 달력 점·선택일 목록·todayJobs 모두 기간 내 표시. 카드 "M/D~M/D · N일간".
- ✅ **내 일정 확인(채팅에서)** — `ChatScreen` 달력 아이콘 → `MyScheduleSheet`. 월 미니 달력(여러날 점) + 날짜 탭 → 그날 시공(이름·🕐시간·📍주소)/"비어있어요". 읽기 전용. `ChatViewModel.scheduledJobs`.

## 정산  ← **1순위. Phase 1 완료 (2026-06-01) — `docs/plan_settlement.md`**
- ✅ 미수금 대시보드 — `SettlementScreen` (히어로 미수총액 + 전체/미수/완납 필터 + 고객 카드). 홈 `OutstandingCard` 진입.
- ✅ 계약금·잔금 받음 토글 — 정산 카드 토글, 실시간 미수 변동. `SettlementViewModel`
- ✅ 완납 확인 + 완납 취소 — 잔금 체크 시 "전액 받았어요?" 확인(오탭 방지) + 완납취소.
- ✅ **현금흐름 4색 달력** (Phase 2, DB v20) — 정산 "현금흐름" 탭. `CashFlowSection`. 지난날 회색 + 범례.
- ✅ **직접 현금 기록** — `ManualCashEntity`(DB v20). 수입/지출·완료/예정·삭제.
- ✅ **확정/예상 순이익** — 현금흐름 월 요약(netDone/netPlanned).
- ✅ **월 매출 전월대비** — `ReportViewModel` prevRevenue/deltaPct + 리포트 ▲▼ 표시.
- ✅ **일당 자동 차감** — `JobCrewEntity`(DB v23). 셀프 일정 등록 시 일당 배정 → `CashFlowCalc` 가 그 날 자동 −지출.
- 계산 단일출처 `domain/settlement/SettlementCalc`+`CashFlowCalc` + 테스트 `SettlementCalcTest`(7) `CashFlowCalcTest`(5) 통과.

## 수첩
- ✅ **일당 관리** / ✅ **거래처 관리** — `NotebookScreen`(설정→수첩). `NotebookContactEntity`(DB v21, kind 통합). CRUD + 전화/문자.
- ✅ **자주 쓰는 문구** — `PhraseSheet`(수첩 문자 시 문구 시트, 일당/거래처 따로 + 편집). prefs 저장.
- ✅ **함께한 현장** — `JobCrewEntity`(DB v23) 배정 이력. 일당 카드 "함께한 현장 N회" → 날짜·고객·일당 목록.

## 견적 · 시공접수서
- ✅ 가격표 관리(CRUD) — `PricingItemsScreen`
- ✅ **평당 계산 옵션** — `PricingItemEntity.unit`(DB v22) 정액/평당. 채팅 견적 다이얼로그 평수 stepper + "평당 X원 × N평" 합산.
- ✅ **사업자정보 등록** — `BusinessInfoScreen`(설정). 상호·대표·사업자번호·주소·연락처·유효기간(prefs).
- ✅ **정식 견적서 + 공유** — 채팅 견적에 사업자정보 헤더+유효기간 자동 삽입 + ACTION_SEND 공유(카톡/문자). (직인 이미지/PDF 파일은 추후)
- ⬜ 시공접수서(고객 자가확인, 서버 필요)

## 더보기 · 설정
- ✅ 문자 템플릿 관리 / ✅ 사장님 톤(말투) 학습 / ✅ AI 서버 상태 표시
- ✅ **자동문자(부재중/D-1/도착)** — 부재중 `AutoReplyScheduler` + 시공 D-1·도착 안내 `ScheduleReminderScreen`(홈 카드→목록, 보내기=채팅 prefill). `ScheduleReminderCalc`(테스트 7). 로그는 recurring_message_log 음수 sentinel 재사용(DB 변경 0).
- ✅ **비즈니스 리포트** — `ReportScreen`(설정). 기간별 매출(받은 돈)·시공 현장·새 고객·현재 미수금. 기존 데이터 집계.
- ✅ **정기문자 예약** — `RecurringMessagesScreen`(설정→정기문자, DB v25 `recurring_messages`). 대상(시공전체/카테고리)·주기(N일)·시각·문구({고객명}) 규칙 CRUD + 켜기/끄기. 자동발송 X(확인 후). `RecurringDueCalc`(테스트 7).
- ⬜ 팀 관리(99k, 서버 필요)

## 온보딩
- ✅ 권한 요청 화면 — `OnboardingPermissionScreen`
- ✅ **업종 선택(다중 최대 3, 해자)** — `TradeSelectScreen`(설정→내 업종, prefs). 첫 실행 통합은 추후.
- ✅ **막내 비서 캐릭터** — `Mascot`(Compose Canvas, 안전모) + 온보딩 "막내 비서 탄생!" 배지.
- ✅ **스토리텔링 캐러셀** — `HorizontalPager` 4장(막내비서/답장/미수금/일정) + 점 인디케이터.

## 데이터 · AI 기반
- ✅ Room DB v25 (20개 엔티티) / ✅ 맥미니 AI 서버 연동 / ✅ 추천 이벤트 로깅

---

## 서버(맥미니) Claude 가 알아야 할 것
- 정산 Phase 1 은 **클라이언트 로컬 데이터만** 사용 (CustomerEntity total/deposit/balance).
  서버 API 추가 불필요. 추후 비즈니스 리포트(매출 집계)·시장 인사이트 단계에서
  서버 집계가 필요해지면 이 문서에 "서버 액션" 블록으로 요청 추가.
- 정산·수첩·일정 셀프등록은 모두 안드로이드 단독 구현 가능(로컬 DB). server/ 변경 없음.
