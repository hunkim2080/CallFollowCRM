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

## 2026-05-28 00:30 · android
사장님 즉시 보고 3건 묶음 fix: DatePicker 잘림 / ✨ 다듬기 작동 안 함 / AI 추천 답변 재진입 시 사라짐.
- 변경:
  - **ChatScreen DatePicker 잘림 재fix** (showProposalDatePicker): Material3 DatePickerDialog wrapper 가 contents+버튼 합산 사이즈를 통제 못 해 작은 폰에서 닫기 버튼 화면 밖. → 직접 `Dialog + Surface` 로 wrap, 닫기 버튼을 fixed bottom 으로 빼고 캘린더 영역만 `weight(1f, fill=false).verticalScroll` + `heightIn(max = screenHeight * 0.88f)` 강제.
  - **✨ AI 다듬기 = Ollama → Claude 교체** (사장님 결정 [memory project_ai_polish_hookup] 의 timing).
    - 신규 `ClaudeRefineRepository` (FastAPI `POST /api/refine` 호출)
    - `AppContainer.refineRepository` = ClaudeRefineRepository (OllamaRefineRepository 는 코드 유지, rollback 용)
    - 사장님 보고 원인 진단: 폰에서 작동 X. 데스크탑 Tailscale 미연결이라 직접 ping 불가. 가장 가능성 큰 원인 = Mac mini Ollama 서버 죽음 (5/27 cowork 의 launchctl 재기동 영향 가능). 근본 fix = Claude 교체로 양쪽 (불안정 + 품질) 동시 해결.
  - **AI 추천 답변 chips 재진입 시 보존** (composer draft 와 같은 원칙): 사장님 통점 = 뒤로갔다 재진입 시 chips 가 잠시 사라졌다 다시 채워짐. → 신규 `SuggestionsCacheStore` (AppContainer in-memory Map<phone, ReplySuggestions>) + ChatViewModel.`_suggestions` 초기값 = cache get(phoneNumber) + cachePersistJob 으로 변경 시 자동 cache put. **낡은 chips 위험은 기존 `effectiveSuggestions` 의 stale 차단 (basedOnReceivedAtMs < latest.dateMs) 이 처리** — 새 메시지 들어오면 캐시된 chips 자동 hidden + 백그라운드 fetch 가 새 chips 받아 교체.
- commit: (이번 커밋)
- **🚨 cowork 작업 요청 (서버 영역) — `POST /api/refine` endpoint 신규**:
  - 사양:
    ```
    POST /api/refine
    Request:  { "raw": "다듬을 원문", "owner_tone_samples": [], "system": null }
    Response (200): { "polished": "다듬어진 문장" }
    Response (5xx 또는 빈 응답): 클라이언트가 Result.failure → "AI 서버 연결 실패" 토스트
    ```
  - System prompt: OllamaRefineRepository.DEFAULT_SYSTEM 참고 (줄눈 시공 사장님 톤, 원문 의미 유지, 정중한 한국어, 가격/날짜 추가 금지, 길이 비슷).
  - 모델: claude-sonnet-4-6 (기존 사용 모델 일관성).
  - 캐시: 같은 raw → 같은 결과 short-cache 가능 (옵션). prompt caching ON 권장.
  - 호출 카운트: 기존 `log_llm_usage(endpoint="refine", ...)` 패턴 따르기.
  - **endpoint 미구현 시 현재 안드로이드 동작**: `Result.failure` → 토스트 "AI 서버 연결 실패 — Tailscale 확인하세요". 사장님이 ✨ 누르면 매번 실패 토스트. cowork 작업 우선순위 높음.
- 다음 액션:
  - cowork: `POST /api/refine` endpoint 신규 + commit/push 후 SYNC append.
  - 사장님: 폰 검증 (1) ChatScreen 에서 [✨ 다듬기] 동작 / (2) [고객한테 제안할 날짜] 다이얼로그 닫기 버튼 보이는지 / (3) chip 보다가 [뒤로] → 재진입 시 chip 즉시 복원.

## 2026-05-28 12:30 · android
사장님 직접 검증 통점 6건 묶음 fix.
- 변경:
  - **현장 주소 수동 등록** (DB v15) — CustomerEntity.address 컬럼 + CustomerDetail 카드 탭 = AddressEditDialog. 자동 추출 칩 prefill, 수동 우선. 길찾기/§13 가 1순위로 활용. AddressEditDialog text 는 rememberSaveable (뒤로/회전 살아남음).
  - **resolveAddressForPhone 의 name fallback 제거** — "김철수" 같은 인명이 네비 검색에 들어가 엉뚱한 곳 표시되는 위험 차단. 토스트로 등록 동선 안내.
  - **미확인 KPI 정의 변경** — `!hasOwnerReply` 조건 제거. lastSent=false (마지막이 고객 메시지) + 7일 이내 = 미확인. **답장한 적 있는 phone 도 그 후 새 메시지가 오면 다시 미확인** (사장님 의도). 부재중 부분은 그대로.
  - **입금 카드 UI 재설계** — 4상태 (EMPTY/PROMISED/RECEIVED/SKIPPED) 시각 분리 + 인플레이스 펼침 (다이얼로그 X). EMPTY=큰 [💸 받았어요] 버튼 1개 + 작은 [건너뛰기]. RECEIVED=✅ 큰 금액 + 날짜 + 작은 [수정/지움]. SKIPPED=🚫 회색 + [되돌리기]. SKIPPED 표현 = amount=0 && paidAt!=null 컨벤션 (DB 컬럼 X).
  - **CustomerDetail 옛 "문자" 카드 제거** — "📩 주고받은 문자" 접이식이 대화요약 아래로 올라오면서 중복.
  - **ChatScreen composer padding 보강** — vertical 2dp → 8dp, end 2dp → 6dp, start 12dp → 14dp. 토스/카톡 톤.
  - **DatePicker proposal 재선택 시 이전 proposal 자동 교체** — 정규식으로 `M월 d일 (E) 시공 가능하실까요...` 패턴 모두 제거 후 새 proposal append. 사장님이 친 인사말 등 다른 텍스트는 보존.
  - **[버그 fix] SpamSwipeBox 광고 처리 → 되돌리기 시 화면 멈춤** — confirmValueChange { true } → dismiss 확정 → SwipeBox state stuck. 같은 LazyColumn key 라 composable 재사용 → 빈 칸 + "광고로 처리" 잔상. fix: false 반환. spam 처리는 markSpam 이 unconfirmedSuffixes 에서 제거 → 카드 자연 사라짐. undo = 새 SwipeBox = 정상.
  - **✨ AI 다듬기 = Gemini 2.5 Flash + 컨텍스트 전송으로 교체** (사장님 결정):
    - 클라이언트: `ClaudeRefineRepository` → `RemoteRefineRepository` rename. `RefineRepository.refine(input, context)` 시그니처 확장 (RefineContext = recent_messages + owner_tone_samples + customer_name + customer_memo). refineStream dead code 제거.
    - ChatViewModel.aiPolish 가 최근 메시지 20건 + 사장님 톤 50건 + customer hint 함께 전송.
    - 서버 endpoint 사양 (cowork 가 박을 것) — 아래 🚨 cowork 요청 참고.
