# -*- coding: utf-8 -*-
"""홈페이지 /updates 밀린 소식 채우기 — 9월 셋째 주·넷째 주. (2026-09-27 사장님 "일단 올려")

  app_updates 는 붙이기만 하는 표라서, 넣은 행 id 만 알면 그대로 되돌릴 수 있다.
  서버 코드는 안 건드린다 → 재시작 없음. /updates 는 요청마다 새로 그린다.
  주차 라벨은 created_at_ms 로 정해지므로(KST 기준 날짜), 주마다 다른 시각을 준다.
"""
import datetime
import sqlite3

DB = "cache.db"

# (kind, text) — kind: new(✨) | fix(🔧) | imp(⚡)
WEEK3 = [
    ("new", "앱 전체를 한 모양으로 — 글자 크기·색·모서리를 규칙 하나로 통일했습니다"),
    ("new", "폰마다 다르게 보이던 그림을 앱이 직접 그린 아이콘으로 바꿨습니다"),
    ("imp", "일정 달력 개편 — 날짜는 작게, 그날 띠가 주인공. 칸에 지역명이 보입니다"),
    ("new", "그날 카드에 주소가 크게 + [길찾기] [전화] 한 번에"),
    ("fix", "상담함 빨간 숫자를 누르면 그 숫자가 가리키는 것만 보여줍니다"),
    ("fix", "고른 날이 비어 있으면 「앞으로의 일정」을 대신 보여줍니다"),
    ("new", "현장 사진을 [고르기] 한 번으로, 두 줄 넘으면 접힙니다"),
    ("fix", "흰 창이 회색으로 보이던 것 · 날짜 창 버튼이 잘리던 것"),
]

WEEK4 = [
    ("new", "「내 기록」 — 이번 달 다닌 현장이 지도에 그려지고 트럭이 길을 따라 달립니다"),
    ("new", "인증샷·영상 — 다녀온 현장을 한 장, 한 편으로 만들어 바로 올립니다"),
    ("new", "동네까지 셉니다 — 전국 행정동 3,520곳 기준으로 어디를 다녔는지"),
    ("imp", "통화 요약이 시간 구간으로 — 「0:00-0:35」 시각을 누르면 그 대목부터 들립니다"),
    ("new", "전화가 오면 그 손님 일정·돈·통화 요약이 화면에 바로"),
    ("new", "070·1588 은 「광고 전화로 의심」, 02·031 은 「집·사무실 전화」로 구분합니다"),
    ("new", "통화하면서 적은 메모가, 끊으면 그 손님 메모로 들어갑니다"),
    ("imp", "고객 메모가 쪽지로 — 언제 적었는지 시각이 붙고 중요한 건 위에 고정"),
    ("new", "같이 할 사장님을 이름으로 고릅니다 — 번호를 외울 필요 없이"),
    ("new", "협업 수락 화면 — 어디·언제·내 일당이 한 장에"),
    ("new", "A/S 명단 — 날짜를 못 잡아도 잊지 않게 올려둡니다"),
    ("new", "보낸 문서에 시공일이 맨 앞 — 「9월 28일 시공접수서」"),
    ("fix", "손님이 쓴 접수서는 고치거나 지울 수 없습니다 — 나중에 볼 기록이라서"),
    ("fix", "갈라진 손님 합치기 — 번호 형식이 달라 둘로 나뉜 기록을 하나로"),
    ("fix", "18분 통화가 「부재중 콜백」으로 요약되던 것"),
    ("fix", "1차 시공이 없는데 2차 날짜가 잡히던 것"),
]


def ms_kst(y, m, d, h=12):
    """그 날짜 KST 정오의 epoch ms. 서버가 utcfromtimestamp + 9h 로 읽으므로 그대로 맞춘다."""
    dt = datetime.datetime(y, m, d, h) - datetime.timedelta(hours=9)
    return int(dt.replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)


con = sqlite3.connect(DB)
cur = con.cursor()
have = {r[0] for r in cur.execute("SELECT text FROM app_updates").fetchall()}

added = []
for week, base in ((WEEK4, ms_kst(2026, 9, 26)), (WEEK3, ms_kst(2026, 9, 18))):
    # 화면은 최신이 위 → 목록 첫 줄이 가장 큰 시각을 갖게 1분씩 내린다
    for i, (kind, text) in enumerate(week):
        if text in have:            # 두 번 눌러도 두 번 안 들어가게
            print("skip:", text[:30])
            continue
        ms = base - i * 60_000
        cur.execute(
            "INSERT INTO app_updates (kind, text, created_at_ms) VALUES (?,?,?)",
            (kind, text, ms))
        added.append(cur.lastrowid)
con.commit()

print("added ids:", added)
rows = cur.execute(
    "SELECT kind, text, created_at_ms FROM app_updates ORDER BY created_at_ms DESC LIMIT 5").fetchall()
for k, t, ms in rows:
    d = datetime.datetime.utcfromtimestamp(ms / 1000) + datetime.timedelta(hours=9)
    print(d.strftime("%Y-%m-%d"), k, t[:40])
con.close()
