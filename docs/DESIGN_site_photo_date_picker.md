# 설계 — 「시공한 날 사진만」 현장사진 선택창

> 상태: **✅ 구현·S9 눈확인 완료** (2026-11-11 사장님 "지금 권한 포함해 만들자"). 내부배포 + 플레이 민감권한 신고서 필요.
> 구현: `presentation/screen/customer/SitePhotoDatePicker.kt`(선택창 + `rememberSitePhotoPickerLaunch` 컨트롤러) ·
> `data/repository/DeviceGalleryRepository.kt`(MediaStore DATE_TAKEN 쿼리) · `domain/schedule/WorkDateRange.kt`(시공일 범위·시험) ·
> `util/PermissionHelper.galleryImagePermissions/hasGalleryImages` · 진입 `CustomerDetailJobCard`(+tile) · 권한 거부 시 시스템 피커 폴백.
> 아래는 원래 설계 기록(참고).

> (원래) 상태: 설계·계획 확정, 빌드 대기 (2026-11-11 사장님 "설계·계획만 먼저, 빌드 X")
> 프로토(스펙 원본): `artifact/EBpuEujiPoUvxLdwXPsSd1` "시공날 사진 고르기" (docs/PROTOTYPES.md 등록)
> 관련 메모: project_instagram_photo_picker_deferred · project_site_photos

## 0. 한 줄 요약

현장 사진 올릴 때, **그 손님 시공한 날에 찍은 사진만** 자동으로 모아 보여주는 **앱 안 선택창**.
위 토글로 「전체 보기」. 며칠 치 사진을 일일이 안 뒤지게.

## 1. 왜 (의도)

- 사장님: "딱 원하던거야" (2026-10-28). 현장 기록할 때 시공한 날 사진을 찾느라 갤러리를 며칠 치 뒤지는 게 번거롭다.
- 시스템 사진 선택기(Photo Picker)는 **촬영 날짜로 거를 수 없다**(남의 화면) → 날짜 필터를 하려면 **앱이 사진첩을 직접 읽어야** 한다.

## 2. ⚠️ 가장 큰 벽 — 권한 + 플레이 (빌드 전 반드시)

- 날짜 필터 = `MediaStore` 의 `DATE_TAKEN` 쿼리 = **`READ_MEDIA_IMAGES` 권한 필요**(API 33+).
- 이 권한은 **2026-07-05 플레이 정책 때문에 일부러 뺐다**(`AndroidManifest.xml` 주석: "가끔 쓰는 이미지 선택은 권한 없이 Photo Picker").
- 즉 **권한 없는 버전은 불가능**(날짜 거르기가 기능의 전부이고, 그건 권한이 있어야만 된다).
- **플레이 "사진·동영상 접근" 민감권한 신고서** 필요 + 심사가 한 번 더 돌 수 있음.
  - 플레이 **시기**는 풀렸음(2026-10-28 통과). 단 신고서는 여전히 필요 → **전용 업데이트**로 올려야 안전.
  - 이 기능이 그 권한 신청의 **CRM 핵심 사유**(현장 기록)가 된다 — 인스타식 피커보다 심사 명분이 강함.

### 플레이 신고서 초안 (사진·동영상 권한 사유)

> 시공막내는 1인 인테리어/줄눈 시공 사장님용 CRM 입니다. 사장님이 **고객 현장(시공) 기록**을 남길 때,
> **그 고객을 시공한 날 촬영한 사진만** 자동으로 모아 현장 기록에 첨부할 수 있도록 사진 접근 권한을 사용합니다.
> 시스템 사진 선택기는 촬영 날짜로 사진을 거를 수 없어, 사장님이 며칠 치 사진을 일일이 찾아야 합니다.
> 날짜별 자동 필터(촬영일 기준)를 제공하려면 사진 메타데이터(촬영 시각)에 접근해야 하므로 `READ_MEDIA_IMAGES`
> 를 사용합니다. 선택한 사진은 **해당 고객의 현장 기록**에만 첨부되며, 광고·분석·외부 판매에 쓰지 않습니다
> (사장님 본인 기기와 사장님 본인 서버에만 저장). 사용자가 직접 "사진 올리기"를 누를 때만 접근합니다.

## 3. 권한 — 버전별 (매니페스트/런타임)

| Android | 권한 | 비고 |
|---|---|---|
| 33+ (13) | `READ_MEDIA_IMAGES` | 신규 추가(런타임 요청) |
| 34+ (14) | `READ_MEDIA_VISUAL_USER_SELECTED` | "선택한 사진만" 부분 허용 지원. 부분 허용이어도 **허용된 사진 안에서 날짜 필터는 됨** + "더 고르기" 안내 |
| ≤32 | `READ_EXTERNAL_STORAGE` (maxSdk=32) | **이미 매니페스트에 있음**(통화녹음 오디오용) — 이미지에도 그대로 씀. S9(API 29)는 이 경로 |

- `ACCESS_MEDIA_LOCATION` 은 **불필요**(GPS/EXIF 위치 안 씀, 촬영시각만).

## 4. 데이터 — MediaStore 날짜 쿼리

