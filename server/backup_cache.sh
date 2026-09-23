#!/bin/bash
# 링고 서버 DB(cache.db) 스냅샷 — 협업·현장사진·블로그·회원 앱백업이 전부 이 파일 하나에 있다.
# 2026-09-14: 백업 장치가 하나도 없다는 걸 발견하고 만듦 (타임머신 미설정, 크론 없음).
#
#  - sqlite3 .backup 을 쓴다 (cp 는 서버가 쓰는 중이면 깨진 파일이 나올 수 있음)
#  - 무결성 검사를 통과한 것만 남긴다 (깨진 백업을 '있다'고 착각하는 게 제일 위험)
#
# 2026-09-23 개정 — **조용한 실패를 없앤다.**
#   외장하드 복사가 2026-09-14 이후 9일 동안 `Operation not permitted` 로 계속 실패했는데,
#   로그에 WARN 한 줄만 남고 **아무도 안 봤다.** 그 9일 동안 사본은 맥미니 한 대뿐이었다.
#   ① 바깥 보관은 **클라우드 폴더**로 (홈 폴더 안이라 맥OS 추가 권한이 필요 없다).
#      외장하드는 뺐다 — 거긴 사장님 사진·영상이 든 작업 드라이브다. 백업 때문에
#      `/bin/bash` 에 전체 디스크 권한을 주는 건 대가가 너무 크다.
#   ② 실패하면 **슬랙으로 말한다.** 단, 매일 떠들면 아무도 안 보니까
#      "바깥 사본이 3일 넘게 안 만들어졌을 때"만 운다.

set -u
SRC="$HOME/ringgo-server/cache.db"
LOCAL_DIR="$HOME/ringgo-backups"
LOG="$LOCAL_DIR/backup.log"
STATE="$LOCAL_DIR/.last_offsite"       # 바깥 사본이 마지막으로 성공한 시각(epoch)
KEEP_LOCAL=14
KEEP_OFFSITE=30
OFFSITE_STALE_DAYS=3                   # 이만큼 바깥 사본이 없으면 슬랙으로 운다
STAMP=$(date '+%Y%m%d_%H%M')

mkdir -p "$LOCAL_DIR" || exit 1
say() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"; }

# 슬랙 — 주소는 코드에 없다. 없으면 조용히 건너뛴다.
yell() {
  local hook
  hook=$(cat "$HOME/.ringgo_alert_webhook" 2>/dev/null) || return 0
  [ -n "$hook" ] || return 0
  /usr/bin/python3 - "$hook" "$1" <<'PY' 2>/dev/null
import json, sys, urllib.request
hook, text = sys.argv[1], sys.argv[2]
body = json.dumps({"text": text}).encode("utf-8")
req = urllib.request.Request(hook, data=body,
                             headers={"Content-Type": "application/json; charset=utf-8"})
try:
    urllib.request.urlopen(req, timeout=8).read()
except Exception:
    pass
PY
}

# 바깥 보관 자리 찾기 — 구글드라이브 > 그 밖의 클라우드 > iCloud 순.
find_offsite() {
  local d
  for d in "$HOME/Library/CloudStorage/GoogleDrive-"*/"My Drive" \
           "$HOME/Library/CloudStorage/GoogleDrive-"*/"내 드라이브" \
           "$HOME/Library/CloudStorage/"*/ \
           "$HOME/Library/Mobile Documents/com~apple~CloudDocs"; do
    [ -d "$d" ] && { printf '%s' "${d%/}/ringgo-backups"; return 0; }
  done
  return 1
}

say "=== 시작 ==="

if [ ! -f "$SRC" ]; then
  say "FAIL 원본 없음: $SRC"
  yell "🚨 *서버 DB 백업 실패* — 원본이 없습니다 ($SRC)"
  exit 1
fi

