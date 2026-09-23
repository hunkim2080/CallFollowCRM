package com.detailline.callfollowcrm.util

/**
 * "이 두 번호가 같은 상대인가" 를 가리는 **한 군데**. (2026-09-23 사장님)
 *
 * 왜 생겼나 — `114` 에서 온 문자 34건이 대화방에서 통째로 안 보였다. 푸시 알림은 멀쩡히 떴는데
 * 눌러서 들어간 방만 비어 있었다. 번호 숫자가 7자리 미만이면 아예 포기(`return emptyList()`)하고 있었기 때문.
 *
 * 그 가드 자체는 이유가 있었다 — 짧은 번호를 '끝자리 맞추기'로 찾으면
 * `114` 를 찾는데 `0107770114` 같은 **남의 번호까지 딸려온다.**
 * 그래서 막는 대신 **짧으면 번호 전체가 똑같을 때만** 같은 상대로 본다.
 */
object PhoneMatch {

    /** 끝자리 맞추기를 쓸 수 있는 최소 길이. 이보다 짧으면 번호 전체를 맞춘다. */
    private const val MIN_SUFFIX_LEN = 7

    /** 비교에 쓸 열쇠 — 보통 번호는 끝 8자리(하이픈·국가번호 차이 흡수), 짧은 번호는 그대로. */
    fun keyOf(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= MIN_SUFFIX_LEN) d.takeLast(8) else d
    }

    /**
     * 같은 상대인가.
     *
     * 한쪽이라도 짧은 번호면 **완전히 같아야** 참. 둘 다 보통 번호면 짧은 쪽 길이만큼(최대 8자리)
     * 끝자리를 맞춘다 — `010-1234-5678` 과 `+821012345678` 과 `1012345678` 이 같은 사람이 되도록.
     */
    fun same(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isEmpty() || y.isEmpty()) return false
        if (x.length < MIN_SUFFIX_LEN || y.length < MIN_SUFFIX_LEN) return x == y
        val shortest = minOf(x.length, y.length, 8)
        return x.takeLast(shortest) == y.takeLast(shortest)
    }
}
