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