- commit: (이번 커밋들)
- **🚨 cowork 작업 요청 update** — `POST /api/refine` endpoint:
  - **변경된 사양**:
    ```
    POST /api/refine
    Request:
    {
      "raw": "사장님이 친 문장",
      "recent_messages": [ {"role":"owner|customer","body":"...","timestamp_ms":0}, ... ],
      "owner_tone_samples": ["...", ...],
      "customer_name": "김철수" or null,
      "customer_memo": "..." or null
    }
    Response 200: { "polished": "..." }
    ```
  - **LLM = Gemini 2.5 Flash** (Claude 가 아님 — 사장님 결정 2026-05-28).
    - 이유: 다듬기는 단순 작업 → Flash 가 가성비 굿. 한국어 자연스러움도 양호.
    - API 키: Mac mini .env 또는 launchd plist 에 `GEMINI_API_KEY` 추가 필요 (사장님이 발급).
  - **System prompt**: 사장님 톤 + 흐름 맞춤. owner_tone_samples 를 few-shot 으로 inject. recent_messages 마지막 고객 메시지가 있으면 그 흐름에 맞는 답변으로 다듬기.
  - `log_llm_usage(endpoint="refine", model="gemini-2.5-flash", ...)` 로 카운트. Gemini 단가도 `MODEL_PRICING_USD_PER_M` 에 추가 필요.
  - endpoint 미구현 시 현재 안드로이드 동작: Result.failure → 토스트 "AI 서버 연결 실패 — Tailscale 확인하세요".
- 다음 액션:
  - cowork: `POST /api/refine` (Gemini 호출 버전) endpoint 신규.
  - 사장님: (1) Gemini API 키 발급 후 Mac mini 에 박기. (2) 폰 검증 = 미확인 KPI 동작, swipe-undo, 입금 카드 4상태, 현장 주소 등록.

