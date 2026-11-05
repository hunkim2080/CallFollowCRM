# 실제 기능 근거와 예시 데이터

조사 기준: 2026-11-05 로컬 저장소. 아래 경로는 저장소 루트 기준입니다.

화면을 새 제품 기능으로 제안하는 사이트가 아닙니다. 현재 구현에서 확인한 구성과 동작을 작은 휴대폰 안에 HTML/CSS로 재현했습니다. 설명용으로 일부 화면을 축약했고, 정산 확인 창은 흐름을 읽기 쉽도록 같은 화면 안에 함께 제시했습니다. 명칭·필드·순서·핵심 색상은 실제 소스를 기준으로 했습니다. 고객 데이터는 전부 새로 지었습니다.

## 참고 범위

- `CLAUDE.md` §12 공용 기능 표: 건별 금액, 시공/입금 상태, 통화 요약, 협업, 정산서의 근거 확인.
- `docs/PROTOTYPES.md`: 현재/기획/미착수 구분, 브랜드 v5, 시간 구간 요약, 답장 안 함, 예약 문자, 사진 표시 등 확인. 외부 artifact 원문은 확보하지 않아 현재 Compose와 앱 내 데모를 구체적 화면 근거로 사용.
- `docs/GOTCHAS.md`: 업무폰 읽기 전용, 기존 코드 및 배포 범위 참고. 휴대폰에 접근하지 않음.
- [모모콜](https://momocall.kr/): 문제 공감 → 구체적 화면 → 사용 흐름 → 요금 → FAQ 순서를 참고. 타사 문구·성과 수치·사용 후기·리워드 기능을 전용하지 않음.

## 화면과 경로

공통 접두사: `app/src/main/java/com/detailline/callfollowcrm/`.

| 사이트의 화면/설명 | 실제 근거 | 반영한 내용 |
|---|---|---|
| 상담함 | `presentation/screen/home/HomeScreen.kt`, `HomeScreenHelpers.kt` | 상담함 제목, 검색, 전체/답장 안 함/시공 대기/잔금 대기, 오늘 시공, 부재중 응답 기록, 최근 대화, 확인 처리 |
| 문의 대화 | `presentation/screen/chat/ChatScreen.kt`, `ChatSmallParts.kt` | 고객 대화, 추천 답장 확인, ⊕ 입력 메뉴, 고객 카드 진입 |
| 부재중 문자 | `presentation/screen/settings/SettingsScreen.kt`; `app/src/main/assets/demos/missedcall.html` | 더보기 → 자동으로 챙기기. 켠 기능/권한 조건 아래 자동 응답. 모든 추천 답장이 자동 발송된다는 표현 제외 |
| 통화 요약 | `presentation/component/CallSummaryBody.kt`; `presentation/screen/chat/ChatTimelineSegments.kt`; `app/src/main/assets/demos/callsummary.html` | 시간 구간·화자 점·손님/나 범례·AI 짐작 안내. 녹음 있는 구간의 재생. 사이트에 실제 녹음은 없음 |
| 일정 | `presentation/screen/schedule/ScheduleScreen.kt`, `ScheduleCalendarGrid.kt`, `ScheduleParts.kt` | 월 달력, 날짜 선택, 내 시공과 협업의 시간순 표시, 길찾기, 금액 토글, 날짜 길게 누르기, 일정 추가 |
| 견적과 접수서 | `presentation/screen/chat/ChatScreen.kt`, `ChatEstimateSheet.kt`, `QuoteDocScreen.kt`, `SlashCommandMenu.kt`; `domain/chat/SlashCommand.kt` | /견적·/접수서, 항목·금액·계약금, 견적서(직인)로 보내기, 미리보기, 문자에 첨부. 품목/규격/금액 표, 서비스·할인, 부가세 표기 |
| 사업자 정보 | `presentation/screen/business/BusinessInfoScreen.kt`, `BizEstimatePreview.kt` | 상호·대표·직인 입력, 견적서 미리보기 |
| 접수 일정 확인 | `domain/intake/IntakeScheduleState.kt`; `presentation/screen/chat/ChatTimelineSegments.kt` | 등록/기존 날짜 유지/날짜 없음 구분. 무조건 새 날짜를 덮는다는 설명 제외 |
| 협업 요청 | `presentation/screen/customer/CustomerCollabParts.kt`, 특히 `같이 할 사장님` 시트 | 현장 → 누구를 부를까요 → 번호로 부르기 → 사람별 일당 → 시간 → 전해둘 말 → 정보 범위 → 요청. 상대 수락 필요 |
| 협업 수신·진행 | `presentation/screen/sharedsite/SharedSiteScreen.kt`, `SharedSiteParts.kt`; `presentation/screen/customer/CustomerCollabParts.kt`; `app/src/main/assets/demos/collab.html` | 요청 수락/거절, 받은 현장, 출발·도착·완료, 사진·메모. 고객 전화와 고객 대화 미공유 |
| 현장 메모·사진 | `presentation/screen/customer/CustomerDetailJobCard.kt`, `CustomerDetailScreen.kt`, `CustomerPhotoMarkFlow.kt`; `docs/PROTOTYPES.md` 사진 표시 항목 | 건별 현장 메모·현장 사진, 크게 보기 → 표시 → 펜 → 완료, 원본 유지/새 사진 추가, A/S 잡기 |
| 잔금 | `presentation/screen/settlement/SettlementScreen.kt`, `SettlementViewModel.kt`; `data/repository/WorkCompletionManager.kt` | 월별 받은 돈·목표·전체 미수, 전체/미수금/완료 필터, 계약금/잔금 구분, 전액 받았어요? / 네, 완납 |
| 일당 정산서 | `presentation/screen/customer/CustomerCollabScreen.kt`; `presentation/screen/sharedsite/SharedSiteSettlement.kt`; `domain/collab/SettlementStatement.kt`; `util/SettlementShot.kt` | 완료된 현장이 있는 달 선택, 기간·발행인·현장별 일당·합계, 이미지 저장·공유. 송금 기능 주장 없음 |
| 첫 사용 | `presentation/screen/onboarding/OnboardingScreen.kt`, `OnboardingSetupScreen.kt`, `OnboardingPermissionScreen.kt`; `presentation/screen/settings/SettingsScreen.kt` | 업종/직접 입력, 상호/지역, 전화·문자 권한, 녹음 자동으로 찾기, 삼성 자동 녹음 안내, 문구·가격표 준비 |
| 예약 문자 | `docs/PROTOTYPES.md` 예약 문자 항목; `presentation/screen/home/HomeScreen.kt`; `presentation/screen/chat/ChatScreen.kt` | 작은 시계로 예약, 상담함 위 예약한 문자, 대기 말풍선/예약취소, 사진은 기본 문자 앱 조건 |
| 내 기록 | `presentation/screen/stats/StatsScreen.kt`; `presentation/screen/tour/FeatureTourScreen.kt`; `app/src/main/assets/demos/myrecord.html` | 완료 기록과 성과가 쌓이는 실제 사용 동기를 표현. 점수·금전 보상·출석 보상은 만들지 않음 |
| 보류 | `presentation/screen/home/HomeOnHoldParts.kt`; `presentation/screen/customer/CustomerDetailJobCard.kt` | 날짜 미룬 고객의 일정 보류, 상담함 보류 구분 |
| 공통 외형 | `presentation/theme/DesignSystem.kt`; `presentation/component/TossComponents.kt`, `RingTabBar.kt`, `Mascot.kt` | 앱 바탕 #F4F5F7, 주색 #3182F6, 협업 보라, 정산 빨강, 상담함/일정/정산/내 기록/더보기, 노란 안전모 막내 |

## 가상 데이터의 연결

- 기준일은 **2026년 11월 5일 목요일**. 달력은 실제 2026년 11월 배치입니다.
- 바른시공 / 대표 강현우: 페이지에서 하루를 따라가는 가상 업체.
- 박지현 / 구의동 한빛아파트: 오늘 오전 8시 욕실 타일 보수. 기존 견적 115만원, 받은 계약금 30만원, 남은 잔금 85만원. 현장 메모 → 마감 기록 → 저녁 잔금 확인에 같은 고객 사용.
- 정다운시공 / 송파: 오후 2시에 지원하는 별도 협업 현장. 아침 달력에서 내 현장과 함께 확인하는 예시.
- 최민서 / 성수동 은솔빌라: 09:37 부재중 → 09:40 문의 → 11:20 전화 → 12:30 견적 → 13:00 협업 요청으로 이어지는 신규 문의. 시공은 **11월 7일 토요일 오전 9시**로 협의.
- 최민서 견적: 보수 90만원 + 자재·운반 30만원 + 청소 서비스 − 할인 5만원 = **115만원(부가세 포함)**. 계약금 30만원 / 잔금 85만원. 박지현 건과 우연히 같은 금액인 별도 예시이며 신규 견적을 오늘 매출로 잡지 않음.
- 이준호 사장님: 11월 7일 성수동 현장 요청을 받는 동료 시공자. 일당 30만원.
- 이준호 일당 정산서: 이전에 완료한 11월 2일 성수 28만원 + 11월 4일 구의 30만원 = **58만원**. 11월 7일 요청은 미완료이므로 제외.
- 월별 받은 돈 230만원 / 목표 1,000만원 / 23%는 해당 업체의 가상 월 누적 예시. 앱 효과·실제 업체 실적을 뜻하지 않음.
- 모든 전화번호는 `010-0000-…` 형식의 설명용 값. 주소·이름·출입번호도 설명용으로 창작. 실제 고객 데이터·연락처·현장 사진은 가져오지 않음.

## 의도적으로 넣지 않은 내용

- 모집 문구, 추천인·통화 포인트·금전 리워드, 다운로드 수·평점·매출 상승률·가상 사용자 후기.
- 가격 확정, 플랜별 기능 제한, 추천 요금제, 무료·무제한 약속.
- 근처 시공자 자동 매칭, 은행 자동 입금 확인, 자동 송금, iOS 지원.
- 기획 문서만으로 확정하기 어려운 웹 글 스튜디오·블로그 자동 발행·전후 영상 선택 흐름.
- 법무 문구·사업자 번호·지원 연락처 등 확인되지 않은 기업 정보.

기존 `app/`, `server/`, `docs/`에는 기록을 추가하거나 변경하지 않았습니다. 조사 결과는 이 사이트 폴더 안에서만 보관합니다.
