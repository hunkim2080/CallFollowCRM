# 프로토 1:1 이식 현황판 — 요소 단위 (SoT)

> **이 문서 = 프로토타입(`design-preview/ringgo-redesign.html`) ↔ 실제 앱을 화면이 아니라
> "요소(섹션·배너·카드·카피·디자인 값)" 단위로 1:1 대조하는 단일 기준표.**
> 사람이 보기 좋은 인터랙티브 버전 = `design-preview/oneone-board.html` (같은 데이터, 사장님 보기용).
> 기능 진행 추적은 `docs/IMPLEMENTATION_STATUS.md`(기능 축) — 이 문서는 **디자인 1:1 축**으로 별개.
>
> - 갱신: 2026-06-02 · android Claude
> - 룰 근거: `CLAUDE.md §0` (프로토 = 실전 스펙, 100% verbatim)

## 상태값
| 표기 | 뜻 |
|---|---|
| ✅ 똑같음 | 프로토와 1:1 확인됨 |
| 🟡 폰 확인 | 옮겼지만 사장님 폰에서 눈으로 1:1 검증 대기 |
| 🔵 다른 부분 | 일부 다르거나 진행 중 |
| ⬜ 아직 | 시작 전 / 의도적 보류(99k·서버 의존) |

---

## ⭐ 이 현황판을 쓰는 이유 + 프로토 바뀌면 반영하는 절차

**왜 "요소 단위 + 프로토 스펙 값"으로 쪼갰나:**
화면 단위("채팅 ✅")로만 두면, 나중에 프로토에서 채팅 입력창 색만 바꿔도 그게 현황판에 안 잡힌다.
요소마다 **프로토 출처 함수**(예: `renderHero`)와 **프로토 스펙 값**(색 hex·px·카피 verbatim)을 박아두면,
프로토가 바뀐 그 요소만 콕 집어 ⬜/🔵 로 되돌려 **놓친 개선이 없게** 만든다.

**프로토를 새로 만들거나 고쳤을 때 (Claude 작업 순서):**
1. 바뀐 화면의 프로토 **출처 함수**를 다시 읽는다 (이 문서 각 요소의 `프로토 출처`).
2. 프로토 스펙 값(색·px·카피·배치)을 새 프로토와 비교.
   - 달라진 요소 → 상태를 ⬜/🔵 로 내리고 `프로토 스펙` 값을 새 값으로 갱신.
   - 프로토에 **새로 생긴 요소** → 새 항목으로 추가(상태 ⬜).
3. 앱을 새 스펙으로 옮긴 뒤 → 🟡 (폰 확인) → 사장님 확인 후 ✅.
4. `oneone-board.html` 의 `AREAS` 배열도 같이 갱신(둘은 같은 데이터).

> 즉 **이 문서가 "프로토 변경 → 앱 반영" 의 체크리스트로 자동 동작**한다.
> 프로토만 고치고 앱을 안 고치면 그 요소가 🟡/⬜ 로 남아 눈에 띈다.

---

## 전체 요약 (요소 기준)
- 똑같음 ✅ + 폰확인 🟡 + 다른부분 🔵 + 아직 ⬜ = 화면 11영역, 요소 단위로 추적.
- 핵심 6화면(홈·채팅·일정·정산·통계·더보기 막내)을 요소 단위로 분해. 나머지(견적/접수서·온보딩·팀·알림)는 굵게.

---

## 🏠 홈 — 상담함
> 프로토: `switchTab('home')` · `renderHero` · `renderTodayNew` · `renderWaiting` · `renderRecent`

