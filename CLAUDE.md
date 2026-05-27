# RING-GO monorepo — Claude 작업 룰

이 repo 는 RING-GO 앱(`app/`) + 서버(`server/`) 가 같이 있는 monorepo 입니다.
어떤 종류의 Claude (Code / Cowork) 든 이 repo 에서 일할 때 반드시 이 룰을 따른다.

---

## 1. 작업 분담

| 폴더 | 누가 만지나 | 머신 |
|---|---|---|
| `app/` (안드로이드, Kotlin) | 컴퓨터 1 데스크탑의 Claude Code | 사장님 데스크탑 |
| `server/` (FastAPI, Python) | Mac mini 의 Cowork (또는 Claude Code) | Mac mini |
| `docs/SYNC.md` | 양쪽이 공유 (작업 시작/종료 시 필수) | (git 으로 sync) |
| `pricing.md`, `*.md` 사양서 | 사장님이 정함, 양쪽은 참고만 | (git 으로 sync) |

**영역 침범 금지** — 안드로이드 Claude 는 `server/` 안 만짐. 서버 Claude 는 `app/` 안 만짐.

---

## 2. 모든 Claude 의 필수 워크플로우

### 작업 시작 전 (예외 없음)
```bash
cd ~/paperclip-company/workspaces/CallFollowCRM
git pull --rebase
tail -100 docs/SYNC.md   # 다른 쪽이 뭐 했나 확인
```

### 작업 끝낸 후 (예외 없음)
1. `docs/SYNC.md` 끝에 한 블록 append:
   ```
   ## YYYY-MM-DD HH:MM · [server|android|cowork]
   <한 줄 요약>
   - 변경: <영향 받는 다른 쪽 인터페이스 / API / UI / 데이터 모델>
   - commit: <hash>
   - 다음 액션 (있으면): <상대편이 해야 할 일>
   ```
2. `git add . && git commit -m "<요약>" && git push`

---

## 3. 사양서 위치

- `RINGGO_BACKEND_BRIEF.md` — 서버 API 전체 요약
- `RINGGO_SERVER_SPEC.md` — 서버 기능 명세
- `RINGGO_SERVER_PHASE1_UPGRADE.md` — Phase 1 (Claude Sonnet 도입)
- `RINGGO_SERVER_P0P1P2_UPGRADE.md` — §11/§12/§13 등 우선순위별 작업
- `ROADMAP.md` — 전체 로드맵
- `NEXT_SESSION_TODO.md` — 다음에 할 일

---

## 4. 현재 서버 상태 (직전 cowork 작업 기준)

- 모델: `claude-sonnet-4-6` (사장님 다운그레이드 X)
- 단가 dict (`MODEL_PRICING_USD_PER_M`): sonnet / opus / haiku 다 박힘 → 추후 모델 전환 시 비용 계산 자동
- DB: SQLite `cache.db` (suggestions_cache / api_usage / summary_cache / llm_usage_log)
- 운영: launchd `com.detailline.ringgo-server`, port 8000, 0.0.0.0
- 네트워크: Tailnet IP 100.86.114.49
- 대시보드: `http://100.86.114.49:8000/admin` (HTML, 모바일 친화)
- 자동 보고: Gmail "RING-GO" 라벨 (Label_2) 자동 부착, Zapier MCP `gmail/message` 액션

---

## 5. 자동 보고 메일 (Cowork 만 — Claude Code 는 안 함)

RING-GO 관련 작업이 끝난 시점에 Cowork 는 자동으로 hugman2080@gmail.com 으로 작업 완료 보고 메일 발송.

- 도구: Zapier MCP — `mcp__9a569859-40c2-4e1e-94bd-1873c991cc6d__execute_zapier_write_action`
- app: `gmail`, action: `message`
- 수신: `hugman2080@gmail.com`
- 라벨: `Label_2` (RING-GO)
- 전문 룰 + 본문 템플릿: `docs/AUTO_REPORT_RULE.md`

---

## 6. 보안

- `com.detailline.ringgo-server.plist` 는 절대 git 에 안 올라감 (`.gitignore` 등록됨, CLAUDE_API_KEY 환경변수 박혀있음)
- `cache.db`, `*.log`, `venv/`, `__pycache__/` 도 `.gitignore`
- GitHub PAT 는 git remote URL 에 평문으로 박혀있으면 안 됨 (Keychain 또는 gh CLI 권장)

---

## 7. 검증 / 배포

- 배포 스크립트: `server/deploy_phase1.sh` (sync + launchctl unload/load + §8 + §12.5 자동 채점)
- 검증 스크립트: `server/test_p0p1p2.sh`, `server/test_section12.sh`
- 사장님이 보통 한 줄로: `bash server/deploy_phase1.sh`

---

## 8. 의문점 발생 시

- 작업 도중 의문 생기면 **즉시 사장님께 묻기** (마음대로 추측 X)
- 양쪽 Claude 사이 spec 충돌 의심되면 `docs/SYNC.md` 에 "의문" 블록 append 후 사장님 알림
