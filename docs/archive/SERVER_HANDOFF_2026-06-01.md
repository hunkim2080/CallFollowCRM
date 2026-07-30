> ⚠️ **이 문서는 `SERVER_HANDOFF_2026-06-02.md` 로 대체되었습니다.** 최신 권위본을 보세요. (아래는 이력 보존용)

# 서버(맥미니) Claude 에게 — 2026-06-01 안드로이드 작업 핸드오프

> 안드로이드 Claude 가 오늘 한 일 + 서버 쪽이 알아야/해야 할 것 정리.
> CLAUDE.md 룰상 안드로이드는 `server/` 를 안 만지므로, 아래는 **요청/공유**입니다.
> 작업 시작 전 `git pull --rebase` 후 이 문서 + `docs/IMPLEMENTATION_STATUS.md` 확인 바랍니다.

---

## 1. 새로 생긴 공유 기준표 (앞으로 같이 관리)
- **`docs/IMPLEMENTATION_STATUS.md`** = 프로토 ↔ 실제 앱 구현 현황 SoT (영역별 ✅/🔶/🔷/⬜).
- 보기용 HTML = `design-preview/status-board.html`.
- **부탁:** 서버 쪽 기능(아래 2번)의 상태가 바뀌면 이 표의 해당 항목을 같이 갱신해 주세요.
  (지금 표는 안드로이드 관점으로 채워져 있어, 서버 내부 진척은 서버 Claude 가 더 정확히 압니다.)

## 2. 서버 확인/진행 요청 (앱이 기대하는데 silent 숨김 중인 것들)
앱은 서버 응답이 없으면 **조용히 카드를 숨기게** 되어 있어, 아래가 실제로 도는지 확인 부탁:

1. **카드 한 줄 요약 (cardSummary)** — 홈/일정/고객상세가 `AiSummaryEntity.cardSummary` 를 observe.
   - 앱 호출: `prepare-reply` 시 또는 `ensureCardSummary(SummaryContext)`.
   - 확인: prepare 파이프라인이 cardSummary 를 생성·캐시해 주는지? 비어 있으면 앱엔 ✨요약이 안 뜸.
2. **대화 요약 박스 (aiSummary)** — ChatScreen/고객상세가 conversation summary 캐시 observe.
   - 확인: conversation-summary 엔드포인트(또는 prepare 부산물)가 채워지는지?
3. **AI 다듬기 ✨ (polish/refine)** — 현재 앱은 `OllamaRefineRepository` 로 동작하나 **품질 부족**.
   - 요청: 서버에 **Claude 기반 /refine(또는 /polish)** 엔드포인트 검토. 입력 = 사장님 초안 + 톤 샘플,
     출력 = 사장님 말투로 다듬은 문장. (메모: `project_ai_polish_hookup` — Claude 교체 예정)
   - 앱 측 시그니처(`aiPolish(raw, onPolished)`)는 그대로 두면 본문만 갈아끼워 연결 가능.

## 3. 서버 액션 불필요 (오늘 안드로이드 단독 작업)
- **정산 Phase 1 (미수금 화면)** = 로컬 `CustomerEntity` 데이터만 사용. **서버 API 추가 없음.**
- 향후 정산 Phase 2(현금흐름)·Phase 3(순이익)도 로컬. **단, "비즈니스 리포트/시장 인사이트"**
  단계로 가면 그때 **서버 집계(매출/전국 평균)** 가 필요 → 그 시점에 이 문서로 별도 요청하겠습니다.

## 4. 로드맵 상 서버가 미리 생각해두면 좋은 것 (지금 당장 X)
- **시장 인사이트 카드** (`project_business_goal_pricing`): 10만명 모이면 집계 데이터 = 시장 인덱스.
  "전국 평균 대비 내 변화" 비교용 익명 집계 API 가 미래 핵심. 데이터 모델만 염두에.
- **블로그 후기 글 생성** (유료 훅): 대화 + 현장사진 → 포스팅 초안. 서버 LLM 작업 후보.

---

## 요약 한 줄
> 오늘 건은 **서버 변경 0**. 다만 **카드/대화 요약·AI 다듬기**가 실제로 서버에서 생성되는지 점검 +
> 다듬기의 Claude 전환만 검토 부탁드립니다. 나머지는 `IMPLEMENTATION_STATUS.md` 동기 관리.
