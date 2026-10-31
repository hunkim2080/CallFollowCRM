package com.detailline.callfollowcrm.presentation.screen.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * QuoteDocData ↔ JSON (2026-07-07) — 발행 이력(issued_docs.docJson)에 견적서 스냅샷을 담고
 *   나중에 그대로 다시 열람(QuoteDocScreen 재렌더)하기 위한 직렬화. org.json 만 사용(의존성 X).
 */
fun QuoteDocData.toIssuedJson(): String {
    val o = JSONObject()
    o.put("lines", JSONArray().apply {
        lines.forEach {
            put(JSONObject().put("name", it.name).put("spec", it.spec).put("amountWon", it.amountWon))
        }
    })
    o.put("totalWon", totalWon)
    o.put("depMode", depMode)
    o.put("depVal", depVal)
    o.put("vatIncluded", vatIncluded)
    workDateMs?.let { o.put("workDateMs", it) }
    recipient?.let { o.put("recipient", it) }
    memo?.let { o.put("memo", it) }
    // 📌 발행 당시 사업자 이름표·날짜 스냅샷 — 재열람 때 "그때 그대로". 없으면 안 담음(옛 카드 호환). (2026-10-31 사장님)
    biz?.let {
        o.put("biz", JSONObject()
            .put("name", it.name).put("owner", it.owner).put("no", it.no)
            .put("addr", it.addr).put("phone", it.phone).put("validDays", it.validDays))
    }
    issuedDateMs?.let { o.put("issuedDateMs", it) }
    return o.toString()
}

/** 실패(빈/깨진 JSON)해도 안전 — null 반환. */
fun quoteDocDataFromIssuedJson(json: String?): QuoteDocData? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val o = JSONObject(json)
        val arr = o.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            QuoteLine(l.optString("name"), l.optString("spec"), l.optLong("amountWon"))
        }
        QuoteDocData(
            lines = lines,
            totalWon = o.optLong("totalWon"),
            depMode = o.optString("depMode", "none").ifBlank { "none" },
            depVal = o.optInt("depVal"),
            vatIncluded = o.optBoolean("vatIncluded"),
            workDateMs = if (o.has("workDateMs")) o.optLong("workDateMs") else null,
            recipient = o.optString("recipient").takeIf { it.isNotBlank() },
            memo = o.optString("memo").takeIf { it.isNotBlank() },
            // 사업자정보·날짜 스냅샷(있으면). 없으면 null → QuoteDocScreen 이 현재 설정을 씀(옛 카드). (2026-10-31)
            biz = o.optJSONObject("biz")?.let {
                QuoteBiz(
                    name = it.optString("name"), owner = it.optString("owner"), no = it.optString("no"),
                    addr = it.optString("addr"), phone = it.optString("phone"),
                    validDays = it.optInt("validDays", 14)
                )
            },
            issuedDateMs = if (o.has("issuedDateMs")) o.optLong("issuedDateMs") else null
        )
    }.getOrNull()
}
