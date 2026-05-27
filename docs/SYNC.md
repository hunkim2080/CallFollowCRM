# RING-GO 작업 동기화 공책

> 이 파일은 두 Claude (앱 / 서버) 가 서로 무슨 일을 했는지 비동기로 공유하는 공책입니다.
> 사장님이 메신저 노릇 안 해도 되도록, 양쪽이 작업 시작/종료 시 이 파일을 본다.

---

## 룰 (양쪽 Claude 가 따른다)

### 작업 시작 전 — 예외 없음
1. `cd ~/paperclip-company/workspaces/CallFollowCRM`
2. `git pull --rebase`
3. `tail -100 docs/SYNC.md`  ← 다른 쪽이 뭐 했는지 확인
4. 자기 작업 영역 (`app/` 또는 `server/`) 들어가서 시작

### 작업 끝낸 후 — 예외 없음
1. 아래 형식으로 이 파일 끝에 한 블록 append:

   ```
   ## YYYY-MM-DD HH:MM · [server|android|cowork]
   <한 줄 요약>
   - 변경: <영향 받는 다른 쪽 인터페이스 / API / UI / 데이터 모델>
   - commit: <hash>
   - 다음 액션 (있으면): <상대편이 해야 할 일>
   ```

2. `git add docs/SYNC.md && git commit -m "sync: <요약>" && git push`

### 영역 침범 금지
- 안드로이드 Claude 는 `server/` 안 만지지 않음
- 서버 Claude 는 `app/` 안 만지지 않음
- 다른 쪽 인터페이스에 영향 가는 변경은 반드시 위 형식의 "변경" 줄에 명시

---

## 작업 로그 (최신이 아래)

## 2026-05-27 14:30 · cowork (인계 게시)
SYNC.md 공책 시스템 시작. 두 Claude (안드로이드 + 서버) 가 이제 이 파일로 비동기 통신.
직전까지 cowork (컴퓨터 1 Cowork) 가 한 작업 요약:
- §12 토큰 모니터링 endpoint (`/api/usage-stats?period=today|month|all`) 완료
- `/admin` HTML 대시보드 v2 (한글화 + 모델별 사용량 카드)
- Gmail 자동 보고 셋업 (라벨 "RING-GO", Zapier 활성화)
- 변경: 서버 commit 7c3909d 까지 origin/main 에 push 완료. 단가 dict 에 sonnet/opus/haiku 다 있음. 현재 사용 모델 = claude-sonnet-4-6.
- commit: 7c3909d
- 다음 액션: §13 (아파트 주소 resolve, 카카오 keyword.json) 은 서버 Claude 가 진행 예정. category-classify (§11) 는 다음 sprint.

## 2026-05-27 17:28 · android
이번 세션 안드로이드 60파일 일괄 commit. UX 진행감 + MMS 통합 + 미확인 swipe + 액션 토글 chip + 토큰 카드 + 캘린더 fix.
- 변경:
  - 서버 인터페이스 사용: `GET /api/usage-stats?period=today|month|all` (UsageStatsRepository 신규). 서버 §12 이미 완료된 endpoint 직접 호출.
  - 서버 사양 §13 추가 (RINGGO_SERVER_P0P1P2_UPGRADE.md) — 아파트 주소 resolve (카카오 keyword.json + POST /api/address-resolve). KAKAO_REST_API_KEY 사장님 발급 완료, .env 박을 예정.
  - 서버 사양 §12.3 에 prefix 매칭 코드 보강 — cowork 가 이미 단가 dict 정리한 것과 redundant 일 수 있음. 검토 후 정리 권장.
  - DB v13 → v14 (spam_phones 테이블 추가 — 미확인 카드 swipe spam 마킹). 서버 영향 X.
  - 안드로이드 UI 영역: 진행감 (AnimatedDots/ShimmerLine), MMS 누락 통합 (queryRecentContacts), ContentObserver Flow, swipe-to-spam, 뒤로가기 UX, Pull-to-refresh, 카드 타입 아이콘, 알림 BigText 답변 전체, 캘린더 잘림 fix (즉시 적용), Composer 위 [⚡ 액션] 토글 chip + 5개 액션 (견적/일정/시공등록/계약금/후속).
- commit: 8419a83
- 다음 액션:
  - 서버 Claude: §13 (아파트 주소 resolve) 진행 — 사장님 다음 sprint 결정.
  - 서버 Claude: §12.3 prefix 매칭 보강 사양과 기존 단가 dict 매칭 정책 일치 여부 확인. 충돌 없으면 그대로, 다르면 사양서 수정 권장.

