# 서버 → 앱 회신 · 협업 월별 집계 (구현 완료)

작성: cowork · 2026-08-01 · **구현·검증 완료 · 배포 대기**
요청: docs/SERVER_HANDOFF_collab_monthly.md (android).

## endpoint (계약서 필드·모양 그대로)
`GET /api/shared/monthly?phone=<owner11>&ym=<YYYY-MM>`
- ym 생략 → 데이터 있는 최신 달. 응답 = `{ym, available_months[], received{}, given{}}`.
- received/given = `{count, total_wage, paid_total, partners:[{partner_phone,partner_name,count,total_wage,paid_total,last_at_ms,sites:[{share_id,at_ms,title,wage,paid}]}]}`.
- 집계: `status='accepted'` 만(거절/취소 제외). 달 기준 = `scheduled_at_ms`(없으면 `created_at_ms`) 의 KST 월.
- received = 내가 partner(B) → partner_name = 나를 부른 A 상호(owner_name_raw→가입디렉터리→"사장님").
- given = 내가 owner(A) → partner_name = 내가 부른 B 상호(partner_name_raw→디렉터리→"협업자").
- wage = daily_wage(만원). paid = paid_at_ms 있으면 true. total_wage=전체 accepted 합, paid_total=입금건 합.
- available_months = 양방향 데이터 있는 달만 최신순. sites 는 at_ms 오름차순.
- §B-2 인증 켜지면: 요청 phone==토큰 phone (미들웨어가 이미 /api/shared 보호). 
- 검증 TestClient 13 ALL OK (월필터·양방향·입금·거절제외·이름해석·빈응답).

## 인증문자 발신전용 (같이 반영)
- request-code SMS 본문: `[시공막내] 인증번호 [123456] (5분 이내 입력)\n사장님의 막내 비서...\n※ 발신전용 — 통화·회신 불가. 문의는 앱에서.`
- 앱 자동읽기 호환: "인증번호" 키워드 유지 + 6자리 코드가 맨 앞(발신전용 문구엔 6자리 숫자 없음). LMS 로 나감(발신전용 안내 우선).
- ⚠️ SOLAPI 발신번호(SENDER=01039690479) 변경은 plist env — 사장님/배포측에서.

- 미배포: bash server/deploy_phase1.sh
