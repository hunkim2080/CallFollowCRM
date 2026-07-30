# 정산 탭 구현 세부계획 (1순위 타깃)

> 기준표: `docs/IMPLEMENTATION_STATUS.md` · 프로토 출처: `design-preview/ringgo-redesign.html`
> 작성: 2026-06-01 (android Claude). **이 문서대로 단계별 구현.**

## 왜 정산이 1순위인가
- 사장님 핵심 통점 = "1인 시공자는 **누가 돈 안 줬나** 추적이 제일 어렵다".
- 프로토엔 19개 정산 기능이 있는데 **실제 앱은 화면 0** = 빈칸이 가장 큼 = 임팩트 최대.
- 데이터(총액/계약금/잔금/받은시각)는 이미 `CustomerEntity` 에 있음 →
  **Phase 1 은 DB 변경 없이** 바로 화면만 얹으면 됨 (리스크 최소).

## 데이터 현황 (이미 있는 것)
`CustomerEntity`: `totalAmount`, `depositAmount`, `depositPaidAt`, `balanceAmount`, `balancePaidAt`
`CustomerRepository`: update* 메서드 전부 존재 (`updateDepositPaidAt`, `updateBalancePaidAt`, `updateTotalAmount`, …)
→ Phase 1 에 **신규 엔티티/마이그레이션 불필요.**

---

## Phase 1 — 미수금 정산 화면 (DB 변경 없음, 먼저 구현)
**목표:** "안 받은 돈 총액 + 고객별 계약금/잔금 받음 체크 + 완납/완납취소".

### 미수 판정 규칙 (일상어)
- 한 고객의 **받을 돈** = `totalAmount`(없으면 deposit+balance 합).
- **받은 돈** = (계약금 paidAt 있으면 depositAmount) + (잔금 paidAt 있으면 balanceAmount).
- **미수** = 받을 돈 − 받은 돈. 0 이면 **완납**.
- 돈 정보가 아무것도 없는 고객(total/deposit/balance 모두 null)은 정산 목록에서 제외.

### 화면 구성 (토스 스타일, 프로토 renderSettle 참고)
1. **히어로**: "아직 못 받은 돈" 큰 숫자(미수 총액) + "이번 달 받은 돈" 보조.
2. **필터 칩**: 전체 / 미수 / 완납.
3. **고객 카드(`settleRowHtml`)**: 이름·번호 + 받을돈 + [계약금 받음 ◻︎] [잔금 받음 ◻︎] 토글
   + 미수면 빨강 금액, 완납이면 회색 + "완납" 배지.
   - 토글 = `setDepositPaid/setBalancePaid` (이미 있는 ViewModel 패턴 재사용).
   - **완납 확인**: 잔금까지 체크 시 `openConfirm` 한 번 ("전액 받았어요?") = 오탭 방지.
   - **완납 취소**: 완납 카드의 "완납 취소" → paidAt 되돌리기 (프로토 payUndo).
4. 카드 탭 → 기존 `CustomerDetailScreen` (돈 금액 수정은 거기서).

### 진입점 (추천)
- **홈에 "미수금" KPI/카드 추가** → 탭하면 정산 화면. (정산이 핵심 가치 → 홈에서 바로 보이게)
- 하단탭 전면 도입은 큰 리팩터라 Phase 1 범위 밖. 일단 홈 진입 + `Destinations.SETTLEMENT` 라우트.

### 만들/고칠 파일
- 신규 `data/local/dao/CustomerDao.kt` 쿼리 또는 ViewModel 필터 — 돈 있는 고객 observe (마이그레이션 X).
- 신규 `presentation/screen/settlement/SettlementViewModel.kt` — 미수 계산 + 필터 상태 + 토글.
- 신규 `presentation/screen/settlement/SettlementScreen.kt` — 위 UI (Compose, 토스 토큰).
- `navigation/Destinations.kt` — `const val SETTLEMENT = "settlement"`.
- `navigation/AppNavHost.kt` — composable 등록 + 홈 콜백.
- `home/HomeScreen.kt` + `HomeViewModel.kt` — 미수금 진입 카드 + `onOpenSettlement`.
- 테스트 `app/src/test/.../SettlementCalcTest.kt` — 미수 계산 규칙 회귀.

### 검수 (사장님 폰)
`scripts/ringo.ps1` 로 화면 캡처 + 빌드. 빈 상태/일반/완납 케이스 확인.

---

## Phase 2 — 현금흐름 달력 + 직접 기록 (DB v20) — 2026-06-01 완료
- [x] `ManualCashEntity` + DAO + Repository, 마이그레이션 v19→v20(additive, index name 일치). AppContainer 배선.
- [x] `CashFlowCalc`(순수: settle 파생 수입 + manual 합산, 4색 agg) + `CashFlowCalcTest`(5케이스).
- [x] 정산 화면 상단 탭 [미수금][현금흐름]. `CashFlowSection` = 월 4색 달력(지난날 회색·범례) + 월 순이익(확정/예상) + 선택일 상세.
- [x] 직접 기록 추가 다이얼로그(금액·메모·수입/지출·완료/예정) + 예정↔완료 토글 + 삭제.
- [x] assembleDebug 성공. **마이그레이션은 폰 첫 실행 시 동작 → 검증 필요**(additive+fallback 안전망이라 위험 낮음).

## Phase 3 — 일정↔정산 연결 + 순이익/월매출
- 일당 자동 차감(`crewCash`)은 **수첩(일당) 구현 후** 가능 → 수첩 영역과 함께.
- 확정/예상 순이익, 전월 대비 월 매출.
- (비즈니스 리포트·시장 인사이트는 서버 집계 필요 → 그때 server Claude 에 요청.)

---

## 진행 체크리스트 (Phase 1) — 2026-06-01 완료
- [x] SettlementCalc (순수 계산) + SettlementViewModel (미수 + 필터 + 토글)
- [x] SettlementScreen (히어로 + 필터 + 카드 + 완납확인/취소)
- [x] Destinations.SETTLEMENT + AppNavHost 라우트
- [x] 홈 미수금 진입 카드 (OutstandingCard) + HomeViewModel outstandingTotal/Count
- [x] 단위 테스트 SettlementCalcTest (7케이스 통과)
- [x] 빌드(assembleDebug) 성공 + status-board/STATUS 갱신
- [ ] **사장님 폰 검증 대기** — 갤S9에서 정산 화면 + 토글 동작 확인 (아침에)

> 진입점 = "홈 미수금 카드" 는 사장님 선택. 나머지 세부(완납 확인 문구, 잔금 추정 규칙 등)는
> Claude 추천으로 진행 — `docs/DECISIONS_2026-06-01.md` 참고.