| 요소 | 상태 | 프로토 출처 | 프로토 스펙(verbatim) / 앱 메모 |
|---|---|---|---|
| 상단 앱바(상담함·AI배지·고객·검색) | ✅ | renderAiBadge/openCustomers/openSearch | ai-badge gradient #EAF2FF→#F1ECFF border #E0E7FB r999 p7x12 12 w800 + dot 6px success **+glow ring** + ✨13 + "{대표업종} AI". dot 색=서버상태 유지, 탭=서버상태 토스트(프로토 aiInfo와 다름·더 실용적) |
| 오늘 신규 문의 카드 | ✅ | renderTodayNew | tn-t "오늘 신규 문의 <b>N통</b>" / tn-s "새 번호 기준 · 어제 N통" / ▲▼-. 이제 전용 "신규 고객 · 날짜별" 화면으로 연결(임시연결 해소) |
| 신규 고객 · 날짜별 화면 | 🟡 | openNewLeads/renderNewLeads | nl-hint ✨ 안내 + cfilter(전체/미답장만) + nl-date "오늘·N통" + nl-row(nl-dot 미답장=red + nl-ph 14 w800 + nl-t + nl-memo, 답장함 태그/재연락 버튼). NewLeadsScreen+VM 신설. 실데이터(시공일 미등록 고객, 응대기록=observeRepliedCustomerIds). 줄탭→상세, 재연락→채팅. 빌드 OK, 폰 확인 |
| 오늘 시공 히어로(D-DAY) | ✅ | renderHero→heroJobHtml | grad #272D3D→#14171F r24 p20, 🟢"오늘 시공·D-DAY" 11.5/white.62, name 23 w800+" · {time} 예정", 📍addr 13/white.78, [길찾기 light][전화][완료 ghost]. f2357a2 |
| 히어로 — 시공 없는 날 | 🟡 | renderHero→heroEmptyHtml/nextHeroHtml | hero-empty 흰 r24: he-top "오늘 시공" + he-title "오늘은 예정된 시공이 없어요" + he-sub "밀린 상담·견적 챙기기 좋은 날이에요." + he-next 회색박스(nx-ic 36 + "다음 시공 · 내일(5/30) {time}" + nx-name, 여러곳=nx-line 시간칩) + he-add "+ 일정 직접 추가". 1:1 재작업(빌드 OK), 폰 확인 |
| 배너 — 견적 회신(보라) | ✅ | EstimateFollowup | inbox-alert 보라 4dp + go pill |
| 배너 — 부재중 자동답장(파랑) | ✅ | renderMissed | inbox-alert 파랑 + [대화]. f2357a2 |
| 배너 — 내일 시공 D-1/도착(앰버 remind-card) | ✅ | openArrival 계열 | remind-card 좌3dp amber r18 p16, label 11 w800 #B8780A "내일 시공 안내·D-1", msg박스 r12 p12 13.5/1.55, [건너뛰기][문자 보낼까요?]. f2357a2 |
| 배너 — 정기문자 차례(앰버) | ✅ | openRecurDue | inbox-alert amber + [보내기]→RecurringDueScreen |
| "지금 답장 기다려요" + AI 즉시발송 | ✅ | renderWaiting | sugbox #EEF4FF "✨ AI 추천 답변" + 본문 + 40dp 파란 Send. "AI 답변 준비 중…" 변형. dd6b729 |
| "최근 대화" 목록 | ✅ | renderRecent | recent-row avatar+이름+미리보기+시각 |
| 하단 탭바 4개 | ✅ | switchTab | 홈/일정/통계/더보기 |

## 🗨️ 채팅 (2026-06-02 재작업, 폰 확인 대기)
> 프로토: `openChat` · `renderChatMsgs` · `renderSugs` · 하단 chat-actions/composer

