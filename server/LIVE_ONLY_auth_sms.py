# -*- coding: utf-8 -*-
# ⚠️ 이 파일은 **읽기용 사본**입니다. 아무도 import 하지 않습니다.
#
# 왜 있나 (2026-09-30)
#   아래 코드는 **라이브 서버(~/ringgo-server/main.py)에만 있고 git 에는 없었습니다.**
#   「인증문자가 진짜 갔는지 통신사에 물어보고, 못 갔으면 알리는」 기능 전부가 그랬습니다.
#   같은 날 whisper_worker.py 가 낡은 사본에 덮여 되돌아간 사고가 있었는데,
#   이 코드는 **git 에 아예 없어서 그렇게 되면 되살릴 방법조차 없었습니다.**
#   그래서 통째로 여기 떠 둡니다.
#
#   ⚠️ server/main.py 에 아직 **합치지 않았습니다.** 라이브 main.py 는 origin 보다 193커밋 뒤라
#      주변 코드가 달라, 검증 없이 옮기면 오히려 깨집니다. 합칠 때 이 파일을 원본으로 쓰세요.
#
#   라이브 기준: main.py.bak-20260930-124925-smsresult 직후 (2026-09-30 12:49 배포분)

_SMS_DEAD_CODES = {"3058", "3032"}
# 없는 번호로 판명된 뒤 막아두는 기간. 통신사 일시 오류일 가능성을 남겨 영구는 아니다.
AUTH_DEAD_BLOCK_DAYS = 3
# 🛑 **한 번도 인증이 안 된 번호**에 이만큼 보냈으면 그만 보낸다.
#   진짜 가입하려는 사람은 몇 번 안에 끝낸다. 10번을 보내도 한 번도 안 됐다면
#   그건 그 번호 주인이 원한 문자가 아니다. (실제: 남의 번호로 98번 나갔다)
AUTH_UNVERIFIED_TOTAL_CAP = 10

# 이통사가 「못 갔다」고 알려주는 코드들. 접수(2000·3000·4000)와 구분한다.
_SMS_FAIL_REASON = {
    "3058": "없는 번호예요 (전송경로 없음)",
    "3032": "없는 번호예요 (미가입자)",
    "3050": "문자를 못 받는 단말기예요",
    "3043": "폰이 꺼져 있었어요",
    "3040": "시간 초과로 못 갔어요",
    "3056": "통신망 문제로 못 갔어요",
}


# 📵 추가98 (2026-09-30) — **쓰는 분께 할 말.** 이유마다 **다음에 뭘 누를지가 다르다.**
#   「안 갔어요」 한마디로는 뭘 해야 할지 모른다. (사장님 2026-09-30)
#   act: rephone = 번호 다시 입력 / retry = 다시 받기 / report = 가입 오류 신고
_SMS_FAIL_SAY = {
    "3058": ("rephone", "이 번호로는 문자가 안 가요.\n번호를 한 번만 다시 봐주세요."),
    "3032": ("rephone", "이 번호로는 문자가 안 가요.\n번호를 한 번만 다시 봐주세요."),
    "3050": ("report", "이 폰이 지금 문자를 못 받는 상태예요.\n번호는 맞아요."),
    "3043": ("retry", "폰이 꺼져 있어서 못 갔어요.\n켜고 다시 받아보세요."),
    "3040": ("retry", "잠깐 통신망이 막혔어요.\n다시 받으면 올 거예요."),
    "3056": ("retry", "잠깐 통신망이 막혔어요.\n다시 받으면 올 거예요."),
}
# 앱이 물어보러 오는 동안만 답한다. 오래된 건 「모름」 — 지난 통화 결과를 지금 것으로 착각하면 안 된다.
AUTH_SMS_RESULT_TTL_MS = 10 * 60 * 1000


def _auth_sms_result_table(con) -> None:
    """📵 통신사 회신을 적어두는 칸. 없으면 만든다(라이브 DB 에 ALTER 안 친다)."""
    con.execute(
        """CREATE TABLE IF NOT EXISTS auth_sms_result (
               phone     TEXT PRIMARY KEY,
               code      TEXT,
               reason    TEXT,
               at_ms     INTEGER,
               day       TEXT,
               day_hits  INTEGER DEFAULT 0,
               alert_day TEXT
           )"""
    )


