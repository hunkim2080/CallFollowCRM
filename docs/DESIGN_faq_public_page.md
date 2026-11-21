# 대표질문 = 손님 공개 FAQ 페이지 — Phase 2 설계 (서버 + 앱 계약)

> 프로토(SoT): `artifact/7U7dnG1GFEoD2RMmd7gRmk` (섞는 버전 — 아스트라 비주얼 + 전체 구조)
> Phase 1(완료·내부배포): `rep_questions.category` 칸(DB v77) + 등록 분류 고르기 + 관리/챗 분류 딱지.
> Phase 2(이 문서): **고정 링크 공개 페이지** + 앱→서버 publish + 공개 토글 + 업체 소개.
>
> ⚠️ **라이브 서버 배포는 폰 확인 + 사장님 「가」 후.** 지금은 repo 에 코드만 준비(staged). 라이브는 git 아님 — `docs/GOTCHAS.md` §3 절차.

## 핵심 통찰 — 손님 문의는 서버 제출이 **없다**
손님 페이지의 "문자로 바로 문의" = **`sms:사장님번호?body=질문` 링크**. 손님 폰의 문자앱이 열려 손님이 보내면,
그 문자가 사장님 폰(= 시공막내, 기본 문자앱)으로 **번호째** 도착 → 기존 SMS 수신 경로로 **상담함 자동 유입**.
→ 서버는 **① 데이터 저장(publish) ② `/faq/{slug}` 렌더** 두 가지만. 문의 제출 엔드포인트·DB 불필요(두 벌 방지).
→ 관리 화면의 "받은 문의"는 **기존 상담함**으로 보내는 입구일 뿐(새 저장소 X).

## 식별 — 고정 slug (업체당 하나)
- 접수서는 `token`=고객당. FAQ 는 **업체당 고정**: `owner_phone` ↔ `slug`.
- slug 생성: 서버가 `biz_name` 로마자화 실패 시 `owner_phone` 해시 6자. **한 번 발급되면 안 바뀐다**(고정 링크 약속).
- URL: `{INTAKE_PUBLIC_BASE_URL}/faq/{slug}` (예: `https://si0in.kr/faq/detailline`).

## 서버 (코덱스 담당 · repo staged, 라이브 X)
### 테이블 (새로 1개)
```
faq_pages(
  owner_phone TEXT PRIMARY KEY,
  slug        TEXT UNIQUE NOT NULL,
  published   INTEGER NOT NULL DEFAULT 0,
  payload_json TEXT NOT NULL,     -- 아래 publish 본문 그대로
  updated_at_ms INTEGER NOT NULL
)
```
사진은 payload_json 안에 base64 dataURL 로(현장 사진과 같은 방식). 1차는 임베드, 최적화는 나중.

### 엔드포인트 2개
1. `POST /faq/publish` — **앱이 호출**(기존 앱→서버 authed 패턴 그대로, owner_phone 은 세션/토큰에서). 본문 = 아래 계약. slug 없으면 발급·있으면 유지. `{ "slug": "...", "url": "..." }` 반환.
2. `GET /faq/{slug}` — **손님용 HTML**(HTMLResponse). `published=0` 이면 "준비 중" 안내. og 태그(카톡 미리보기)·Pretendard — 블로그/접수서와 같은 틀. 아스트라 비주얼(아이보리·SVG 일러스트·정성 카피)을 프로토 `7U7dnG1GFEoD2RMmd7gRmk` **1:1**. 손님 "문자로 보내기" = `sms:{owner_sms}?body=...` 링크(서버 제출 아님).

### robots
`/faq/` 는 공개(검색 노출 OK — 홍보 목적). `/q/` 처럼 Disallow 하지 **않는다**.

## 앱→서버 publish 본문 (계약 — 양쪽 이 모양으로)
```json
{
  "published": true,
  "biz_name": "디테일라인",
  "specialty": "욕실·주방 줄눈 시공 전문",
  "intro": "안녕하세요, 10년 경력 ...",
  "region": "서울·경기",
  "career": "경력 10년",
  "count": "시공 1,200건",
  "owner_sms": "01012345678",
  "cover_photos": ["data:image/jpeg;base64,...", "..."],
  "categories": ["가격", "시공시간", "A/S", "관리법"],
  "questions": [
    { "category": "가격", "q": "평수당 가격이 어떻게 되나요?", "a": "...", "photo": "data:image/...|null" }
  ]
}
```
- `biz_name`·`region` = 사업자정보 재사용. `owner_sms` = 사장님 업무폰.
- `categories` = 질문에서 뽑은 분류 순서(빈 분류는 「기타」 로 묶어 마지막).
- `questions` 순서 = `rep_questions.sortOrder`.

## 앱 (페이블 담당 · 이번에 구현)
- `AppPreferences`: `faqPublished`(토글)·`faqSlug`(발급받아 캐시)·`faqIntro`·`faqCareer`·`faqCount`·`faqCoverPhotos`(앱내 복사본 주소들). 업체명·지역은 사업자정보에서 읽음.
- **업체 소개 편집 화면**: 소개글·경력·시공건수·대표사진(여러 장, `TemplatePhotoStore`/현장사진 창고 재사용). 프로토 ② "업체 소개 편집".
- **관리 화면 확장**: 공개 on/off 토글 → `faqPublished` + publish 호출 / 고정 링크(발급 후 표시·복사·공유) / 미리보기(브라우저로 `/faq/{slug}` 열기) / 업체 소개 편집 입구 / 받은 문의(상담함으로).
- **`ai/FaqPublishRepository`**: `Net.builder()`(토큰 자동)로 `POST /faq/publish` — 공개 토글·질문 변경·업체소개 저장 시 호출. 응답 slug 캐시. 오프라인이면 우체통(`Outbox`) 재시도 고려(1차는 실패 시 화면에 말하고 다음 켤 때 재시도).
- 재사용: 사진 base64 = `ImageEncoder`(CAP) · 네트워크 = `Net.builder` · 저장실패 말하기 = `SaveGuard` · 링크 공유 = 안드로이드 공유 인텐트.

## 작업 쪼개기
- [코덱스] 서버 `faq_pages` 테이블 + `POST /faq/publish` + `GET /faq/{slug}`(프로토 1:1 HTML) — repo staged, py_compile 통과, 라이브 X.
- [페이블] 앱 AppPreferences·업체소개 편집·관리 확장·FaqPublishRepository.
- [공통] 위 publish 본문 계약을 양쪽이 지킴.
- 배포: 앱=내부배포(토글만 있고 서버 라이브 전이면 "준비 중"). 서버 라이브=폰 확인 + 「가」 후 GOTCHAS §3.
