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

    /**
     * 🔒 **저장할 때 쓰는 단 하나의 모양.** (2026-09-28 사장님)
     *
     *   사장님: "우리 앱에서는 번호가 두 갈래 세 갈래로 나뉘면 안 돼.
     *            무조건 한 번호로 통일해야 흩어지지 않지."
     *
     *   `010-3404-5247` · `+82 10-3404-5247` · `010 3404 5247` → 전부 `01034045247`.
     *
     * ⚠️ **화면에 보이는 모양이 아니다.** 보여줄 땐 [PhoneNumberFormatter.format] 이
     *    하이픈을 붙인다 — 사장님이 보시는 건 그대로 `010-3404-5247` 이다.
     *    여기서 숫자만 남기는 건 **DB 에 두 줄이 생기지 않게** 하려는 것뿐이다.
     *
     * 규칙:
     *   · 숫자만 남긴다 (하이픈·공백·괄호 제거)
     *   · `+82…` 는 국내형 `0…` 으로 되돌린다 — 단 **휴대폰 모양일 때만**.
     *     `82012345678` 처럼 애매한 건 손대지 않는다(지어내면 남의 번호가 된다).
     *   · 숫자가 하나도 없으면 원본을 그대로 (이름이 들어와 있을 수 있다 — 지우면 손실)
     */
    fun normalize(phone: String?): String {
        val raw = phone?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return raw
        // +82 10 1234 5678 → 010 1234 5678. '+' 가 붙었거나, 82 다음이 1 로 시작하는 휴대폰 길이일 때만.
        val looksIntl = digits.startsWith("82") &&
            (raw.startsWith("+") || (digits.length in 11..12 && digits.getOrNull(2) == '1'))
        return if (looksIntl) "0" + digits.drop(2) else digits
    }

    /**
     * 🔒 **복원할 때 쓰는 통일형 — 줄을 절대 잃지 않는다.** (2026-09-29 사장님)
     *
     *   백업 복원이 **백업에 적힌 글자 그대로** 손님을 넣고 있었다. 그래서 옛 백업을 되돌리면
     *   갈라진 번호가 **그대로 다시 살아났다** — 치워도 다시 생기는 길이 여기였다.
     *
     *   그냥 통일형으로 바꾸면 위험하다: 백업 안에 두 줄이 있으면 같은 번호가 되고,
     *   `INSERT OR REPLACE` 가 **한 줄을 지운다**(unique 충돌). 손님이 조용히 사라진다.
     *
     *   그래서 **이미 쓰인 번호면 원본을 그대로 둔다.** 갈라진 채로 남지만 **잃지는 않는다** —
     *   그건 「갈라진 손님 합치기」가 사장님 확인을 받고 처리할 일이다.
     *
     * @param taken 이번 복원에서 이미 쓴 번호들.
     */
    fun normalizeUnique(phone: String?, taken: Set<String>): String {
        val raw = phone?.trim().orEmpty()
        val fixed = normalize(raw)
        if (fixed.isEmpty() || fixed == raw) return raw
        return if (fixed in taken) raw else fixed
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
