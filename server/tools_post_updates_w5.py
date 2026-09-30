# -*- coding: utf-8 -*-
"""홈페이지 /updates — 9월 다섯째 주(9/28~10/1). (2026-10-01 사장님 "ㄱㄱ")

  app_updates 는 붙이기만 하는 표라 넣은 행 id 만 알면 그대로 지울 수 있다.
  서버 코드는 안 건드린다 → 재시작 없음. /updates 는 요청마다 새로 그린다.

  ⚠️ 읽는 사람은 **다른 사장님들**이다. 우리가 뭘 고쳤는지가 아니라
     **당신 앱에서 뭐가 달라졌는지**로 적는다. 개발 용어는 쓰지 않는다.
"""
import datetime
import sqlite3
import sys

DB = "cache.db"

# (kind, text) — kind: new(✨) | fix(🔧) | imp(⚡)
WEEK5 = [
    ("fix", "통화 전문에 「네.」가 여러 번 반복되던 것 — 조용한 구간을 잘못 받아쓰던 문제를 고쳤습니다"),
    ("imp", "받아쓰기가 우리 말을 알아듣습니다 — 「백시멘트」 「카라폭시」 같은 단어가 제대로 적힙니다"),
    ("imp", "「내 기록」을 열면 지도와 이번 달 숫자가 먼저 — 할 일은 그 현장 줄 옆으로 옮겼습니다"),
    ("imp", "영상 길이가 다닌 곳 수에 맞춰 10~15초로 — 많이 다닌 달은 더 천천히 달립니다"),
    ("fix", "영상이 흔들려 보이던 것 — 한 현장에서 다음 현장까지는 지도를 세워두고 트럭만 움직입니다"),
    ("imp", "현장 사진을 더 선명하게 올립니다 — 줄눈 선이 뭉개지지 않게 화질을 올렸습니다"),
    ("new", "접수서 비고를 보던 그 자리에서 바로 고칩니다 — 다시 만들 필요 없이"),
    ("new", "인증문자가 안 가면 그 자리에서 알려드립니다 — 이유와 함께, [가입 오류 신고] 한 번으로"),
    ("new", "끝난 현장은 잔금까지 한 번에 — 「다 받으셨나요?」에 답하면 완료와 입금이 같이 기록됩니다"),
    ("new", "내가 건 전화에도 손님 카드가 뜹니다 — 통화하면서 주소·잔금·메모를 봅니다"),
    ("new", "협업 요청에 「그날 되나?」가 맨 위에 — 내 일정과 겹치는지 먼저 알려드립니다"),
    ("new", "협업 요청에 그 사장님과 함께한 이력 — 몇 번, 어느 달에 했는지"),
    ("fix", "전해둘 말이 한 줄로 잘리던 것 — 현관 비번이 끊기면 안 알려준 것과 같습니다"),
    ("fix", "같은 현장이 일정에 두 번 뜨던 것"),
    ("fix", "9월을 보는데 1년치 숫자가 섞여 있던 것"),
    ("new", "사진을 크게 보면서 돌릴 수 있습니다 — 닫으면 그대로 저장됩니다"),
]


def ms_kst(y, m, d, h=12):
    """그 날짜 KST 정오의 epoch ms. 서버가 utcfromtimestamp + 9h 로 읽으므로 그대로 맞춘다."""
    dt = datetime.datetime(y, m, d, h) - datetime.timedelta(hours=9)
    return int(dt.replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)


con = sqlite3.connect(DB)
cur = con.cursor()
have = {r[0] for r in cur.execute("SELECT text FROM app_updates").fetchall()}

base = ms_kst(2026, 10, 1)
added = []
for i, (kind, text) in enumerate(WEEK5):
    if text in have:            # 두 번 돌려도 두 번 안 들어가게
        print("skip:", text[:30])
        continue
    cur.execute(
        "INSERT INTO app_updates (kind, text, created_at_ms) VALUES (?,?,?)",
        (kind, text, base - i * 60_000))
    added.append(cur.lastrowid)
con.commit()

print("넣은 id:", added)
print("되돌리려면: DELETE FROM app_updates WHERE id IN (%s);" % ",".join(map(str, added)))
rows = cur.execute(
    "SELECT kind, text, created_at_ms FROM app_updates ORDER BY created_at_ms DESC LIMIT 4").fetchall()
for k, t, ms in rows:
    d = datetime.datetime.utcfromtimestamp(ms / 1000) + datetime.timedelta(hours=9)
    print(" ", d.strftime("%Y-%m-%d"), k, t[:44])
con.close()
