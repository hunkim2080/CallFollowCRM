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
            memo = o.optString("memo").takeIf { it.isNotBlank() }
        )
    }.getOrNull()
}
