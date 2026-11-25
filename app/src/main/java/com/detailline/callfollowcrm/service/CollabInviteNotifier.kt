package com.detailline.callfollowcrm.service

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import com.detailline.callfollowcrm.R

/**
 * 🤝 협업 요청 알림(받는 쪽) **커스텀** — 라이브 카운트다운 칩(수락 마감까지) + 현장/일당. (2026-11-25 사장님 프로토)
 *   Chronometer 로 앱 안 켜도 1초씩 줄어드는 카운트다운(쿠팡식). inflate 실패(기기별)면 null → 글자 알림 폴백.
 *
 *   ⚠️ RemoteViews 화이트리스트만(순수 <View> 금지 — 알림 사라짐). 만료시각은 CollabInviteExpiry 로 계산해 넘긴다.
 */
object CollabInviteNotifier {

    /**
     * @param who 부른 사장 ("하우스픽 사장님"). @param site 현장명. @param dailyWage 일당(만원, null 가능).
     * @param deadlineMs 수락 마감 epoch ms (anchor + TTL). 이미 지난 값이면 호출부가 안 부른다(pending 만 보여줌).
     */
    fun buildView(context: Context, who: String, site: String, dailyWage: Int?, deadlineMs: Long): RemoteViews? =
        runCatching {
            val siteLine = buildString {
                append(who); append(" · "); append(site)
                dailyWage?.takeIf { it > 0 }?.let { append(" · ${it}만") }
            }
            val rv = RemoteViews(context.packageName, R.layout.notification_collab_invite)
            rv.setTextViewText(R.id.ci_site, siteLine)
            // Chronometer 는 elapsedRealtime 기준 → 벽시계 마감(deadlineMs)을 elapsedRealtime 기준으로 변환.
            val base = SystemClock.elapsedRealtime() + (deadlineMs - System.currentTimeMillis())
            rv.setChronometer(R.id.ci_cd, base, null, true)   // base, format=기본(H:MM:SS/MM:SS), started
            rv.setChronometerCountDown(R.id.ci_cd, true)       // 거꾸로(카운트다운) — API 24+
            rv
        }.getOrNull()
}