TMP="$LOCAL_DIR/cache_${STAMP}.db"
# .backup = 서버가 쓰는 중에도 일관된 스냅샷을 뜬다
if ! /usr/bin/sqlite3 "$SRC" ".backup '$TMP'" 2>>"$LOG"; then
  say "FAIL 스냅샷 실패"
  yell "🚨 *서버 DB 백업 실패* — 스냅샷을 못 떴습니다. 오늘 백업 없음."
  rm -f "$TMP"; exit 1
fi

# 무결성 검사 — 통과 못 하면 버린다 (깨진 걸 백업이라 믿는 게 최악)
CHK=$(/usr/bin/sqlite3 "$TMP" "PRAGMA integrity_check;" 2>>"$LOG" | head -1)
if [ "$CHK" != "ok" ]; then
  say "FAIL 무결성 검사 실패: $CHK"
  yell "🚨 *서버 DB 백업 실패* — 뜬 백업이 깨져 있습니다 ($CHK). 오늘 백업 없음."
  rm -f "$TMP"; exit 1
fi

ROWS=$(/usr/bin/sqlite3 "$TMP" "SELECT (SELECT COUNT(*) FROM shared_sites)||'/'||(SELECT COUNT(*) FROM blog_posts)" 2>/dev/null)
gzip -f "$TMP" || { say "FAIL 압축 실패"; yell "🚨 *서버 DB 백업 실패* — 압축 실패"; exit 1; }
GZ="$TMP.gz"
SZ=$(du -h "$GZ" | cut -f1)
say "OK  로컬 $GZ ($SZ) · 협업/블로그 $ROWS"

# ── 바깥 보관 (맥미니가 통째로 죽어도 살아남게) ──
OFFSITE_OK=0
if OFF_DIR=$(find_offsite); then
  if mkdir -p "$OFF_DIR" 2>>"$LOG" && cp "$GZ" "$OFF_DIR/" 2>>"$LOG"; then
    OFFSITE_OK=1
    date +%s > "$STATE"
    say "OK  바깥 사본 → $OFF_DIR"
    ls -1t "$OFF_DIR"/cache_*.db.gz 2>/dev/null | tail -n +$((KEEP_OFFSITE+1)) | while read -r f; do rm -f "$f"; done
  else
    say "WARN 바깥 사본 실패 — $OFF_DIR"
  fi
else
  say "WARN 바깥 보관 자리 없음 (클라우드 폴더 미설치)"
fi

# 며칠째 바깥 사본이 없으면 **말한다.** 매일 떠들지 않으려고 3일 넘을 때만.
if [ "$OFFSITE_OK" -eq 0 ]; then
  LAST=$(cat "$STATE" 2>/dev/null || echo 0)
  NOW=$(date +%s)
  DAYS=$(( (NOW - LAST) / 86400 ))
  if [ "$LAST" -eq 0 ] || [ "$DAYS" -ge "$OFFSITE_STALE_DAYS" ]; then
    if [ "$LAST" -eq 0 ]; then WHEN="한 번도 없음"; else WHEN="${DAYS}일째"; fi
    yell "⚠️ *서버 DB 바깥 백업이 안 만들어지고 있습니다* ($WHEN)
맥미니 안에만 사본이 있습니다 — 이 컴퓨터가 죽으면 같이 갑니다.
맥미니에 구글드라이브(또는 다른 클라우드) 폴더를 만들어 주세요."
  fi
fi

# 오래된 로컬 정리
ls -1t "$LOCAL_DIR"/cache_*.db.gz 2>/dev/null | tail -n +$((KEEP_LOCAL+1)) | while read -r f; do rm -f "$f"; done

N_LOCAL=$(ls -1 "$LOCAL_DIR"/cache_*.db.gz 2>/dev/null | wc -l | tr -d ' ')
if [ "$OFFSITE_OK" -eq 1 ]; then
  N_OFF=$(ls -1 "$OFF_DIR"/cache_*.db.gz 2>/dev/null | wc -l | tr -d ' ')
  say "=== 종료 · 로컬 ${N_LOCAL}벌 · 바깥 ${N_OFF}벌 ==="
else
  say "=== 종료 · 로컬 ${N_LOCAL}벌 · 바깥 0벌 ==="
fi
