#!/usr/bin/env bash
# 템플릿 한글 깨짐(mojibake) 어디서 생기는지 단계별로 집어내는 진단.
# 사용: bash ~/paperclip-company/workspaces/CallFollowCRM/diag_template_encoding.sh
# 결과 → _diag_template.txt (cowork 가 읽음). 아무것도 안 바꿈(읽기 전용).

REPO="$HOME/paperclip-company/workspaces/CallFollowCRM"
SRV="$HOME/ringgo-server"
OUT="$REPO/_diag_template.txt"
{
  echo "===== 템플릿 인코딩 진단 · $(date '+%Y-%m-%d %H:%M:%S') ====="
  echo
  echo "----- [0] 로케일 (C/POSIX 면 인코딩 사고 잦음) -----"
  locale 2>/dev/null | grep -E 'LANG|LC_ALL|LC_CTYPE' || echo "(locale 없음)"
  echo "python 기본 인코딩: $(cd "$SRV" && ./venv/bin/python -c 'import sys;print(sys.getdefaultencoding(), sys.getfilesystemencoding())' 2>/dev/null)"
  echo
  echo "----- [1] origin 원본(git) 에 한글 정상? -----"
  n=$(git -C "$REPO" show origin/main:server/main.py 2>/dev/null | grep -c '현관바닥')
  echo "origin main.py 의 '현관바닥' 매칭 수 = $n  (1 이면 원본 정상)"
  echo
  echo "----- [2] 로컬 체크아웃(작업트리) 에 한글 정상? -----"
  echo "작업트리 main.py '현관바닥' = $(grep -c '현관바닥' "$REPO/server/main.py" 2>/dev/null)"
  echo "로컬 HEAD: $(git -C "$REPO" rev-parse --short HEAD 2>/dev/null) / origin: $(git -C "$REPO" rev-parse --short origin/main 2>/dev/null)"
  echo "git status(요약):"; git -C "$REPO" status --porcelain 2>/dev/null | head -5
  echo
  echo "----- [3] 배포된 파일(~/ringgo-server/main.py) 에 한글 정상? -----"
  echo "배포 main.py '현관바닥' = $(grep -c '현관바닥' "$SRV/main.py" 2>/dev/null)"
  echo "origin vs 배포본 동일? $( [ "$(git -C "$REPO" show origin/main:server/main.py 2>/dev/null | md5)" = "$(md5 -q "$SRV/main.py" 2>/dev/null)" ] && echo 동일 || echo 다름 )"
  echo
  echo "----- [4] 살아있는 서버 응답에 lone surrogate/한자? -----"
  curl -s http://localhost:8000/api/expo/template/julnun 2>/dev/null | \
    "$SRV/venv/bin/python" -c '
import sys,json
raw=sys.stdin.buffer.read()
try:
    j=json.loads(raw.decode("utf-8"))
    d=json.dumps(j,ensure_ascii=False)
    bad=[hex(ord(ch)) for ch in d if 0xD800<=ord(ch)<=0xDFFF]
    cjk=[ch for ch in d if 0x4E00<=ord(ch)<=0x9FFF]
    print("live name =", j["template"]["name"])
    print("live 첫 항목 =", j["template"]["sections"][0]["items"][0])
    print("live lone surrogate 개수 =", len(bad), bad[:6])
    print("live 한자 개수 =", len(cjk), cjk[:6])
except Exception as e:
    print("live 파싱 실패:", e)
' 2>/dev/null || echo "(서버 응답 없음/파이썬 실패)"
  echo
  echo "===== 판독 ====="
  echo "[1]=1 & [3]=1 & live 정상  → 이미 정상(캐시/앱쪽 문제)"
  echo "[1]=1 이지만 [2] 또는 [3]=0/깨짐  → git pull 안 됨/배포본 낡음 → 아래 복구"
  echo "복구: cd $REPO && git stash && git pull --rebase && bash server/deploy_phase1.sh"
} > "$OUT" 2>&1
echo "완료 → $OUT. cowork 에게 '템플릿 진단 결과 봐줘' 하세요."