- 테이블: `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`
- projection: `_ID`, `DATE_TAKEN`(ms), `DATE_ADDED`(s)
- **시공한 날 범위** = `job.scheduledWorkDate` 의 `startOfDay` ~ `+ (scheduledWorkDays)일` 끝.
  - 공용 셈 재사용: **`domain/schedule/AsSchedule` 와 같은 결 — 시공일 범위는 `jobCoversDay`(ScheduleParts) 쪽과 한 곳으로.**
    (A/S 때 만든 `AsSchedule.coveredDayStarts` 처럼, 시공일 범위를 주는 **순수 함수 하나**를 `domain/` 에 두고
     picker·캘린더가 같이 쓴다. 두 벌 금지 §12.)
- selection: `DATE_TAKEN BETWEEN dayStartMs AND dayEndMs` (DATE_TAKEN 이 null 인 사진은 `DATE_ADDED*1000` 폴백)
- 정렬: `DATE_TAKEN DESC`
- 「전체 보기」: 범위 없이 전부 쿼리 → **날짜별로 묶어** 그린다(그날 섹션 먼저, 그 밖의 날들 뒤).
- content URI: `ContentUris.withAppendedId(EXTERNAL_CONTENT_URI, id)` → 그대로 `addSitePhotos(uris, jobId)` 에 넘김.

## 5. UI (프로토 EBpuEujiPoUvxLdwXPsSd1 1:1 — §0)

- 상단바: `✕` + 제목 **「현장 사진 올리기」**
- 맥락 배너: **손님 이름**(「OOO 고객님」) + **주소 · 업종**(회색)
- 세그먼트 토글: **「📅 시공한 날만 (M/d)」**(기본 on, 날짜 파랑) / **「전체 보기」**
- 안내(시공한 날 모드만): **「💡 M/d 시공한 날 찍은 사진만 모아봤어요 — 며칠 치 안 뒤져도 돼요.」**
- 스크롤 영역:
  - 날짜 머리: **「10월 8일 (수) · 시공한 날 · N장」** + 파란 점. 「전체 보기」면 뒤에 **「그 밖의 날들」**(회색 점) 섹션.
  - 3열 그리드, 타일 = 1:1, 좌하단 **촬영 시각**(작은 흰 글씨), 우상단 **체크 동그라미**. 선택 시 파란 테두리 + ✓.
- 하단바: **「N장 선택됨」** + **「N장 올리기」** 버튼(0장이면 disabled, 회색).
- **카메라(촬영) 없음**. **"전송" 아님** — 현장 기록에 담기만(기존 addSitePhotos 그대로).
- 기본 UX(§0): 로딩 스켈레톤, 권한 거부/빈 화면 처리(아래), 뒤로가기=닫기.

## 6. 연결 지점 (§12-K)

- **교체 자리**: `CustomerDetailJobCard.kt:548` — 지금 `PickMultipleVisualMedia(20)` → `addSitePhotos(uris, jobId)`.
  여기 "사진 올리기"를 **새 날짜필터 picker 화면**(바텀시트/풀스크린)으로 바꾼다. 고른 URI → **기존 `addSitePhotos(uris, jobId)` 그대로** 호출.
- **뒤쪽은 안 건드림**: filesDir 복사·Room(`SitePhotoRepository`)·서버 업로드(`ownerPhotoUploadManager`)·한 현장 20장 캡 = 그대로.
- 협업 현장 사진 업로드(`SharedSiteScreen.kt:121`)도 같은 picker 쓸지는 **2차**(이번 범위 = 사장님 본인 현장사진).

## 7. 엣지 케이스 (끝내기 전 자문 §13)

- **시공일이 없는 건** → 「시공한 날만」 거를 날이 없다. 기본을 **「전체 보기」**로 열고 안내 "시공일이 아직 없어요".
- **권한 거부** → **시스템 Photo Picker 로 폴백**(지금 동작 유지) + "설정 열기" 안내. 기능이 조용히 죽지 않게.
- **부분 허용(안드 14 "선택한 사진만")** → 허용된 사진만 뜸 + **"사진 더 고르기"** 버튼(시스템 재요청).
- **DATE_TAKEN 없는 사진**(스캔/다운로드) → DATE_ADDED 폴백. 그래도 없으면 「그 밖의 날들」로.
- **느린 로딩/많은 사진** → 페이징 or 썸네일 비동기(§0 스켈레톤). 메인스레드 쿼리 금지(ANR).
- **연타** → 「올리기」 누르면 바로 disable(중복 추가 방지).

## 8. 작업 쪼개기 (빌드 착수 시)

1. `domain/` 에 **시공일 범위 순수 함수**(+단위시험). (캘린더와 공용)
2. `data/` 에 **MediaStore 날짜 쿼리 repository**(권한·버전 분기·DATE_TAKEN 폴백·페이징).
3. `presentation/` 에 **picker 화면**(프로토 1:1) — 세그먼트·날짜 섹션·그리드·선택·하단바. 크게보기=기존 PhotoViewer 재사용.
4. 권한 런처(READ_MEDIA_IMAGES/USER_SELECTED/≤32) + 거부·부분허용·폴백.
5. `CustomerDetailJobCard.kt:548` 진입점 교체 → 고른 URI `addSitePhotos`.
6. 매니페스트 권한 추가 + **플레이 신고서**(§2 초안) + 전용 내부배포→심사.
7. 가드(dup/style/size/brand/rules) 통과 · 단위시험 · S9/S23U 폰 확인(실제 사진 날짜 필터 눈확인).

## 9. 안 할 것

- 촬영(카메라). 고객 "전송". 위치(EXIF). 협업 업로드(2차). 영상.
