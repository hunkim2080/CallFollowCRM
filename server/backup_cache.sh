#!/bin/bash
# 링고 서버 DB(cache.db) 스냅샷 — 협업 70건·현장사진·블로그 106편이 전부 이 파일에 있다.
# 2026-09-14: 백업 장치가 하나도 없다는 걸 발견하고 만듦 (타임머신 미설정, 크론 없음).
#
#  - sqlite3 .backup 을 쓴다 (cp 는 서버가 쓰는 중이면 깨진 파일이 나올 수 있음)
#  - 로컬 14벌 + 외장하드 30벌 보관
#  - 무결성 검사를 통과한 것만 남긴다 (깨진 백업을 '있다'고 착각하는 게 제일 위험)

set -u
SRC="$HOME/ringgo-server/cache.db"
LOCAL_DIR="$HOME/ringgo-backups"
EXT_DIR="/Volumes/1T 외장하드(T7)/ringgo-backups"
LOG="$LOCAL_DIR/backup.log"
KEEP_LOCAL=14
KEEP_EXT=30
STAMP=$(date '+%Y%m%d_%H%M')

mkdir -p "$LOCAL_DIR" || exit 1
say() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"; }

say "=== 시작 ==="

if [ ! -f "$SRC" ]; then say "FAIL 원본 없음: $SRC"; exit 1; fi

TMP="$LOCAL_DIR/cache_${STAMP}.db"
# .backup = 서버가 쓰는 중에도 일관된 스냅샷을 뜬다
if ! /usr/bin/sqlite3 "$SRC" ".backup '$TMP'" 2>>"$LOG"; then
  say "FAIL 스냅샷 실패"; rm -f "$TMP"; exit 1
fi

# 무결성 검사 — 통과 못 하면 버린다 (깨진 걸 백업이라 믿는 게 최악)
CHK=$(/usr/bin/sqlite3 "$TMP" "PRAGMA integrity_check;" 2>>"$LOG" | head -1)
if [ "$CHK" != "ok" ]; then
  say "FAIL 무결성 검사 실패: $CHK"; rm -f "$TMP"; exit 1
fi

ROWS=$(/usr/bin/sqlite3 "$TMP" "SELECT (SELECT COUNT(*) FROM shared_sites)||'/'||(SELECT COUNT(*) FROM blog_posts)" 2>/dev/null)
gzip -f "$TMP" || { say "FAIL 압축 실패"; exit 1; }
GZ="$TMP.gz"
SZ=$(du -h "$GZ" | cut -f1)
say "OK  로컬 $GZ ($SZ) · 협업/블로그 $ROWS"

# 외장하드 2차 보관 — 맥미니 디스크가 통째로 죽어도 살아남게
if [ -d "/Volumes/1T 외장하드(T7)" ]; then
  mkdir -p "$EXT_DIR" 2>/dev/null
  if cp "$GZ" "$EXT_DIR/" 2>>"$LOG"; then
    say "OK  외장하드 복사 완료"
    ls -1t "$EXT_DIR"/cache_*.db.gz 2>/dev/null | tail -n +$((KEEP_EXT+1)) | while read -r f; do rm -f "$f"; done
  else
    say "WARN 외장하드 복사 실패 — 로컬 백업은 남아 있음"
  fi
else
  say "WARN 외장하드가 연결돼 있지 않음 — 로컬 백업만 남김"
fi

# 오래된 로컬 정리
ls -1t "$LOCAL_DIR"/cache_*.db.gz 2>/dev/null | tail -n +$((KEEP_LOCAL+1)) | while read -r f; do rm -f "$f"; done

N_LOCAL=$(ls -1 "$LOCAL_DIR"/cache_*.db.gz 2>/dev/null | wc -l | tr -d ' ')
N_EXT=$(ls -1 "$EXT_DIR"/cache_*.db.gz 2>/dev/null | wc -l | tr -d ' ')
say "=== 종료 · 로컬 ${N_LOCAL}벌 · 외장 ${N_EXT}벌 ==="
