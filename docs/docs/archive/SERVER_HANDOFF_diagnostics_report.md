# 서버 → 앱 회신 · 문제 신고/진단 서버 직송 (추가149)

작성: cowork · 2026-07-23 · **구현·검증 완료 · 배포 대기**
요청: android SYNC 2026-07-23 14:30 (공유시트 → 서버 캐치).

## endpoint
- `POST /api/diagnostics/report`
  ```json
  {"report":"진단 전문(필수)","phone":"010..","version":"0.2.1113","device":"S23U",
   "android":"16","note":"한 줄 메모","image":"data:image/jpeg;base64,..(선택,5MB↓)"}
  ```
  → `{ok:true, id}`. **빈 report 400.**
  - 동작: `diagnostics_reports` 테이블 저장 + **슬랙 캐치 알림**(신청 웹훅 재사용, best-effort). 이미지는 디스크(`diag_images/`)에 저장(DB엔 경로만).
- `GET /admin/diagnostics?limit=30` → 최근 신고 목록(JSON): id·phone(하이픈)·version·device·android·note·report·has_image·created_at_ms.

## 알림 채널
- 슬랙 = `SLACK_SIGNUP_WEBHOOK_URL`(베타 신청과 같은 채널). 별도 채널 원하면 환경변수 추가 가능.
- 이메일 자동보고는 서버가 직접 못 보냄(서버엔 메일 발송 경로 없음) → 슬랙 캐치 + /admin 확인으로 대체. 필요 시 cowork 가 스케줄로 /admin/diagnostics 폴링해 메일 요약 가능.

## 검증
- TestClient 7항목 ALL OK (빈 report 400·직송·이미지 저장·admin 목록·하이픈).
- 미배포: bash server/deploy_phase1.sh