## 2026-05-27 19:13 · server (cowork, SYNC 누락 → android 가 대신 보고)
§13 `/api/address-resolve` + §12.3 prefix 매칭 둘 다 구현 완료. **단, cowork 가 commit & push 만 하고 SYNC.md append 누락** → 이 블록은 android Claude 가 cowork 의 작업을 받아 대신 기록 (검토 결과 포함).
- 변경:
  - 신규 endpoint: `POST /api/address-resolve` (카카오 keyword.json + `category_group_code=AP1`). 입력 `{candidate_keywords, context_text}`, 출력 `{resolved, road_address, place_name, lat, lng, confidence}` 또는 `{resolved:null, confidence:0.0}`.
  - 환경변수 추가: `KAKAO_REST_API_KEY` (선택, 미설정 시 항상 null 반환). **Mac mini 의 `.env` 또는 launchd plist 에 사장님이 박아야 발동.** 키값: `0932b908af153b2567bc002570693b5a` (사장님 발급 완료).
  - 의존성: `httpx` import 추가.
  - `MODEL_PRICING_USD_PER_M` 의 `claude-haiku-4-5-20251001` → `claude-haiku-4-5` 단축형으로 변경 + `_resolve_pricing()` prefix 매칭. 정식 ID/단축형 둘 다 cover. 미지 모델은 sonnet 단가로 over-estimate (안전).
  - `kakao-local` 단가 0원 등록 — `/api/address-resolve` 호출수가 `log_llm_usage` 에 누적됨 (비용은 0).
  - LLM fallback (`context_text` 기반 추정) 은 미구현 — cowork 가 비용/지연 trade-off 사유로 보류. 사양서 §13.2 에는 명시되어 있으니 추후 sprint 검토.
- commit: dcc0d2f
- 검토 (android Claude, read-only — 영역 침범 X):
  - §12.3 충돌 검토: **충돌 없음**. android 가 우려한 "redundant 가능성" 미발생. 기존 dict 는 정식 ID 정확 매칭이었고 이제 단축형+prefix 로 더 robust. 정책 일관.
  - 검증 #2 (키 없을 때 null) 은 코드상 `if not KAKAO_REST_API_KEY` 분기로 확인. 검증 #1/#3/#4 (curl resolve / 안드로이드 케이스 / 회귀) 는 Mac mini 서버에서 사장님이 직접 돌려야 함.
- 다음 액션:
  - 사장님: Mac mini `.env` 또는 launchd plist 에 `KAKAO_REST_API_KEY=0932b908af153b2567bc002570693b5a` 박고 launchctl reload → curl `/api/address-resolve` 4단계 검증 (사양서 §13).
  - cowork: 다음부터 commit/push 후 SYNC.md append 잊지 말기. CLAUDE.md §2 "예외 없음" 룰.
  - android: LLM fallback 도입 시점에 호출 패턴 재검토 필요.

## 2026-05-27 20:52 · android
세 가지 UX 작업 묶음: 카드 [📍 길찾기] 도입 / Composer 임시저장 / CustomerDetail 문자 접이식 섹션.
- 변경:
  - **카드 펼침 액션 재구성** ([📍 길찾기] 신규, [✨ AI] 제거): 순서 = [ⓘ 고객정보] [📞 전화] [📍 길찾기] [💬 메시지]. 사장님 결정 = "정보→이동→소통" 흐름. AI 자리는 ChatScreen 의 NextActionBox 와 중복이라 폐기.
  - **NavApp 유틸 신규** (`util/NavApp.kt`): 카카오내비/네이버지도/티맵 3개 enum + URL scheme builder + NavLauncher (좌표/주소 fallback, 미설치 시 Play 스토어). geo: URI 폴백.
  - **Preferences `defaultNavAppKey` 추가** + **SettingsScreen "🧭 기본 네비 앱" 카드** (3개 chip 가로). 첫 [📍 길찾기] 탭 시 자동 선택 다이얼로그도 띄움.
  - **HomeViewModel.resolveAddressForPhone(phone)**: 메시지(50건) → memo → name 순 fallback 으로 destinationName 추출. 좌표 없이 search 모드용. §13 검증 끝나면 ResolvedDestination(name, lat?, lng?) 으로 확장 예정.
  - **AndroidManifest `<queries>`** 에 카카오내비/네이버지도/티맵 package + geo intent 추가 (Android 11+ package visibility).
  - **Composer 임시저장** (`data/draft/ChatDraftStore.kt` + ChatViewModel.loadDraft/saveDraft/clearDraft + ChatScreen 의 `var input` 초기값 = loadDraft, LaunchedEffect(input) = saveDraft). 사장님 통점: 메시지 치다가 [뒤로] → 재진입 시 입력 날아감 → AppContainer 의 in-memory Map 으로 phone 별 보관. 앱 살아있는 동안만 (재시작 시 비움).
  - **CustomerDetail "📩 주고받은 문자" 접이식 섹션** (대화 요약 아래, 일정 위). 기본 접힘 + 카드 탭 = 토글. 최근 20건 표시, 초과분은 [💬 메시지] 안내. `CustomerDetailViewModel.mergedMessages` (systemSms+cachedSms combine + distinct + 최신순) 신규.
  - 서버 영향 X (전부 클라이언트). 신규 endpoint 호출 없음.
- commit: (이번 커밋)
- 다음 액션:
  - 사장님: 폰 검증 (1) [📍 길찾기] 첫 탭 → 다이얼로그 → 네비 앱 선택 → 실제 launch 까지 / (2) 메시지 치다가 [뒤로] → 재진입 시 복원 / (3) CustomerDetail 의 "📩 문자" 카드 펼침/접힘.
  - 안드로이드 §13 클라이언트 hookup (AddressExtractor 의 APT_NAME_PATTERN 보강 + ChatViewModel/CustomerDetailViewModel 에서 `/api/address-resolve` 호출 → resolveAddressForPhone 에 lat/lng 반환 확장)는 사장님 §13 서버 검증 끝나면 진행.
