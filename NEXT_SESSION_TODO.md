# 다음 세션 작업 명세서

> 이 파일은 다음 세션 시작 시 사장님 + 클로드가 함께 보는 가이드입니다.
> 핵심 작업 1개 + 보조 작업 2개. 위에서부터 순서대로 진행.

---

## 🎯 핵심 작업: 카드 탭 인라인 액션 4개 (에이닷 벤치마킹)

### 무엇
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

## 🔧 보조 작업 2: AI 제안 버튼 액션 hookup

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