| 요소 | 상태 | 프로토 출처 | 프로토 스펙 / 앱 메모 |
|---|---|---|---|
| 상단 이름 | 🟡 | openChat→chat-name | 고객명(없으면 아파트+호수) |
| **요약 바(chat-summary ✨)+펼치면 상세** | 🟡 | openChat→chat-summary | 흰 바 p10x18, 12.5 #1B64DA w600, 아래 테두리, ✨ + "요약: …". 사장님 결정="둘 다": CollapsedSummaryHeader 프로토 바로 재스타일 + 기본 접힘 시작, 탭→UnifiedSummaryCard 펼침. 빌드 OK, 폰 확인 |
| 말풍선(나/고객·사진·시각) | 🟡 | renderChatMsgs(.bubble/.brow/.btime) | bubble p11x14 r19+꼬리6. cust 흰+그림자/좌꼬리, me 파랑/우꼬리. btime 말풍선 밖 10.5 t3. ChatBubble 1:1 재작업(연파랑→흰, 시각 밖으로). 빌드 OK |
| 날짜 구분선(chat-date) | 🟡 | renderChatMsgs | 가운데 회색 알약 11.5 w700 t2 rgba(.06) r999. ChatDateDivider + withDateDividers(경계 삽입). 라벨 오늘/어제/M월 D일(요일). 빌드 OK |
| 통화 구간 카드(📞 청록) | ✅ | callCardHtml | 수신/발신/부재중·N분. buildChatTimeline 병합 |
| AI 추천 "✨ 이렇게 답해보세요" | 🟡 | renderSugs | sug-area p8/14/4, head 12 w800 blue, sug-chip 흰 238 r15 p12x13(cl 11 w800 blue + ct 13 t1 1.45). 9b48e01 |
| 하단 액션칩 3개 | 🟡 | chat-actions(act-chip) | act-chip 흰 r999 p8x13, 12.5 w700 t2, icon blue14, gap8. [견적작성→send_estimate][내일정→openMySchedule][문구넣기→openTplPicker]. ⚠️ ⚡토글 대체(사장님 지시) |
| 입력창(composer) | 🟡 | composer/field/snd | field bg r22 p7x15: ✨19 왼 + textarea14 "메시지 입력..." + 📷19 오른, snd 40px 파란원 send18. ⚠️ 터치영역 프로토 크기로 축소—작으면 키우기 |

## 📅 일정
> 프로토: `switchTab('schedule')` · `buildCalendar` · `renderAddSchedule` · `openMySchedule`

| 요소 | 상태 | 프로토 출처 | 메모 |
|---|---|---|---|
| 앱바(일정·날짜·[+]) | ✅ | switchTab schedule | 우상단 [+] Box(FAB 제거). 03a585d |
| 달력 헤더(가운데+원형nav) | ✅ | cal-head/CalNav | CalNav 34dp |
| 달력 카드(흰 7×6) | ✅ | buildCalendar | cal-card r20 p10/10/16 + 기간선 |
| 안내문(📌 길게 눌러 등록) | ✅ | cal-hint | long-press→openAddSchedule |
| 날짜 시공 카드 | ✅ | selectDay | dot+name+N일차+time+D-day+edit+📍addr+pay+정산링크 |
| 팀원 배정 라인(99k) | ⬜ | openAssign | 의도적 보류 |
| 셀프 등록 폼 | ✅ | renderAddSchedule | 필드·시간칩·기간칩 |
| 채팅 "내 일정" 시트 | ✅ | openMySchedule | 미니 달력 읽기전용 |

## 💰 정산
> 프로토: `renderSettle` · `renderCashCal`

| 요소 | 상태 | 메모 |
|---|---|---|
| 월 매출 다크 카드(settle-top) | ✅ | grad #272D3D→#14171F, 월nav+받은돈+만원+전월대비+목표바+pace. 9fa0c68 |
| 현금흐름 달력(인라인) | ✅ | CashFlowCard 4색+회색+범례 |
| "받을 돈·미수 관리" + 필터칩 | ✅ | sec-sub + 전체/미수금/완료 |
| 미수 줄(srow)+완납 | ✅ | PayBlock 계약금/잔금 + "완납 N건" + done row |
| 목표 수정 다이얼로그 | ✅ | GoalEditDialog |

## 📊 통계
> 프로토: `renderStats` · `renderStatsGreeting` · `renderStatTypes`

