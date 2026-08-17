# 서버 핸드오프 — 현장 사진 팀↔사장님 공유 (2026-06-04, android→cowork)

## 배경
사장님 요청: "팀원도 그 현장에 사진 올리고, 나(사장님)도 그 현장에 올려서, 고객 카드에서 같이 본다."

앱(안드로이드) 측은 **로컬 전용**(사장님 폰에만 저장, 고객별 site_photos 테이블)으로 우선 활성화함(commit 별도). 하지만 **팀원↔사장님 공유**는 서버 보강이 필요해 이 문서로 넘김.

## 현재 서버 상태 (main.py 확인)
- `team_site_photos` 테이블 존재: `photo_id, token, member_id, owner_phone, label, image_data_url, image_path, note, uploaded_at_ms`.
- 업로드: 팀원 토큰으로만 (`TeamPhotoUploadRequest{token,label,image_data_url,note}` → `/api/team/...`). member 의 link token 검증.
- 조회: `GET /api/team/photos?owner_phone=&member_id=&since_ms=&limit=` — owner 전체(또는 member별).

## 빠진 것 (요청)
1. **고객(현장) 연결이 없음** — 사진이 owner_phone·member_id 로만 묶여, "이 고객 현장 사진"으로 못 보여줌.
   → `team_site_photos` 에 **고객 식별 컬럼** 추가 권장: `customer_phone TEXT`(또는 끝 8자리 suffix). 앱은 고객을 전화번호로 식별함.
2. **사장님(owner) 업로드 통로가 없음** — 팀원 토큰 없이 사장님이 직접 올리는 엔드포인트 필요.
   → `POST /api/site-photo/owner-upload` 신설 제안: `{owner_phone, customer_phone, label?, image_data_url, note?}` → team_site_photos 에 member_id=NULL(또는 'OWNER')로 INSERT.
3. **팀원 업로드 시 고객 연결** — 팀원은 배정된 일정(schedule snapshot)에 고객이 있음. 업로드 req 에 `customer_phone`(또는 job 식별자)을 실어 보내거나, 토큰의 schedule_snapshot 에서 서버가 매핑.
4. **고객별 조회** — `GET /api/site-photos?owner_phone=&customer_phone=&since_ms=&limit=` → 그 고객의 사진(팀원+사장님 모두), 각 row 에 업로더 표시(member 이름 or '사장님') + label + uploaded_at.

## 앱이 연결할 계획 (서버 준비되면)
- `SitePhotoRepository`(현재 로컬 Room)를 서버 동기화 추가:
  - 사장님 업로드 → owner-upload 호출 + 로컬 캐시.
  - 고객 상세 현장사진 카드 = `GET /api/site-photos?customer_phone=` 결과(팀원 사진 포함) + 로컬 미동기화분 머지.
  - 팀원 사진은 프로토대로 **파란 이름표**(업로더 name) 표시.
- 로컬 기존 사진 마이그레이션 전략은 서버 스펙 확정 후 협의.

## 참고 (앱 측 현재 구현)
- Room `site_photos(id, customerId, filePath, label?, createdAt)` — DB v27. 파일은 filesDir/site_photos/ 복사.
- UI: CustomerDetailScreen "📷 현장 사진" 카드 — 갤러리 PickMultipleVisualMedia 로 올림, 3열 그리드, 탭=풀스크린, ✕=삭제.
- 업로드 라벨(시공 전/중/후)은 아직 미사용(컬럼만 둠) — 서버 스펙에 맞춰 추가 가능.

## 보안/주의
- image_data_url base64 1MB 컷(기존 서버 정책) 유지. 큰 사진은 image_path(디스크) Phase B.
- owner-upload 도 owner_phone 검증/rate limit 권장(기존 패턴).
