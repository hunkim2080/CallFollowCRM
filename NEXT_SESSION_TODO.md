# 다음 세션 작업 명세서

> 이 파일은 다음 세션 시작 시 사장님 + 클로드가 함께 보는 가이드입니다.
> 핵심 작업 1개 + 보조 작업 2개. 위에서부터 순서대로 진행.

---

## 🎯 핵심 작업: 카드 탭 인라인 액션 4개 (에이닷 벤치마킹) ✅ 2026-05-24 완료

> 빌드 통과 (`./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL). 다음 세션 = 폰에서 동작 확인 + (시간 남으면) 보조 작업 진입.
>
> 변경 파일:
> - [HomeScreen.kt](app/src/main/java/com/detailline/callfollowcrm/presentation/screen/home/HomeScreen.kt) — `onOpenCustomerDetail` 파라미터 추가, `expandedKey` rememberSaveable, HomeRow 의 onClick → 토글, AnimatedVisibility 펼침 영역, InlineActionButton 컴포넌트 신규.
> - [AppNavHost.kt](app/src/main/java/com/detailline/callfollowcrm/presentation/navigation/AppNavHost.kt) — HomeScreen 호출부에 `onOpenCustomerDetail` 콜백 연결.
>
> 동작:
> - 카드 탭 = 펼침/접힘 토글. 한 번에 하나만 펼침 (다른 카드 탭하면 이전 거 접힘).
> - 펼침 영역 = 회색 구분선 + 액션 4개 가로 균등 배치 (아이콘 + 라벨).
> - [💬 메시지] = 기존 ChatScreen 진입
> - [📞 전화] = `Intent.ACTION_DIAL` (다이얼러만 띄움 — 권한 없이 안전)
> - [✨ AI] = ChatScreen 진입 (P3 = 진입 시 AI 박스로 스크롤 / 답변 추천 칩 강조)
> - [ⓘ 고객 카드] = CustomerDetail. customer 없는 SMS-only 카드면 disabled (회색 + 클릭 무시).

### 원본 사양 (참고용 — 위에서 완료)
HomeScreen 카드를 탭하면 **지금처럼 ChatScreen 으로 바로 가는 대신**, 같은 자리에 액션 4개가 펼쳐짐. 사장님이 원하는 액션을 골라서 누름.

### 에이닷 참조
사장님이 보내준 두번째 캡처 — 카드 아래 [기록 / 통화요약 / 연락처 / 메시지] 4개 가로 배치.

### RING-GO 액션 4개 (제안)
| 액션 | 아이콘 | 동작 |
|------|--------|------|
| **💬 메시지** | 🗨️ | 기존 ChatScreen 진입 (지금 카드 탭과 동일) |
| **📞 전화** | 📞 | 시스템 다이얼러 (intent ACTION_DIAL) |
| **✨ 다음 액션** | ✨ | AI 제안 박스로 스크롤 (있으면) 또는 답변 추천 칩 |
| **ⓘ 고객 카드** | ⓘ | CustomerDetail 진입 |

> 또는 사장님이 직접 4개 정하셔도 됨. 시공자 워크플로우에 가장 자주 쓰는 것 위주.

### 구현 위치
- `app/src/main/java/com/detailline/callfollowcrm/presentation/screen/home/HomeScreen.kt`
  - HomeRow composable 의 onClick 동작 변경
  - 카드 아래 펼침 영역 추가 (애니메이션은 AnimatedVisibility)
- HomeScreen / HomeViewModel 에 expandedCardKey 상태 추가 (한 번에 하나만 펼침)

### UX 디테일
- 같은 카드 다시 탭 = 접힘
- 다른 카드 탭 = 이전 카드 접히고 새 카드 펼침
- 펼친 상태에서 액션 1개 탭 = 펼친 카드 그대로 두고 해당 액션 실행 (또는 자동 접힘)
- 펼친 상태에서 스크롤 = 펼친 상태 유지

### 작업량
중간. 한 세션 안에 끝남 (UI + 상태 관리 + 애니메이션).

---

## 🔧 보조 작업 1: 맥미니 서버 응답 검증 (사장님이 끝냈으면)

### 무엇
사장님이 맥미니에서 `RINGGO_SERVER_P0P1P2_UPGRADE.md` 작업 끝냈으면, 실제 서버 응답 받아서 안드로이드 UI 가 제대로 채워지는지 확인.

### 체크 포인트
1. HomeScreen 카드 — "✨ <한 줄 요약>" 표시되는지 (파란색)
2. ChatScreen 진입 — "✨ 대화 요약" 박스 + "✨ AI 제안" 박스 표시되는지
3. 응답 품질 — 요약이 사장님이 보기에 정확한지

### 품질 안 좋으면
서버의 system prompt 튜닝 (사장님이 맥미니에서):
- card-summary: 너무 길거나 짧으면 "정확히 15-25자" 강조
- conversation-summary: 추측 정보 들어가면 "명시되지 않은 것 금지" 강조
- next-action-suggest: 잘못된 액션 제안하면 매칭 시나리오 더 명확하게

---

## 🔧 보조 작업 2: AI 제안 버튼 액션 hookup ✅ 2026-05-24 완료

> 빌드 통과. P2 완성 → 다음 = P3 (시공 흐름 자동화) 진입 가능.
>
> 변경 파일:
> - [ChatViewModel.kt](app/src/main/java/com/detailline/callfollowcrm/presentation/screen/chat/ChatViewModel.kt) — `setScheduledWorkDate(timestampMs)` 함수 추가 (CustomerRepository.updateScheduledWorkDate + ensureCustomerId 자동 보장)
> - [ChatScreen.kt](app/src/main/java/com/detailline/callfollowcrm/presentation/screen/chat/ChatScreen.kt) — `NextActionBox` 의 onClick = action_type 별 분기, `TemplatePickerDialog` 컴포넌트 신규, Material3 `DatePickerDialog` 추가
>
> 분기:
> - `send_estimate` → ESTIMATE 카테고리 템플릿 다이얼로그
> - `request_deposit` → RESERVATION 카테고리 (계약금 = 예약 흐름)
> - `send_followup` → 전체 템플릿 (전용 카테고리 없음 → 사장님 직접 선택)
> - `confirm_schedule` → `viewModel.regenerateSuggestions()` (답변 추천 일정 키워드)
> - `register_schedule` → Material3 DatePicker → `viewModel.setScheduledWorkDate(timestamp)`
> - 카테고리 매칭 결과 비어있으면 전체로 fallback (UX 안전망)
>
> ## 🐛 함께 fix — Sent MMS 누락 (2026-05-24 사장님 보고)
> [SmsRepository.kt](app/src/main/java/com/detailline/callfollowcrm/data/repository/SmsRepository.kt) `queryMmsByPhone` — OneUI(갤S9) 이 사장님 발신 MMS 의 addr 테이블에 `"insert-address-token"` placeholder 만 저장하는 동작 → address 매칭 통째로 실패 → sent MMS 누락. **thread_id 기반 3-pass fallback** 추가 (SMS 시드 + inbox MMS 매칭 thread_id → address 매칭 실패한 sent MMS 복구). 사장님 확인 = "이제 잘 보임".

### 원본 사양 (참고용 — 위에서 완료)

### 무엇
지금 ChatScreen 의 AI 제안 박스에서 [버튼] 눌러도 아무 일 안 일어남. placeholder 상태. 진짜 동작 연결.

### action_type 별 동작
| action_type | 동작 |
|-------------|------|
| `send_estimate` | 템플릿 목록 띄움 (견적 카테고리 우선) |
| `confirm_schedule` | 답변 추천 칩 자동 ↻ (일정 키워드) |
| `request_deposit` | 템플릿 목록 (계약금 안내) |
| `register_schedule` | DatePicker 다이얼로그 띄워서 시공일 등록 |
| `send_followup` | 템플릿 목록 (후기 요청) |

### 구현 위치
- `app/src/main/java/com/detailline/callfollowcrm/presentation/screen/chat/ChatScreen.kt`
  - `NextActionBox` 의 `Surface(onClick = { /* P3 — primary_action 별 분기 */ })`
  - 거기에 `when (action.primaryAction)` 분기 추가

### 작업량
작음. 30분~1시간.

---

## ✅ 다음 세션 시작 시 체크리스트

세션 시작 전 사장님이 확인:

- [ ] 윈도우 → git push 됐나? (이번 세션 모든 변경 — SMS fix, UI 박기, 디버그 제거, 서버 사양서)
- [ ] 폰에 새 빌드 깔렸나? (HomeScreen 의 ✨ 요약 안 보여도 OK — 서버 응답 없어서 그런 거)
- [ ] 맥미니 서버 작업 어디까지? (안 됐어도 진행 가능 — UI 는 이미 박혀있음)

세션 시작 후 클로드가 확인:

- [ ] [session_next_kickoff.md](file:///C:/Users/admin/.claude/projects/d--dev-CallFollowCRM/memory/session_next_kickoff.md) 메모리 read
- [ ] 이 파일 (`NEXT_SESSION_TODO.md`) read
- [ ] 사장님께 "핵심 작업 (카드 탭 인라인 액션) 부터 갈까요?" 확인

---

## 📦 이번 세션 (2026-05-24 이어진 작업) 추가 완료

9. **카드 탭 인라인 액션 4개** — HomeScreen 카드 탭 = [메시지/전화/AI/고객카드] 펼침
10. **AI 제안 버튼 hookup** — NextActionBox 의 action_type 별 분기 (템플릿/DatePicker/regenerate)
11. **▶ 보내기 확인 다이얼로그** — composer ▶ = 즉시 발송 X, 미리보기 후 한 번 더 탭
12. **Sent MMS 누락 fix** — OneUI 의 `insert-address-token` 문제 → thread_id 시드 기반 3-pass 매칭
13. **HomeScreen UX 개편** — KPI 를 LazyColumn 첫 item 으로 (스크롤 시 사라짐), AI 문자함 + 내 말투 학습 칩 숨김
14. **ScheduleScreen 캘린더 그리드** — 월별 7×6 그리드, 시공 있는 날 점 표시, 셀 탭 = 시공 카드, ◀▶ + 가로 swipe 월 이동
15. **CustomerDetail 대화 요약 박스** — ChatScreen 과 같은 ✨ 대화 요약 데이터, 일정 카드 직전
16. **ScheduleCustomerCard 에 ✨ cardSummary** — 캘린더 셀 탭 시공 카드에 "어떤 내용인지" 한 줄
17. **P3 일정 후보 추천 데이터 흐름** — CustomerRepository.getOtherUpcomingScheduleDates / PrepareContext+SummaryContext+CustomerHint 의 새 필드 / ServerSuggestionRepository+ConversationAiRepository JSON 직렬화 / SmsReceiver+ChatViewModel.regenerateSuggestions+loadFullSummary 호출 / 서버 사양서 2개 update

### 다음 세션 시작점

**사장님 맥미니 서버 작업 필요** — 서버 사양서 (RINGGO_SERVER_PHASE1_UPGRADE.md §1.4-1.5, RINGGO_SERVER_P0P1P2_UPGRADE.md §1, §4) 의 새 필드 (`scheduledWorkDateMs` / `otherUpcomingSchedulesMs` / `other_upcoming_schedules_ms`) Pydantic 모델에 추가 + system prompt 에 SCHEDULE_CONTEXT inject 로직 추가.

사장님이 맥미니 Claude Code 에 줄 프롬프트:
> "RINGGO_SERVER_PHASE1_UPGRADE.md §1.4-1.5 와 RINGGO_SERVER_P0P1P2_UPGRADE.md §1·§4 의 P3 (2026-05-24 추가) 변경사항을 server/main.py 에 반영해줘. Pydantic 모델에 새 필드 + prompt template 에 SCHEDULE_CONTEXT inject."

서버 끝나면 검증:
- 캘린더에 사장님 시공 예약 2-3건 등록
- 그 중 한 고객이 "이번 주 토요일 가능?" 같은 SMS 보내는 척
- 답변 추천이 일정 근거로 정확히 답하는지

---

## 📦 이번 세션에서 완료한 것 (요약)

자세한 건 [session_next_kickoff.md](file:///C:/Users/admin/.claude/projects/d--dev-CallFollowCRM/memory/session_next_kickoff.md) 의 "2026-05-24 마지막 세션" 섹션 참조.

1. **SMS 매칭 fix** — SQL LIKE selection 으로 17000건 폰에서도 옛 메시지 잡힘
2. **년도 표시** — 다른 해 메시지면 "2025.10.3 09:25" 형식
3. **HomeScreen SMS 통합** — 갤럭시 메시지의 모든 사람이 카드로 자동
4. **HomeScreen pagination** — 20개씩 + infinite scroll
5. **백그라운드 prefetcher** — 가시 카드 SMS 캐시 미리 채움
6. **P0+P1+P2 UI 박기** — 카드 ✨ 요약 / ChatScreen 상단 요약 박스 / AI 제안 박스 (데이터는 server 응답 기다림)
7. **디버그 박스 제거**
8. **서버 사양서 작성** — `RINGGO_SERVER_P0P1P2_UPGRADE.md` (사장님 맥미니로)

---

## 🚫 다음 세션에 절대 하면 안 되는 것

- 카드 탭 = ChatScreen 직진 되돌리기 X (이게 핵심 작업)
- AI 제안 박스 / 카드 ✨ 요약 제거 X (server 미구현이라 안 보여도 정상)
- DB v10 → v9 회귀 X (ai_summary_cache 테이블 보존)
- SMS scanLimit 줄이지 마세요 (사장님 폰 17000건 환경 검증됨)
- 디버그 박스 다시 박지 마세요 (사장님 거부)