| 요소 | 상태 | 메모 |
|---|---|---|
| 히어로(인사+현장N곳+작년대비+전국배지) | ✅ | 전국=가짜숫자 대신 "모이는 중". e490c5b |
| 막내 한 줄 | ✅ | "부쩍 자랐어요" |
| 2×2 그리드 | ✅ | 현장·문의·전환율·답장 실데이터 |
| 문의 추이(토글+막대+시장비교) | ✅ | D7/D30, GBar 92dp, 시장 "모이는 중" |
| 이번 달 시공 종류 | ✅ | wt-hero + wt-row |

## ⚙️ 더보기
> 프로토: `openAgent`

| 요소 | 상태 | 메모 |
|---|---|---|
| 막내 비서 카드 | ✅ | grad #EAF2FF→#F1ECFF r22, Lv칩 #3182F6→#7C5CFC, 말투% bar, "상담 N·시공 N·다음 N건". aab8a73 |
| 메뉴 섹션 재배치 (완전 깔끔하게) | 🟡 | 사장님 결정="완전 깔끔하게". 더보기=메뉴만(함께 일하는 사람/장사 분석/내 답장 재료/앱 설정/도움말 + tier 배지). 진단·기능 카드(말투·자동문자·네비·문자앱·알림·서버/토큰)는 메뉴 탭→내부 서브페이지(BackHandler). 팀관리=비즈니스 잠금 토스트, "비즈니스 리포트"→"상세 리포트". lockcard 42·r13·tier-tag 1:1. 빌드 OK, 폰 확인 |
| 하위 화면들(템플릿·가격표·리포트·수첩·사업자·업종) | 🟡 | 기능 완료, 화면별 1:1 점검 후속 |

## 🧾 견적 · 시공접수서
| 요소 | 상태 | 메모 |
|---|---|---|
| 견적 작성 다이얼로그 | 🟡 | EstimateDialog 동작, sheet 1:1 점검 후속 |
| 시공접수서(openQuote) | ⬜ | 서버 §19 완료, 앱 [링크 보내기]+polling+카드 미구현 |
| 사업자정보 등록 | ✅ | BusinessInfoScreen |

## 🎒 온보딩 · 로그인
| 요소 | 상태 | 메모 |
|---|---|---|
| 로그인 | ✅ | .login 1:1 |
| 온보딩 7장 | ✅ | OB_SLIDES verbatim. b6908da |

## 👥 팀(99k) · 알림
| 요소 | 상태 | 메모 |
|---|---|---|
| 팀 관리/팀원 보기 | ⬜ | 서버 §20 완료, 앱 미구현(99k) |
| 알림 센터(openNotif) | ⬜ | 앱 1:1 점검 전 |

---

## 다음 액션 (우선순위)
1. **채팅 폰 확인** — 9b48e01(액션칩·추천·입력창) + 요약 바(접힘 한 줄 / 탭 펼침) 사장님 검증.
2. ✅ ~~히어로 빈 날(heroEmptyHtml) 1:1~~ — 완료(빌드 OK).
3. ✅ ~~채팅 요약 바~~ — 사장님 "둘 다" 결정 반영(접힘=프로토 바, 탭=풍부 카드). 빌드 OK.
4. ✅ ~~신규 재연락 전용화면(openNewLeads)~~ — NewLeadsScreen 신설(빌드 OK). 빌드 OK, 폰 확인.
5. ✅ ~~더보기 메뉴 섹션 재배치~~ — "완전 깔끔하게"(메뉴만+서브페이지+tier) 반영. 빌드 OK, 폰 확인.
6. ✅ ~~상단 앱바 AI 배지~~ — glow 추가 1:1.
7. 견적 작성 다이얼로그 / 사업자정보 / 채팅 말풍선 등 잔여 화면 1:1 점검.
8. 하위 화면들(템플릿·가격표·리포트·수첩) 개별 1:1 디자인 점검.
