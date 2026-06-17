# SERVER HANDOFF — 2026-06-18 (android → cowork/Mac mini)

안드로이드 Claude 가 협업 화면을 "공유받은 현장 / 내가 공유한 현장" 으로 나눴습니다(commit 6d04a20).
앱은 이미 `GET /api/shared/by-me` 를 호출해 "내가 공유한 현장" 탭을 채웁니다. 아래 3건 서버 작업 부탁드립니다.

---

## 1. `GET /api/shared/by-me` 응답에 `partner_name` 추가  (★ 핵심)

**왜:** "내가 공유한 현장" 탭의 사장님 1순위 요구 = **"누구랑 같이하는지 한눈에"**.
앱은 `SharedSite.partnerName` 으로 파싱해 카드에 `🤝 OO 사장님과 함께` 로 표시합니다.
없으면(미구현) 앱은 "함께할 사장님 수락 대기 중 / 함께하는 사장님과" 안내만 보여줍니다(graceful).

**요청:** by-me 가 돌려주는 각 site 객체에 협업자(B) 표시명을 넣어주세요.
- 키: `partner_name` (문자열). 수락 전(pending)이면 빈 값/생략 OK.
- 값: B 가 수락 시 보낸 `partner_name`(상호) — respond/progress payload 에 이미 들어옵니다.
- 협업자가 여러 명인 구조면, 대표 1명(가장 최근 수락) 또는 "OO 외 N명" 형태 문자열로.

앱 파싱 (참고):
```kotlin
partnerName = o.optString("partner_name").takeIf { it.isNotBlank() && it != "null" }
```

## 2. by-me 가 **지난 날짜 현장도** 돌려주도록  (#9 "6/4 현장 사라짐" 관련)

**증상(사장님):** B폰으로 6/4 협업현장 등록 → A폰에서 수락 → 어느 순간부터 **B폰(주인)에선 6/4 현장이 사라지고 A폰(협업자)에만 남음.**

**안드로이드 분석:** 데이터 유실이 아니라 **주인(A-role)에게 "내가 공유한 현장" 전용 목록이 없었음** — 지금까진 일정 카드 뱃지로만 보였는데 6/4 가 2주 전이라 일정 화면에서 자연스레 빠진 것. 협업자(B-role)는 `with-me` 목록(지난 현장 포함)에 계속 남아 있었고요. **방금 만든 "내가 공유한 현장" 탭(by-me)이 이걸 해결**합니다 — 단 **by-me 가 지난 날짜 현장도 반환**해야 6/4 가 다시 보입니다.

**요청:**
- by-me 에 "upcoming/today 만" 같은 날짜 필터가 있으면 **빼주세요**(최근순 limit 으로만 자르기). with-me 와 동일하게 지난 현장도 포함.
- (확인) 협업자 수락 시 주인 쪽 by-me 에서 그 row 가 사라지는 상태 전이(예: status/owner 재라벨)가 없는지 점검.

## 3. 다운로드 페이지 stale — `server/static/install.html` (#10)

**증상:** 크롬에서 다운로드 페이지가 **v0.1-beta · 20.3MB** (옛날 값) 으로 뜸. 실제 앱은 0.2-beta.

**요청 (3곳):**
- `server/static/install.html:167` — `<div><b>시공막내</b> · 베타 v0.1</div>` 하드코딩 → 현재 버전(0.2-beta)으로.
- `GET /api/download/version` 응답 `version` / `size_mb` 를 현재 배포 APK 기준으로 갱신(앱이 L184/185 에서 이 값을 표시).
- 서버가 **서빙하는 APK 파일 자체**를 최신본으로 교체(테스터가 받는 게 옛 버전이면 안 됨).
  - 파일명: 사장님이 `shigongmagne.apk` 로 통일(앱 build.gradle 도 release output 을 이 이름으로 자동 rename).

---

### 참고 — 앱측 이번 커밋
- 6d04a20 feat(협업): 화면 "공유받은/내가 공유한" 분리 + 카드 제목 실제 현장명+날짜
  - `SharedSiteRepository.SharedSite.partnerName` 필드 + `partner_name` 파싱
  - `SharedSiteViewModel.mySharedSites` = `repo.byMe(myPhone)`
  - 화면 상단 세그먼트 `[공유받은 현장][내가 공유한 현장]`
- e813763 fix(통화요약): 탭한 통화에 요약 강제 연결(앱 단독, 서버 무관)