async def _auth_sms_watch(phone: str, message_id: str) -> None:
    """📣 **보냈다고 끝이 아니다.**

    SOLAPI 는 접수되면 바로 200 을 준다. 진짜 성공/실패는 **이통사 리포트**로
    몇 초~몇 분 뒤에 온다. 전엔 그 뒷얘기를 아무도 안 들어서, 앱은 "보냈어요"
    라고 하고 사장님은 영영 몰랐다. → 45초 뒤 한 번 물어보고, 못 갔으면 알린다.
    """
    if not message_id:
        return
    try:
        await asyncio.sleep(45)
        url = "https://api.solapi.com/messages/v4/list?messageId=" + message_id
        async with httpx.AsyncClient(timeout=12.0) as client:
            r = await client.get(url, headers=_solapi_headers())
        if r.status_code >= 400:
            return
        m = (r.json().get("messageList") or {}).get(message_id) or {}
        code = str(m.get("statusCode") or "")
        if code in _SMS_FAIL_REASON:
            why = _SMS_FAIL_REASON[code]
            print(f"[auth/sms] 미도달 {phone} {code} {why}")
            # 🚫 **없는 번호면 적어둔다** — 다음부터 문자를 안 보낸다.
            #   전원 꺼짐·시간 초과·망 장애는 번호 탓이 아니라 기록하지 않는다.
            dead = code in _SMS_DEAD_CODES
            now = _now_ms()
            today = _dt.datetime.fromtimestamp(now / 1000, tz=_KST).strftime("%Y-%m-%d")
            # 📵 추가98 — **적어둔다.** 앱이 50초쯤에 물어보러 온다.
            #   같이 「오늘 몇 번째인가」도 센다 — 슬랙에 한 줄로 합쳐 알리려고.
            day_hits = 1
            alerted_today = False
            try:
                with db_conn() as con:
                    _auth_sms_result_table(con)
                    prev = con.execute(
                        "SELECT day, day_hits, alert_day FROM auth_sms_result WHERE phone = ?",
                        (phone,),
                    ).fetchone()
                    if prev and prev[0] == today:
                        day_hits = int(prev[1] or 0) + 1
                        alerted_today = (prev[2] == today)
                    con.execute(
                        """INSERT INTO auth_sms_result (phone, code, reason, at_ms, day, day_hits, alert_day)
                           VALUES (?, ?, ?, ?, ?, ?, ?)
                           ON CONFLICT(phone) DO UPDATE SET
                               code = excluded.code, reason = excluded.reason,
                               at_ms = excluded.at_ms, day = excluded.day,
                               day_hits = excluded.day_hits, alert_day = excluded.alert_day""",
                        (phone, code, why, now, today, day_hits, today),
                    )
                    con.commit()
            except Exception as _e:  # noqa: BLE001 — 기록이 본 기능을 막으면 안 된다
                print(f"[auth/sms] 기록 실패(무시): {type(_e).__name__}: {_e}")
            if dead:
                with db_conn() as con:
                    # (dedup 판단은 위 auth_sms_result 로 옮겼다 — 전엔 **「없는 번호」 코드에만**
                    #  걸려 있어서 3050 같은 건 누를 때마다 슬랙에 떴다. 사장님 스샷의 중복이 이것.)
                    con.execute(
                        "SELECT alert_day FROM auth_dead_numbers WHERE phone = ?", (phone,)
                    ).fetchone()
                    con.execute(
                        """INSERT INTO auth_dead_numbers
                               (phone, code, reason, first_ms, last_ms, hits, alert_day)
                           VALUES (?, ?, ?, ?, ?, 1, ?)
                           ON CONFLICT(phone) DO UPDATE SET
                               code = excluded.code, reason = excluded.reason,
                               last_ms = excluded.last_ms, hits = hits + 1,
                               alert_day = excluded.alert_day""",
                        (phone, code, why, now, now, today),
                    )
                    con.commit()
            # 같은 번호로 하루 종일 같은 알림이 오면 사장님 슬랙이 도배된다. 하루 한 번만.
            if not alerted_today:
                _told = "• 이 분 앱에도 **그 자리에서 알려줬습니다**.\n" if code in _SMS_FAIL_SAY else ""
                _nth = f"• 오늘 **{day_hits}번째** 시도입니다.\n" if day_hits > 1 else ""
                await _slack_post(
                    "📵 *인증문자가 안 갔어요*\n"
                    f"• 번호: `{_fmt_phone(phone)}`\n"
                    f"• 이유: {why} (코드 {code})\n"
                    + _nth
                    + ("• 이 번호는 **3일간 발송을 막습니다**(같은 번호로 계속 새는 걸 끊으려고).\n"
                       if dead else "")
                    + _told
                    + "• 이 분은 지금 가입을 못 하고 있습니다. 연락해 보세요."
                )
    except Exception as e:  # noqa: BLE001 — 알림이 본 기능을 막으면 안 된다
        print(f"[auth/sms] 확인 실패(무시): {type(e).__name__}: {e}")


@app.get("/api/auth/sms-result")
async def auth_sms_result(phone: str) -> dict:
    """📵 **그 인증문자 갔나요?** — 앱이 코드 입력 화면에서 한 번 물어본다. (추가98 · 2026-09-30)

    사장님: "사용자한테 너 단말기는 문자를 못받는 단말기다. 이렇게 안내 팝업 보여줬어?"
    → 전엔 **아니었다.** 통신사 회신은 사장님 슬랙으로만 갔고, 쓰는 분 화면은 그대로였다.

    답:
      {"state":"failed", "code":"3050", "reason":"...", "say":"...", "act":"report"}
      {"state":"unknown"}   # 아직 회신 전이거나, 잘 간 경우 (둘을 구분해 말하지 않는다)

    ⚠️ 로그인 전에 부르는 자리라 토큰이 없다. 그래서 **못 간 기록이 있을 때만**,
       그것도 **10분 안의 것만** 답한다. 남의 번호를 넣어봐야 「모름」밖에 안 나온다.
    """
    digits = _norm_phone(phone)
    if not digits:
        return {"state": "unknown"}
    try:
        with db_conn() as con:
            _auth_sms_result_table(con)
            row = con.execute(
                "SELECT code, reason, at_ms FROM auth_sms_result WHERE phone = ?", (digits,)
            ).fetchone()
    except Exception as e:  # noqa: BLE001
        print(f"[auth/sms-result] 조회 실패(무시): {type(e).__name__}: {e}")
        return {"state": "unknown"}
    if not row:
        return {"state": "unknown"}
    code, reason, at_ms = str(row[0] or ""), str(row[1] or ""), int(row[2] or 0)
    if _now_ms() - at_ms > AUTH_SMS_RESULT_TTL_MS:
        return {"state": "unknown"}
    act, say = _SMS_FAIL_SAY.get(code, ("retry", "문자가 안 갔어요.\n다시 받아보세요."))
    return {"state": "failed", "code": code, "reason": reason, "say": say, "act": act}
