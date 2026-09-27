package com.detailline.callfollowcrm.util

/**
 * ☎️ **번호를 맞추는 열쇠 — 여기 한 곳에서만 만든다.** (2026-09-28)
 *
 * 왜 생겼나:
 *   같은 손님 번호가 화면마다 다르게 적혀 있다 — `010-4805-2630` 과 `01048052630` 과 `+821048052630`.
 *   그래서 **어디서는 같은 사람, 어디서는 딴 사람**이 됐다.
 *   2026-09-27 에 실제로 터졌다: 대화방은 끝 8자리로 찾아 이름이 멀쩡히 나오는데
 *   상담함은 글자 그대로 비교해서 **고객 카드가 아예 안 붙었다** —
 *   이름도 딱지도 안 뜨고, A/S·시공 대기·잔금 대기 숫자에서도 그 손님이 통째로 빠졌다.
 *   그 계산을 **36개 파일이 각자** 적고 있었다. 한 곳만 고쳐서는 또 어긋난다.
 *
 * 왜 끝 8자리인가:
 *   앞자리는 표기가 제각각이지만(`010`, `+8210`, 없음) **뒤 8자리는 안 바뀐다.**
 *   8자리가 안 되는 짧은 번호(114·1588 등)는 있는 그대로 쓴다 — 자를 게 없다.
 */
object PhoneKey {

    /** 번호 → 열쇠. 숫자만 남긴 뒤 끝 8자리. 8자리 미만이면 있는 그대로. 빈 값이면 "". */
    fun of(phone: String?): String {
        val digits = phone?.filter { it.isDigit() } ?: return ""
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    /** 두 번호가 같은 사람인가. 한쪽이라도 비면 false — **빈 값끼리 같다고 하면 안 된다.** */
    fun same(a: String?, b: String?): Boolean {
        val ka = of(a)
        return ka.isNotEmpty() && ka == of(b)
    }

    /** 번호로 찾아 쓰기 좋은 지도. 같은 열쇠가 겹치면 **먼저 온 것**이 이긴다. */
    fun <T> mapBy(items: List<T>, phone: (T) -> String?): Map<String, T> {
        val out = LinkedHashMap<String, T>()
        items.forEach { item ->
            val k = of(phone(item))
            if (k.isNotEmpty() && !out.containsKey(k)) out[k] = item
        }
        return out
    }

    /** 이 번호의 주인을 찾는다. 없으면 null. */
    fun <T> find(items: List<T>, target: String?, phone: (T) -> String?): T? {
        val k = of(target)
        if (k.isEmpty()) return null
        return items.firstOrNull { of(phone(it)) == k }
    }
}