## 2026-05-28 15:28 · android (세션 종료)
이번 세션 안드로이드 작업 23건 일괄 묶음. 핵심 = **DB v16 Summary Cache** (HomeScreen 풀스캔 통점 본격 해결).
- 추가 변경 (위 블록 이후 일부 누락분 정리):
  - **DB v15 → v16**: `sms_contacts_cache` 테이블 신규 (PRIMARY KEY=normalizedSuffix + lastDateMs index). 첫 실행 시 풀스캔 → cache rebuild. 그 후 Room observe 만.
  - 신규 파일: `data/local/entity/SmsContactCacheEntity.kt`, `data/local/dao/SmsContactCacheDao.kt`, `data/repository/SmsContactCacheRepository.kt`
  - `SmsRepository.queryContactsOnce(scanLimit, contactLimit)` suspend 추가 (Application 첫 실행 풀스캔 용)
  - `CallFollowCrmApplication.onCreate`: 캐시 count==0 일 때만 풀스캔 → rebuild
  - `HomeViewModel.smsContactsState`: 풀스캔 Flow → `smsContactCacheRepository.observeAll(500)` 로 전환
  - `SmsReceiver`: 새 SMS 도착 시 `cacheRepository.upsertOne(newContact)` 비동기 (hasOwnerReply=false)
  - `ChatViewModel.sendMessage`: 사장님 발신 성공 시 `cacheRepository.upsertOne` (lastSent=true, hasOwnerReply=true)
  - `AppContainer.pendingNewSmsContacts` 제거 (cache upsert 로 대체, 영속)
  - `HomeViewModel.isInitialSmsLoading`: 첫 cache emit 전까지 true → HomeScreen 상단 얇은 LinearProgressIndicator (2dp)
  - 통화 종료 → 목록 표시 3중 안전망: Pull-to-refresh + HomeScreen 진입 sync + Application TelephonyCallback 동적 등록
  - 알림 small icon: `drawable/ic_notification.xml` (Material 종, fillColor=#FFFFFF) + 옛 PNG 삭제
  - SpamSwipeBox 광고 처리 → 되돌리기 화면 멈춤 fix (confirmValueChange { false })
  - AI 추천 답변 효과:
    - 알림 3번 안 보임 fix (size>=3 우선 + 부분 fallback)
    - 알림 "준비 중..." 무한 멈춤 fix (timeout 시 "🔌 서버 응답 없음")
    - effectiveSuggestions stale 차단 제거 + suggestionsAreStale 별도 노출 (chip 그대로 보이고 "📨 새 메시지가 왔어요" 안내)
  - DatePicker proposal 재선택 시 이전 proposal 자동 교체 (정규식)
  - ChatScreen composer padding 보강 (vertical 2dp → 8dp)
  - 현장 주소 수동 등록 (DB v15, CustomerEntity.address, AddressEditDialog rememberSaveable)
  - resolveAddressForPhone name fallback 제거 (인명 검색 위험 차단)
  - 미확인 KPI: hasOwnerReply 조건 제거 (답장 후 새 메시지도 다시 미확인)
  - 입금 카드 UI 재설계 (4상태 + 인플레이스 펼침)
  - CustomerDetail 옛 "문자" 카드 제거
  - ChatScreen composer 임시저장 (ChatDraftStore)
  - AI chips 재진입 시 보존 (SuggestionsCacheStore)
  - 카드 펼침 [📍 길찾기] 도입 + NavApp util + Settings 네비 앱 선택
  - ✨ AI 다듬기 = Gemini + 컨텍스트 전송 교체 (서버 endpoint 작업 대기)
- commit: 마지막 = `0c45cc1` (Summary Cache). 그 외 이번 세션 commits = 8419a83 → ... → 0c45cc1
- **🚨 cowork 작업 요청 (변동 없음)**: `POST /api/refine` (Gemini 2.5 Flash) endpoint. 사양은 위 12:30 블록 참고. **사장님이 GEMINI_API_KEY 발급 → Mac mini 에 박기 → cowork 가 endpoint 박기** 흐름.
- 다음 액션:
  - cowork: `/api/refine` 박기. + (선택) prepare-reply 가 LLM 완성 후 한 번에 READY 반환 (부분 응답 시 GENERATING 유지) — 알림 3번 안 보임 근본 fix.
  - 사장님: 새 빌드 설치 → DB v15+v16 migration 자동 → Summary Cache 첫 풀스캔 (5초) → 그 후 instant 동작 확인. 메모리 [[session-next-kickoff]] 의 20개 검증 항목 시도.
  - android: 사장님 검증 피드백 받고 다음 결정 — (1) `/api/refine` 도착 시 ✨ 동작 / (2) 본질 방향성 align / (3) 차기 비즈니스 대시보드 진입.

## 2026-05-28 15:50 · android
사장님 폰 (Galaxy S9, Android 10) 새 빌드 첫 실행 crash 2건 fix. adb logcat -d -b crash 로 stack trace 잡고 둘 다 신규 코드 원인 — 같은 사장님 검증 세션에서 발견 + fix.

- **crash 1: `NoClassDefFoundError: Landroid/telephony/TelephonyCallback`** (앱 시작 0초)
  - 원인: `CallFollowCrmApplication.callStateCallback` 을 멤버 필드 (object expression) 로 박아둠. Android 10 (API 29) ART verifier 가 Application 인스턴스화 시점에 멤버 필드 type 들을 미리 resolve 시도 → TelephonyCallback (API 31+) 없음 → crash. SDK_INT 런타임 분기는 이미 too late.
  - fix: 멤버 필드 제거 → `@RequiresApi(S)` 가 달린 `registerTelephonyCallbackS()` 함수 안의 local val 로 격리. 함수는 호출 시점에만 verify 되므로 Android 10 에선 그 클래스를 아예 안 본다. PhoneStateListener fallback 은 그대로.
  - 회귀 방지 메모리: `reference_android_min_sdk_class_load.md` 박음.

- **crash 2: `IllegalStateException: Migration didn't properly handle: sms_contacts_cache`** (DB 첫 query 시점)
  - 원인: `SmsContactCacheEntity` 에 index 선언 없는데 `MIGRATION_15_16` SQL 은 `CREATE INDEX idx_sms_contacts_cache_lastDateMs` 박음. Room schema validator: Expected indices=[] vs Found indices=[Index{...}] → crash.
  - fix: Entity 에 `indices = [Index("lastDateMs", name = "idx_sms_contacts_cache_lastDateMs")]` 추가. 이름까지 정확히 일치.
  - 회귀 방지 메모리: `reference_room_entity_index_must_match_migration.md` 박음.

- 변경: 서버 영향 X (전부 클라이언트). 데이터 모델 동일.
- commit: (이번 커밋)
- 다음 액션:
  - cowork: 변동 없음. `/api/refine` (Gemini) 요청 그대로 대기. 사장님 결정 = endpoint 만 먼저 박고 GEMINI_API_KEY 는 나중 발급.
  - 사장님: 폰 풀스캔 (5~30초, 17000건이라 좀 길 수 있음) 끝나면 카드 보이는지 확인. session_next_kickoff 의 20개 검증 항목 진행.
  - android: 검증 피드백 받고 다음 — 본질/방향성 align 또는 비즈니스 대시보드 진입.

## 2026-05-28 16:00 · cowork (Mac mini, 서버 담당)
§14 (POST /api/refine, Gemini 2.5 Flash) endpoint 신규 구현 완료. android 의 SYNC 2026-05-28 12:30 🚨 요청 그대로.
- 변경:
  - `server/main.py` 에 `POST /api/refine` endpoint 추가.
  - 입력: `{raw, recent_messages:[{role,body,timestamp_ms}], owner_tone_samples:[], customer_name?, customer_memo?}` — 안드로이드가 보낸 사양 그대로.
  - 출력: `{polished: "..."}` (HTTP 200).
  - 모델: `gemini-2.5-flash` (Google Generative Language API v1beta `:generateContent`).
  - 호출: httpx 비동기, timeout 30초, maxOutputTokens 500, temperature 0.7.
  - System prompt: 사장님 톤 샘플 (최대 50개, 5~300자 필터) few-shot inject. 규칙 — 의미 절대 변경 X, 정중한 한국어, 가격·날짜·시간 추가 금지, 길이 유지, 출력은 다듬어진 문장 한 줄만 (인사/설명/태그/백틱 X).
  - User msg: 고객정보(이름/메모) → 최근 대화 최근 20건 → 다듬을 원문 → "한 줄로 답하라" 지시.
  - 단가 dict 에 `gemini-2.5-flash` 추가 — input $0.075/1M, cache_read $0.01875/1M, output $0.30/1M. ₩1380/USD 적용. **prefix 매칭으로 `gemini-2.5-flash-001` 같은 정식 ID도 정확 잡힘.** Sonnet 대비 입력 1/40, 출력 1/50 가격.
  - `log_llm_usage(endpoint="refine", model="gemini-2.5-flash", ...)` 와이어링 — Gemini usageMetadata 의 promptTokenCount / candidatesTokenCount / cachedContentTokenCount 매핑. /api/usage-stats by_endpoint 에 "refine" 누적.
  - 실패 처리:
    - `GEMINI_API_KEY` 미설정 → HTTP 503 (안드로이드는 "AI 서버 연결 실패" 토스트 정확히 트리거).
    - Gemini API 호출 실패 → HTTP 502.
    - raw 빈 입력 → HTTP 400.
  - 환경변수: `GEMINI_API_KEY` 추가 필요. 사장님이 https://aistudio.google.com/apikey 에서 발급 → Mac mini launchd plist EnvironmentVariables 에 박기 → launchctl reload.
- commit: 92eff42 (사장님 push 진행 중)
- 검증 (sandbox ALL PASS, 실측은 GEMINI_API_KEY 박은 후):
  - syntax/import clean (httpx 기존 import 재사용)
  - /api/refine 라우트 등록 OK
  - gemini-2.5-flash 단가 ₩0.3105/건 (수동 계산 일치)
  - gemini-2.5-flash-001 정식 ID prefix 매칭 OK
  - 기존 단가 회귀 (claude-sonnet/haiku, kakao-local) 변동 없음
  - GEMINI_API_KEY 없을 때 503 반환 (안드로이드 토스트 트리거 — Mac mini 실측에서도 확인됨)
  - raw 빈 입력 → 400
  - System/User prompt 빌더 정상
  - 회귀 — 15개 endpoint 모두 등록 (기존 14 + refine 신규)
- 비용 감각: 한 건 다듬기 ~₩0.3. 사장님 하루 50번 다듬어도 월 ~₩450.
- 다음 액션:
  - 사장님: (1) https://aistudio.google.com/apikey 에서 Gemini API 키 발급 (paid tier 권장 — 무료 tier 는 prompt 학습 데이터로 쓰일 수 있음). (2) Mac mini plist 에 `GEMINI_API_KEY` 박기 + launchctl reload. (3) 폰에서 ChatScreen [✨ 다듬기] 실측.
  - android: refine 실측 통과 후 — (선택) prepare-reply 가 LLM 완성 후 한 번에 READY 반환 (알림 3번 안 보임 근본 fix).
  - cowork: 다음 sprint — category-classify (§11), 또는 §13 LLM fallback (옵션).

## 2026-05-28 17:00 · cowork (Mac mini, 서버 담당)
§15 (사업 건강도 인프라) 구축 완료. "스타트업으로서 가치있게 자라기" 위한 첫 step — Cost 추적기 → 사업 건강 진단기로 진화.
- 변경:
  - 신규 DB 테이블 `subscribers` (phone, plan_tier ['founder'|'beta'|'pro'|'enterprise'], monthly_price_krw, name, company, started_at_ms, churned_at_ms, notes). MRR/Margin/ARPU 계산의 source of truth.
  - 신규 endpoint 3개:
    - POST /api/admin/subscribers/upsert — 사용자 등록/수정/해지 (X-Admin-Token 헤더 필수)
    - GET  /api/admin/subscribers — 전체 목록 (include_churned 옵션)
    - GET  /api/admin/business-stats — MRR + COGS + Margin + ARPU + Top users + 시간×요일 heatmap (7×24)
  - 신규 환경변수: `ADMIN_TOKEN` (plist EnvironmentVariables 에 박힘. 미설정 시 admin endpoint 503).
  - 신규 dashboard 카드 3개 (관리자 토큰 필요):
    - 💼 사업 건강도 — MRR / COGS / Gross Margin (80%+=초록, 50-80%=노랑, <50%=빨강), ARPU, cost per user, churn
    - 👥 Top 사용자 — 호출수/비용/구독료/유저 마진 (heavy user 빨강 마킹)
    - 📊 시간 × 요일 heatmap — 사장님들이 언제 가장 많이 쓰나 패턴 분석
  - 관리자 토큰 입력 UI — 첫 진입 시 password 입력 → localStorage 저장 → 다음부터 자동 unlock
  - 매일 아침 8시 자동 보고 — `server/ringgo_daily_report.sh` + `com.detailline.ringgo-daily-report.plist.template` (launchd StartCalendarInterval Hour=8). Cost spike 자동 알림 (어제 비용 > 임계값 시 stdout log + 추후 Gmail 발송).
- commit: (사장님 push 진행 중)
- 검증 (sandbox ALL PASS):
  - admin endpoint 3개 등록 OK
  - subscribers 테이블 생성 OK
  - ADMIN_TOKEN 미설정 → 503, 잘못된 토큰 → 401, 정확한 토큰 → 정상 응답
  - upsert create/update/churn/재활성화 4가지 시나리오 정상
  - MRR/ARPU/Margin/by_plan 계산 정확 (가짜 데이터로 검증)
  - heatmap shape 7×24
  - invalid plan_tier → 400
  - 회귀 — 기존 7개 critical endpoint (prepare-reply, refine, address-resolve, usage-stats, card/conversation/next-action) 모두 살아있음
- 사용 시나리오:
  1. 사장님 본인 등록 — POST /api/admin/subscribers/upsert {phone, plan_tier="founder", monthly_price_krw=0, name, company}
  2. 베타 사용자 추가 — plan_tier="beta", monthly_price_krw=0
  3. 정식 사용자 — plan_tier="pro", monthly_price_krw=29000 (또는 39000)
  4. 해지 — 같은 phone 으로 upsert + churned=True
- 다음 액션:
  - 사장님: (1) ADMIN_TOKEN 랜덤 발급 (예: `openssl rand -hex 24`). (2) plist 에 박기 + launchctl reload. (3) /admin 페이지에서 토큰 입력. (4) 사장님 본인 founder plan 으로 등록. (5) 베타 사용자 받기 시작 — 새 phone 마다 upsert.
  - 사장님 (선택): launchd plist 도 ~/Library/LaunchAgents/ 에 설치 → 매일 아침 8시 자동 보고 활성화.
  - android: 사장님이 RING-GO 앱 안에 사용자 관리 UI 만들 시점 — POST /api/admin/subscribers/upsert 호출하면 됨.
  - cowork: 다음 sprint — (a) Cohort retention grid (M0/M1/M3/M6 잔존율), (b) A/B test 인프라 (Sonnet vs Haiku 다듬기 품질), (c) Anomaly detection 자동화 (어제 대비 +50% 알림 메일).

## 2026-05-28 18:30 · cowork (Mac mini, 서버 담당)
모델 배치 효율화 완료. 원칙: Sonnet 의 자리는 "사장님 톤이 매출 직결" 인 자리만. 나머지는 Haiku 4.5 로 내려서 ~63% 비용 절감.
- 변경 (모델 배치):
  - `/api/card-summary`         → **claude-haiku-4-5** (Sonnet 의 1/3 가격, 단순 한 줄 요약)
  - `/api/conversation-summary` → **claude-haiku-4-5** (정형 요약 + enum 분류, Haiku 충분)
  - `/api/next-action-suggest`  → **claude-haiku-4-5** (분류 워크로드)
  - `/prepare-reply`            → **Sonnet 4.6 유지** (사장님 톤 = 매출 직결, 품질 우선)
  - `/api/refine`               → Gemini 2.5 Flash (그대로)
  - `/api/address-resolve`      → 카카오 (그대로)
- 변경 (코드):
  - `HAIKU_MODEL` 상수 추가 ("claude-haiku-4-5", alias — Anthropic API 가 정식 ID 로 자동 매핑)
  - `call_claude_json` 에 `model` 파라미터 추가 (default = Sonnet)
  - `_handle_summary_endpoint` 도 `model` 받음
  - 3 endpoint 가 `model=HAIKU_MODEL` 명시
- 변경 (prepare-reply prompt caching 강화):
  - `build_system_blocks()` 신규 — system 을 4 block 으로 분리:
    - A. 고정 규칙 (사장님 톤 학습 + 답변 3개 차별화) — 영원히 안 변함, cache_control
    - B. 가격표 — pricing.md mtime 변경 시만, cache_control
    - C. 사장님 톤 샘플 50건 — 같은 사장님이면 거의 동일, cache_control
    - D. 답 형식 강제 — 영원히 안 변함, prefix 매칭으로 자동 hit
  - 같은 사장님 5분 내 재호출 시 ~90% cache 적중 (입력 비용 1/10)
- 비용 임팩트 (현 데이터 215건/월 기준):
  - 직전: ₩4,148/월 (전부 Sonnet)
  - 예상: ~₩1,537/월 (Haiku 전환 + caching 강화)
  - 절감: **~₩2,615/월 (63% 절감)**
- 검증 (sandbox ALL PASS):
  - syntax/import clean
  - CLAUDE_MODEL=sonnet-4-6 / HAIKU_MODEL=haiku-4-5 상수 박힘
  - call_claude_json + _handle_summary_endpoint 둘 다 model 받음
  - 3 summary endpoint 가 model=HAIKU_MODEL 명시
  - build_system_blocks — 4 blocks, 그중 3 개 cache_control 박힘
  - 각 block 의 의도 정확 (A 고정/B 가격표/C 톤/D 형식)
  - Sonnet ₩14.49 > Haiku ₩4.83 단가 비율 1:3
  - 회귀 — 23개 endpoint 모두 등록
  - prepare-reply 가 build_system_blocks 사용 (multi-block + cache_control)
- 자동 모니터링:
  - 단가 dict 의 prefix 매칭 덕분에 by_model 응답에 모델별 비용 자동 분리
  - 대시보드 "기능 × 모델 매트릭스" 카드에 변경분 자동 반영 (card/conversation/next-action 의 모델 column 이 sonnet → haiku 로 자동 이동)
  - prompt cache 적중률 = /api/usage-stats by_endpoint 의 cache_read_tokens vs prompt_tokens 비율 (대시보드에 노출됨)
- commit: (사장님 push 진행 중)
- 다음 액션:
  - 사장님: 한 줄 deploy → 폰에서 card-summary 호출 (메인 홈화면) → 응답 품질 체감 비교 (Sonnet 시절 vs Haiku 시절). 차이 못 느끼면 성공 — 같은 품질에 1/3 비용.
  - 사장님 (1주 후): /api/usage-stats?period=month 확인 → 비용 그래프 ₩4k → ₩1.5k 떨어졌는지 검증.
  - android: 변동 없음. 서버 model 만 바뀌어서 응답 형식 동일 — 클라이언트 영향 X.
  - cowork: A/B test 인프라 (다음 sprint) — Haiku 응답 품질이 진짜 Sonnet 만큼인지 데이터로 검증. 만약 quality 떨어지면 일부 endpoint Sonnet 으로 복구.

## 2026-05-28 19:00 · android
**킬러 콘텐츠 1단계 = AI 추천 답변 의도 분화** 안드로이드 측 박음. 사장님 결정 (2026-05-28): 유료 앱 정당화의 핵심 = "내가 수정할 부분이 없는 AI 추천 답변" → 패러다임 전환 = "답변 3개" → **"상담 전략 3개"**.

- 변경 (안드로이드):
  - `ReplySuggestions.suggestions: List<String>` → `List<ReplyChoice>` 로 확장.
  - 신규 `ReplyChoice(text, label?, intentKey?, why?)` data class. label = "💰 견적 안내" 형식. why = 로그용 미노출.
  - `ReplySuggestions` 에 scenario / scenarioConfidence / scenarioReason 필드 추가 (UI 미노출, 로깅용).
  - `ServerSuggestionRepository.parseFetchResult` — 새 스키마 (객체 list) + 옛 스키마 (string list) 둘 다 지원. **서버 배포 전이어도 안드로이드 안 깨짐.**
  - `ChatScreen.SuggestionChip` — label != null 이면 카드 상단에 작은 라벨 줄 (이모지 + 텍스트) + 본문, label == null 이면 기존 모양 (번호 + 본문).
  - `SmsReceiver` — fetch 결과 `.map { it.text }` 로 변환해서 NotificationHelper 에 전달 (알림은 chip UI 없어 라벨 불필요).
- 서버 영향 X (아직 옛 스키마 받는 중). cowork 가 새 스키마 박는 순간 자동 활성화.
- commit: (이번 커밋)
- **🚨 cowork 작업 요청 — `POST /api/prepare-reply` + `GET /suggestions/{phone}` 응답 스키마 v2**:
  - **목표**: 3개 답변을 단순 string list 가 아니라 **서로 다른 상담 전략 3개** 로 강제. 시나리오 분류도 함께.
  - **응답 스키마 (GET /suggestions/{phone}) — 필드 추가**:
    ```json
    {
      "status": "READY",
      "phone": "...",
      "basedOnMessage": "...",
      "basedOnReceivedAtMs": 0,
      "generatedAtMs": 0,
      "scenario": "price_inquiry",
      "scenario_confidence": 0.78,
      "scenario_reason": "고객이 평수 공유 후 가격 문의",
      "suggestions": [
        {"intent_key":"quote","label":"💰 견적 안내","text":"...","why":"..."},
        {"intent_key":"condition","label":"✅ 조건 확인","text":"...","why":"..."},
        {"intent_key":"booking","label":"📅 예약 유도","text":"...","why":"..."}
      ]
    }
    ```
  - **Intent Pool v1 (사장님 시안 그대로)** — 7개 시나리오 + 1개 fallback:
    | scenario | 3종 intent (intent_key / label) |
    |---|---|
    | `initial_inquiry` (초기 문의) | `quick`/📞 빠른 답변, `info`/❓ 정보 요청, `assure`/🤝 안심 설명 |
    | `price_inquiry` (가격 문의) | `quote`/💰 견적 안내, `condition`/✅ 조건 확인, `booking`/📅 예약 유도 |
    | `hesitation` (고객 망설임) | `price_explain`/💬 가격 설명, `case`/📷 사례 제시, `nudge`/➡️ 결정 유도 |
    | `schedule` (일정 조율) | `date_confirm`/🗓️ 날짜 확정, `alternative`/🔄 대안 제시, `prep`/📋 준비 안내 |
    | `pre_booking` (예약 확정 전) | `deposit`/💵 계약금 안내, `final_check`/✔️ 최종 확인, `caution`/⚠️ 주의사항 안내 |
    | `pre_service` (시공 전) | `visit`/🚪 방문 안내, `prep_req`/📝 준비 요청, `assure_pre`/🛡️ 안심 안내 |
    | `post_service` (시공 후) | `usage`/📖 사용 안내, `review`/⭐ 후기 요청, `upsell`/🎁 추가 제안 |
    | **`fallback_default`** (분류 신뢰도 낮음) | `general`/💬 무난 답변, `clarify`/❓ 추가 확인, `manual`/✍️ 직접 확인 |
  - **System prompt 강제 사항**:
    1. 최근 대화 (recent_messages) 보고 위 8개 시나리오 중 하나로 분류 (`scenario` 필드).
    2. 신뢰도 측정 → `scenario_confidence` (0.0~1.0). 0.6 미만이면 **`fallback_default`** 로 빠짐 (사장님 결정: `initial_inquiry` 가 아닌 별도 fallback 세트 사용).
    3. 선택된 시나리오의 정의된 3종 intent_key 만 사용. label 은 위 표 그대로.
    4. **"suggestions 3개는 반드시 서로 다른 상담 전략. 단순 말투/길이/친절도 차이는 실패."** (사장님 명시 강조)
    5. 각 답변에 `why` 한 줄 (왜 이 답변을 추천했는지) — UI 미노출, 로그/품질 개선용.
    6. JSON schema 강제, 위 4개 top-level 필드 + suggestions[3] 필수.
  - **모델**: 현재 `claude-sonnet-4-6` 그대로 (의도 분화는 답변 품질 핵심이라 Sonnet 유지). 18:30 cowork 작업의 `/prepare-reply → Sonnet 유지` 결정과 일치. 추후 [E] 2단계 분리 시점에 Haiku=분류, Sonnet=생성으로 재배치 가능.
  - **prompt caching 통합**: 18:30 cowork 가 만든 `build_system_blocks()` 의 **block A (고정 규칙)** 안에 이번 사양 (8 시나리오 분류 + 3종 intent 강제 + JSON schema) 을 추가. block A 는 cache_control 박힌 영원 캐시 → 의도 분화 사양도 자동 캐시 수혜.
  - **호환성**: 안드로이드는 옛 스키마 (`suggestions: ["..."]`) 도 fallback 으로 받음. 서버 배포 전엔 옛 모드 그대로 동작.
  - **로깅**: `log_llm_usage(endpoint="prepare-reply", ...)` 에 scenario 필드 함께 기록하면 추후 시나리오별 채택률 분석 가능.
- 다음 액션:
  - cowork: 위 v2 스키마 + Intent Pool + system prompt 강제 사항 박기. push 후 SYNC append.
  - 사장님: cowork 작업 완료 후 새 빌드 설치 → ChatScreen 진입 시 chip 위에 라벨 노출 확인 → 3개 답변이 진짜 다른 전략인지 검증.
  - android: cowork 완료 후 1단계 검증 끝나면 → [2단계 상황 분류 정확도 측정 / 3단계 채택·수정 tracking] 진행. 로드맵 6단계 ([[project-killer-content-roadmap]] 메모리 참고).

## 2026-05-28 20:00 · cowork (Mac mini, 서버 담당)
prepare-reply 응답 스키마 v2 박음. android 의 19:00 요청 100% 반영. **"답변 3개" → "상담 전략 3개"** 패러다임 전환 서버 측 완료.
- 변경 (코드):
  - `INTENT_POOL_V1` 상수 — 8 시나리오 × 3 intent (initial_inquiry / price_inquiry / hesitation / schedule / pre_booking / pre_service / post_service / fallback_default).
  - `SCENARIO_CONFIDENCE_FLOOR = 0.6` — 사장님 결정 반영 (이 미만이면 fallback_default 강등).
  - `_SYSTEM_BLOCK_A_FIXED` 안에 v2 사양 inject — 시나리오 분류 + Intent Pool + "단순 말투 차이는 실패" 강조 + JSON schema. **cache_control 박힌 block 이라 영원 캐시 → v2 사양도 추가 비용 거의 0**.
  - `_SYSTEM_BLOCK_D_FORMAT` 의 JSON schema v2 로 (scenario / scenario_confidence / scenario_reason / suggestions[3 obj]).
  - `_coerce_v2_suggestions()` — 안전망 4단계:
    1. scenario enum 외 → fallback_default
    2. confidence < 0.6 → fallback_default (사장님 결정)
    3. invalid intent_key → 제외 + 시나리오 default 로 padding
    4. label 은 우리 정의로 강제 overwrite (모델이 다른 라벨 박았어도)
  - `_parse_suggestions_v2()` — JSON 파싱 실패 시에도 fallback_default 빈 답변 3개로 안전 반환.
  - `call_claude_for_suggestions_with_meta()` 반환: `list[str]` → `dict (v2)`.
  - `generate_and_cache()` — v2 dict 그대로 DB 저장. stdout 로그에 scenario/confidence/intents 포함.
  - `db_set_ready(phone, v2: dict)` — schema 변경 없이 suggestions_json 안에 dict 통째 직렬화.
- 변경 (GET /suggestions/{phone} 응답):
  - 신규 top-level 필드: scenario / scenario_confidence / scenario_reason.
  - suggestions: [str] → [{intent_key, label, text, why}].
  - **legacy v1 cache (옛 string list) 자동 wrap → fallback_default 시나리오** — 배포 직후 캐시 호환.
- 호환성: 안드로이드 19:00 작업의 parseFetchResult 가 v1 + v2 둘 다 parse 가능 → 점진 배포 안전.
- 모델: claude-sonnet-4-6 그대로 (사장님 톤 + 상담 전략 = 매출 직결).
- 검증 (sandbox ALL PASS, 11 시나리오):
  - INTENT_POOL_V1 8 scenarios × 3 intents
  - block A 에 v2 사양
  - coerce 정상 + label overwrite
  - confidence < 0.6 → fallback_default 강등
  - unknown scenario → fallback_default
  - invalid intent_key 거부 + padding
  - JSON 파싱 실패 → fallback_default 안전 반환
  - DB 저장/로드 — v2 dict 직렬화
  - GET 응답 v2 schema 완벽
  - v1 legacy cache → v2 wrap 자동 호환
  - 회귀 — 10 critical endpoint 살아있음
- commit: (사장님 push 진행 중)
- 다음 액션:
  - 사장님: 한 줄 deploy → 폰 ChatScreen 진입 → chip 위 label (📞/💰/✨ 등) 노출 + 3개 답변이 진짜 다른 상담 전략인지 검증.
  - 사장님: stdout.log 에 `[ready] scenario=... conf=... intents=...` 라인 — 시나리오 분류 정확도 빠른 검증.
  - android: 1단계 검증 통과 후 → 2단계 분류 정확도 측정 / 3단계 채택·수정 tracking (events 테이블 준비 시점).
  - cowork: 다음 sprint — (a) scenario 통계 endpoint (시나리오별 채택률, events 와 함께), (b) A/B test 인프라.

## 2026-05-29 14:00 · android
**MMS 알림 통점 해결 Phase A 1단계 = 기본 SMS 앱 자격 인프라 박음.** 사장님 통점 = "MMS 가 RING-GO 로 알람이 안 옴". 원인 = WAP_PUSH_DELIVER 는 default SMS 앱만 받는데 RING-GO 가 default 아님. 사장님 결정 (2026-05-29) = 옵션 A "RING-GO 를 default SMS 앱으로 만들기". 단계 분할 — 1단계는 자격 인프라만, 실제 전환은 2단계 (MMS 본격 구현) 끝난 후.
- 변경 (안드로이드):
  - **AndroidManifest** — Default SMS 앱 자격 4개 컴포넌트 박음:
    1. SmsReceiver 에 `SMS_DELIVER_ACTION` intent-filter 추가 (RECEIVED 와 병기).
    2. 신규 MmsReceiver — `WAP_PUSH_DELIVER` + mimeType `application/vnd.wap.mms-message` + `BROADCAST_WAP_PUSH` permission.
    3. 신규 SmsHeadlessSendService — `RESPOND_VIA_MESSAGE` + `SEND_RESPOND_VIA_MESSAGE` permission + sms/smsto/mms/mmsto schemes.
    4. MainActivity 에 SENDTO + VIEW intent-filter + sms/smsto/mms/mmsto schemes.
  - 권한 추가: `WRITE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`.
  - **SmsReceiver** — onReceive 가 SMS_DELIVER 분기 처리:
    - DELIVER 일 때 (default 만): 시스템 SMS provider Inbox 에 `ContentValues` INSERT 책임 (Telephony.Sms.MESSAGE_TYPE_INBOX, read=0, seen=0). INSERT 실패해도 알림/prepare-reply 는 그대로 진행.
    - RECEIVED 일 때 (default 아닐 때, 현재 상태): 시스템이 INSERT 책임 → 우리는 prepare-reply 만 (지금이랑 동일).
  - **MmsReceiver stub** — 1단계는 WAP_PUSH_DELIVER 받기만 받고 처리 X. 호출 시 `Log.w` 로 "premature toggle warning" 출력. **default 토글 disabled 인 동안엔 호출 자체 안 됨.**
  - **SmsHeadlessSendService stub** — RESPOND_VIA_MESSAGE 호출 받아도 stopSelf 만. 사장님은 RING-GO 안에서 답장하므로 1단계 무동작 OK.
  - **MainActivity SENDTO trampoline** — sms:01012345678[?body=hi] / smsto:/mms:/mmsto: 받으면 phone 추출 → ChatScreen 으로 navigate. body 가 있고 ChatDraftStore 가 비어있으면 prefill (사장님 작성 draft 안 덮어쓰기 안전망).
  - **DefaultSmsAppHelper util 신규** — RoleManager (Q+) / Telephony.Sms.getDefaultSmsPackage (P 이하) 분기. isCurrentDefault / createRequestIntent / createReleaseIntent.
  - **SettingsScreen 최상단 DefaultSmsAppCard 추가** — disabled (회색) Switch + 사장님 카피 그대로:
    - 메인: "📱 RING-GO를 기본 메시지 앱으로 사용하기"
    - 설명: "SMS/MMS 수신을 RING-GO에서 관리합니다. 현재는 준비 중이며, MMS 안정화 후 활성화됩니다."
    - 안내: "🚧 2단계 MMS 처리 완료 후 사용할 수 있습니다"
- 서버 영향 X (전부 클라이언트). 신규 endpoint 호출 없음.
- 사장님 체감: **1단계는 변화 0**. 토글 disabled 라 default 전환 안 일어남. SMS_RECEIVED 그대로 받고 prepare-reply 그대로 돔.
- commit: (이번 커밋)
- 다음 액션:
  - cowork: 변동 없음. MMS 작업은 안드로이드 영역.
  - 사장님: 새 빌드 깔아도 동작 변화 0 — 안정성 확인만 (앱 진입 / 메시지 수신 / 기존 SMS 알림 정상).
  - android (다음 세션): **Phase A 2단계 — MMS 본격 구현**. (1) WAP_PUSH PDU 파싱 (M-Notification.ind), (2) MMSC HTTP GET (APN 의존, 통신사별 디버깅), (3) M-Retrieve.conf 파싱 → 본문 + 첨부 파트 분리, (4) content://mms 및 mms/part 에 INSERT, (5) 첨부 이미지 다운로드, (6) NotificationHelper.showIncomingMms() 트리거, (7) PrepareContext 에 첨부 메타 추가 (Vision 호출). 2단계 끝나면 DefaultSmsAppCard 의 `enabled = true` 한 줄 fix.

## 2026-05-29 15:30 · android
**Phase A 2단계 Day 1~3 = MMS 다운로드 인프라.** 사장님 통점 (사장님 폰만 위한 게 아니라 14명 테스터 + 향후 paid 모두를 위한 product) 반영해서 **통신사 hardcoding 0**. klinker library (이미 deps 박혀있던 5.2.5) 수신측 wrap 활용해서 작업 양 대폭 단축.
- klinker 5.2.5 수신측 발견:
  - `com.klinker.android.send_message.MmsReceivedService extends IntentService` — onHandleIntent 만 override 하면 전체 다운로드 + persist 자동.
  - `com.google.android.mms.pdu_alt.PduParser/PduPersister` — WAP_PUSH M-Notification.ind / RetrieveConf 파싱 + content://mms 및 mms/part INSERT 전부 wrap.
  - `com.android.mms.transaction.RetrieveTransaction` — MMSC HTTP GET + ConnectivityManager.requestNetwork(TYPE_MOBILE_MMS) + proxy 적용 다 자동.
  - `com.android.mms.service_alt.MmsConfigManager` + `com.klinker.android.send_message.ApnUtils.initDefaultApns` — APN 자동 추출 (통신사 hardcoding 없이). KT/SKT/LGU+/알뜰 다 자동.
- 변경 (안드로이드):
  - 신규 `MmsDownloadService extends klinker MmsReceivedService` — onHandleIntent 에서 super 호출 → klinker 가 PDU 파싱 + APN 자동 추출 + MMSC HTTP GET + 응답 파싱 + content://mms 및 mms/part INSERT 다 처리. 우리 hook (NotificationHelper / prepare-reply / 첨부 메타) 은 placeholder — 다음 세션 (Day 4~5) 본격 구현.
  - **MmsReceiver 본격화** (1단계 stub → 2단계 본격): WAP_PUSH_DELIVER 받으면 MmsDownloadService 에 intent forward (action/extras/type 전부 보존). broadcast scope 안에서 MMSC HTTP GET 안 함 (수 초~수십 초 소요 → goAsync 한도 초과). IntentService 위임 = 안드로이드 표준.
  - **AndroidManifest** — `MmsDownloadService` 등록 (exported=false, 내부 MmsReceiver 만 trigger).
- 통신사 대응:
  - APN 자동 추출 chain — klinker ApnUtils → MmsConfigManager → CarrierConfigManager (klinker 가 모두 내부 wrap).
  - 사용자 수동 입력 fallback UI — 다음 세션. 사장님 결정: "MMS 수동 입력 칸 + 자동 감지 결과 레이블" (Settings 에 노출, 자동 추출 결과 미리보기).
- 서버 영향 X.
- 사장님 체감: **여전히 변화 0**. Settings 토글 disabled 라 MmsReceiver onReceive 호출 자체 안 됨. 1단계 + 2단계 Day 1~3 = 전부 인프라.
- commit: (이번 커밋)
- 다음 세션 (Day 4~5) 작업:
  1. MmsDownloadService hook 본격: super 호출 후 content://mms/inbox 최근 row 추출 → sender + body + 첨부 파트 → NotificationHelper.showIncomingMms 트리거 → PrepareContext 구성 → suggestionRepository.requestPrepare.
  2. Settings 에 "MMS 서버 수동 입력 (선택)" UI + 자동 감지 결과 레이블 ("자동 감지: KT (MMSC: http://mmsc.ktfwing.com:9082)" 식).
  3. **DefaultSmsAppCard.enabled = true** (한 줄 fix) — 토글 활성화.
  4. 사장님 폰 (S9/Android 10) + 14명 테스터 폰에서 default 토글 켜고 실제 MMS 수신 검증. 통신사별 케이스.
  5. 향후 — 첨부 사진을 서버 Vision 으로 보내서 견적/주소/평수 자동 추출 (PrepareContext 의 attachments 메타).
- 다음 액션:
  - cowork: 변동 없음.
  - 사장님: 새 빌드 깔아도 동작 변화 0 — 안정성 확인 (앱 진입 / 기존 SMS 수신/알림 / 빌드 자체 ok).
  - android (다음 세션): 위 5가지 작업 + 사장님 폰 검증.

## 2026-05-29 16:30 · android
**Phase A 2단계 Day 4~5 = MMS hook 본격 + Settings 토글 활성화.** 사장님 통점 (MMS 알림 안 옴) 해결까지 한 발 더. 이제 사장님이 토글 켜면 진짜로 MMS 알림이 RING-GO 로 옴.
- 변경 (안드로이드):
  - **SmsRepository.queryLatestInboxMms()** public 신규 — content://mms inbox 가장 최근 row 추출. addresses + parts (text + 첨부 URI) 통째 반환. MmsDownloadService 가 hook 에서 사용.
  - **MmsDownloadService hook 본격화**:
    1. super.onHandleIntent — klinker 가 download + persist 완료
    2. queryLatestInboxMms (3회 polling, klinker INSERT 비동기 안전망)
    3. body = `📎 사진 N장\n\n{원본}` (첨부 prefix)
    4. NotificationHelper.showIncomingSms 재사용 (같은 채널·ID 통합 UX)
    5. SmsContactCacheRepository.upsertOne → HomeScreen 즉시 갱신
    6. PrepareContext 구성 (history/customer/tone/schedules) + suggestionRepository.requestPrepare (fire-and-forget)
    7. smsCachePrefetcher.prefetchForNumber
    - IntentService worker thread 에서 runBlocking 으로 suspend 함수 호출 (alive 한 동안)
    - polling/algo 는 SmsReceiver 와 일관
  - **AppPreferences** 신규 3 필드: `manualMmscUrl`, `manualMmscProxy`, `manualMmscPort` — 자동 추출 실패 시 안전망. 알뜰폰/특수 SIM 14명 중 1명 케이스.
  - **DefaultSmsAppCard 본격 활성화** (Day 5 약속 한 줄 fix 그 이상):
    - `enabled = true`
    - Switch onCheckedChange — RoleManager.createRequestRoleIntent / createReleaseIntent launch via ActivityResultContracts
    - 동적 설명 문구 (default 일 때 vs 아닐 때)
    - **🔧 MMS 서버 수동 입력 (선택)** expander — MMSC URL / proxy / port 3 필드 + 저장 버튼
    - 자동 추출 결과 레이블 = 다음 sprint (klinker ApnUtils 비동기 콜백 + 결과 표시)
- 서버 영향 X (전부 클라이언트). PrepareContext 의 첨부 메타 (Vision 호출용) 는 다음 sprint.
- 사장님 체감: **드디어 변화 있음**. 사장님이 Settings 의 토글 켜고 시스템 다이얼로그 동의 → RING-GO 가 default → MMS 받으면 RING-GO 알림 (사진 N장 표시) + ChatScreen 진입 시 AI 추천 답변.
- commit: (이번 커밋)
- **🚨 사장님 폰 + 14명 테스터 폰 실제 검증 필요** — 본격 product 검증의 첫 단추:
  1. 사장님: Settings 진입 → 토글 ON → 시스템 다이얼로그 동의 → 폰에 본인 또는 다른 폰으로 MMS (사진 첨부) 보내서 RING-GO 알림 뜨는지 확인.
  2. 안 뜨면 → Settings → 🔧 MMS 서버 수동 입력 expander → MMSC URL 박기 (KT: `http://mmsc.ktfwing.com:9082`, SKT: `http://omms.nate.com:9082`, LGU+: `http://omms.uplus.co.kr:9084`).
  3. 14명 테스터 (KT/SKT/LGU+/알뜰 케이스 모두) — 첫 사용자가 MMS 받았는지 사장님께 보고 받기.
- 다음 sprint 후보:
  - (a) 자동 추출 결과 레이블 — klinker ApnUtils 비동기 콜백 → "자동 감지: KT (MMSC: http://mmsc.ktfwing.com:9082)" 표시.
  - (b) 첨부 사진 → 서버 Vision (PrepareContext 의 attachments 메타 + cowork 의 prepare-reply 가 Vision 호출).
  - (c) 알림 BigPictureStyle — 첫 첨부 사진 thumbnail.
  - (d) 14명 테스터 onboarding UI — 첫 진입 시 default 앱 전환 안내 다이얼로그.
- 다음 액션:
  - cowork: 변동 없음 (다음 sprint 의 Vision 호출 시점에 prepare-reply prompt 확장).
  - 사장님: 위 검증 3단계 + 14명 테스터 결과 수집.
  - android: 사장님 검증 결과 받고 — (1) 안 되는 통신사 디버깅 / (2) 자동 추출 결과 레이블 / (3) Vision 메타 확장.

## 2026-05-29 18:00 · android
**킬러콘텐츠 3단계 = 채택/수정 데이터 수집.** "수정 거리 0" 이 product 목표 — 사장님이 추천 답변을 그대로 보낼수록 우리 품질 ↑. 사장님 행동 5가지 시그널을 자동으로 백그라운드 capture. 4/5/6단계 (Tone RAG / 페르소나 / 학습 루프) 의 기반 인프라.
- 변경 (안드로이드):
  - **DB v16 → v17** — `suggestion_events` 테이블 신규 (id / phoneSuffix / scenario / scenarioConfidence / intentKey / intentLabel / suggestionText / action / finalSentText / editDistance / createdAtMs / reportedToServer). 2개 인덱스 (createdAtMs, reportedToServer).
  - 신규 `SuggestionEventEntity` + `SuggestionEventDao` + `SuggestionEventRepository` (DB write + pending pickup + 통계).
  - `SuggestionEventAction` 상수 5종: SENT_AS_IS / EDITED / REFINED_THEN_SENT / IGNORED / DISMISSED.
  - **ChatViewModel hook**:
    - 신규 state: `pickedChoice` / `pickedRefined` / `pickedActioned` / `pickedSuggestionsSnapshot`.
    - `onSuggestionTapped(ReplyChoice)` — chip 탭 시 호출 → picked snapshot 저장.
    - `aiPolish` — picked 가 set 된 상태에서 다듬기 호출 → `pickedRefined = true` (REFINED_THEN_SENT 판정 기준).
    - `sendMessage` 성공 시 `captureSendSignal(sentText)` 자동 호출:
      - picked 있고 sentText == picked.text → **SENT_AS_IS** (editDistance=null)
      - picked 있고 pickedRefined → **REFINED_THEN_SENT** (editDistance=null)
      - picked 있고 다른 경우 → **EDITED** + Levenshtein 거리 계산
      - picked 없고 suggestions 노출됐었으면 → **IGNORED** (사장님이 chip 보고 직접 typing)
      - 둘 다 아니면 시그널 안 박음 (잡음)
    - `onCleared()` override — picked 있고 actioned 안 됨 → **DISMISSED** (applicationScope 로 비동기 박음).
  - **AppContainer** — `suggestionEventRepository` DI + `applicationScope` 신규 (onCleared 후 비동기 작업용).
  - **ChatScreen** — `SuggestionArea.onPickSuggestion: (String)` → `onPickChoice: (ReplyChoice)` 시그니처 확장. 호출처에서 viewModel.onSuggestionTapped(picked) → input = picked.text.
  - Levenshtein 거리 = 단순 DP (max 1000자 cutoff 안전망). SMS 짧아 충분.
- 서버 영향 X (전부 클라이언트).
- 사장님 체감 0 — 백그라운드 capture, UI 노출 없음. 다음 sprint 에 Settings 카드로 채택률 표시.
- commit: (이번 커밋)
- **🚨 cowork 작업 요청 — `POST /api/suggestion-events` batch endpoint 신규** (선택, 다음 sprint 도 가능):
  - **목표**: 안드로이드가 쌓아둔 pending event 들을 batch upload → 서버에서 시나리오별 채택률 / 평균 수정 거리 / intent_key 별 ranking 등 분석. 사장님 multi-user (14명 테스터 / paid) 확장 시 cohort 분석 핵심.
  - **요청 스키마**:
    ```json
    {
      "device_id": "anonymous-uuid",
      "events": [
        {
          "phone_suffix_hash": "sha256(phone)",
          "scenario": "price_inquiry",
          "scenario_confidence": 0.78,
          "intent_key": "quote",
          "intent_label": "💰 견적 안내",
          "suggestion_text": "...",
          "action": "EDITED",
          "final_sent_text": "...",
          "edit_distance": 12,
          "created_at_ms": 1748541234567
        }
      ]
    }
    ```
  - **응답**: `{"received": N, "stored": N}` (200).
  - **server-side DB**: 신규 테이블 `suggestion_events` (위 필드 그대로 + uploaded_at_ms + device_id).
  - **활용 sprint** (4단계 Tone RAG 시점):
    - EDITED 의 (suggestion vs final_sent) 페어 → 사장님 톤 high-quality sample.
    - 시나리오별 채택률 → fallback_default 빈도 / scenario_confidence 분포 분석.
    - intent_key 별 ranking → "사장님이 어떤 intent 를 가장 자주 그대로 보내는지" 측정.
  - **익명화**: phone 자체 보내지 않음. phone_suffix_hash 만. cowork 가 hash key 정책 결정 가능 (안드로이드는 plain suffix → 서버가 hash).
  - 이번 commit 의 안드로이드는 record 만. upload 로직 (HTTP POST + reportedToServer 마킹) 은 다음 sprint 에 박음.
- 다음 액션:
  - cowork: 위 endpoint **선택** — 다음 sprint 에 박아도 OK. android 가 우선 capture 만.
  - 사장님: ChatScreen 에서 답변 chip 탭 / 수정 / 다듬기 / 무시 자연스럽게 사용. 백그라운드 capture 만, 사장님 동선 영향 0.
  - android (다음 sprint): (1) Settings 의 "💡 추천 답변 통계" 카드 — 채택률 / 평균 수정 거리 노출. (2) batch upload 로직 (서버 endpoint 박힌 후). (3) 4단계 Tone RAG 시작 — Mac mini 에 bge-m3 임베딩 모델.

## 2026-05-29 18:30 · android
**Settings "💡 추천 답변 채택률" 카드 추가** — 킬러콘텐츠 3단계 데이터를 사장님이 직접 확인. "수정 거리 0" 진화 모습 시각화 = 동기부여.
- 변경 (안드로이드):
  - **SettingsViewModel**: `suggestionStats` StateFlow + `suggestionStatsPeriodDays` + `loadSuggestionStats(days)`. 초기 load = 이번 주 (7일).
  - **SuggestionStatsCard Composable** 신규 (사장님 톤 학습 카드 바로 아래 = RING-GO 정체성 일관):
    - 상단 — 기간 chip (오늘 / 이번 주 / 이번 달) 선택 가능
    - 채택률 — 큰 % 숫자 (Toss Blue) + "{total}건 중 {adopted}건 그대로 보냈어요"
    - 평균 수정 거리 (EDITED 가 있을 때만) — "✏️ 수정한 답변 평균 N자 고침"
    - 4 분포 막대 (StatsBar) — ✅ 그대로 / ✏️ 수정 / 🤷 무시 / 👋 떠남
    - 데이터 없으면 — "아직 데이터가 없어요. 채팅 화면에서 AI 추천 답변을 사용해보세요." 안내
  - `Surface`, `fillMaxHeight` import 추가.
- 서버 영향 X. 카드 = 로컬 DB v17 의 suggestion_events 집계.
- 사장님 체감: Settings 열면 "RING-GO 가 얼마나 나답게 답하는지" 한눈에. 매일 보고 싶어질 카드.
- commit: (이번 커밋)
- 다음 액션:
  - cowork: 변동 없음.
  - 사장님: 채팅 화면에서 AI 답변 사용 → Settings 열면 채택률 시각 확인. 며칠 사용 후 "수정 거리" 가 줄어들면 RING-GO 가 진화 중이라는 뜻 (Tone RAG 후).
  - android: 다음 sprint — (a) 4단계 Tone RAG (Mac mini bge-m3 + sqlite-vec) / (b) batch upload (cowork endpoint 박힌 후) / (c) 카드의 시간 기준 정밀화 (한국 시각 자정 align).
