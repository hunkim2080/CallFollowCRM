package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.domain.collab.CollabTogether
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🤝 **협업 사장 함께한 현장 캐시 — 여기 한 곳.** (2026-10-27 사장님 "다녀온 페이지는 기록해 다음에 안 불러오게")
 *   폰에 저장(`AppPreferences.collabSitesCacheJson`)해 두고, 다음에 그 고객을 열면 **서버에 안 묻고 바로** 보여준다.
 *   서버가 새 현장을 주면 [save] 로 갱신하고, 빈 답이면 **안 지운다**(협업 사장은 보통 고객이 안 됨 — 서버 먹통에도 안 흔들림).
 */
object CollabSitesCache {

    /** 캐시에 있으면 그걸로 만든 [CollabTogether.View], 없으면 null. */
    fun load(prefs: AppPreferences, phoneKey: String): CollabTogether.View? {
        if (phoneKey.isBlank()) return null
        val node = runCatching { JSONObject(prefs.collabSitesCacheJson) }.getOrNull()
            ?.optJSONObject(phoneKey) ?: return null
        val r = sitesFrom(node.optJSONArray("r"))
        val g = sitesFrom(node.optJSONArray("g"))
        if (r.isEmpty() && g.isEmpty()) return null
        return CollabTogether.build(r, g).takeIf { it.isPartner }
    }

    /** 받은/준 현장(수락분)을 이 고객 번호열쇠로 저장한다. 빈 값은 저장하지 않는다(= 안 지움). */
    fun save(prefs: AppPreferences, phoneKey: String, received: List<SharedSite>, given: List<SharedSite>) {
        if (phoneKey.isBlank() || (received.isEmpty() && given.isEmpty())) return
        val root = runCatching { JSONObject(prefs.collabSitesCacheJson) }.getOrNull() ?: JSONObject()
        root.put(phoneKey, JSONObject().put("r", arrOf(received)).put("g", arrOf(given)))
        prefs.collabSitesCacheJson = root.toString()
    }

    private fun arrOf(sites: List<SharedSite>): JSONArray =
        JSONArray().apply { sites.forEach { put(toJson(it)) } }

    private fun toJson(s: SharedSite): JSONObject = JSONObject().apply {
        put("sid", s.shareId); put("op", s.ownerPhone); put("on", s.ownerName)
        put("pn", s.partnerName); put("t", s.title); put("ad", s.addr)
        put("at", s.scheduledAtMs); put("tl", s.timeLabel); put("ws", s.workSummary)
        if (s.dailyWage != null) put("dw", s.dailyWage)
        put("mm", s.memo); put("st", s.status); put("pg", s.progress.name); put("ct", s.createdAtMs)
    }

    private fun sitesFrom(arr: JSONArray?): List<SharedSite> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromJson(it) } }
    }

    private fun fromJson(o: JSONObject): SharedSite = SharedSite(
        shareId = o.optString("sid"),
        ownerPhone = o.optString("op"),
        ownerName = o.optString("on"),
        partnerName = o.strOrNull("pn"),
        title = o.optString("t"),
        addr = o.strOrNull("ad"),
        scheduledAtMs = o.optLong("at"),
        timeLabel = o.strOrNull("tl"),
        workSummary = o.strOrNull("ws"),
        dailyWage = if (o.has("dw") && !o.isNull("dw")) o.optInt("dw") else null,
        memo = o.strOrNull("mm"),
        status = o.optString("st"),
        progress = SharedSiteRepository.Progress.from(o.optString("pg")),
        createdAtMs = o.optLong("ct")
    )

    private fun JSONObject.strOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)
}
