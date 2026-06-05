# SERVER HANDOFF — 통화 정리 문자(call-summary) 톤 변경

작성: 2026-06-06 · android Claude (사장님 직접 요청으로 server/main.py 1곳 수정)

## 무엇을 바꿨나
`server/main.py` 의 `CALL_SUMMARY_SYSTEM` 프롬프트 + `_coerce_call_summary()` 컷 길이.

- 대상 엔드포인트: `POST /api/call-summary` (Haiku 4.5)
- `suggested_followup_sms` 규칙을 "짧은 초안(없으면 null)" → **"고객에게 정리해서 보내는 문자"** 로 변경.
  - 반드시 `"고객님, 통화 내용 정리드립니다."` 로 시작.
  - 줄바꿈 후 통화 원문에 **실제로 나온 내용만** 2~5줄 정리(- 로 시작), 마지막 존댓말 마무리.
  - 창작·추측 금지 강화(원문에 없는 가격/날짜/평수/부위 만들지 마라).
  - null 금지(원문에 내용 있으면 항상 채움), 320자 이내.
- `_coerce_call_summary` 의 followup 컷: 240자 → 480자 (여러 줄 정리가 잘려서 어색해지는 것 방지).

## 왜
사장님 통점: 통화 후 음성/직접 입력 → AI 정리하면 **녹음과 전혀 다른 내용**으로 나오고,
고객에게 "정리해서 보내는" 형태가 아니었음. 이제 항상 고객용 정리 문자로 나옴.

## 맥미니에서 할 일
```
cd ~/paperclip-company/workspaces/CallFollowCRM
git pull --rebase
bash server/deploy_phase1.sh   # launchctl reload
```
- 추가 의존성 없음. DB 변경 없음. (앱 측은 이미 빌드/배포 완료)
- 검증: 앱 → 통화 정리해서 보내기 → 아무 통화내용 입력 → "AI로 정리하기" →
  결과 "고객에게 보낼 문자" 가 `고객님, 통화 내용 정리드립니다.` 로 시작하는지 확인.
