package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import com.detailline.callfollowcrm.util.PhoneKey

/**
 * 🤝 **공유받은 현장을 「업체별 → 달별」로 묶는 셈 — 여기 한 곳.** (2026-10-16 사장님)
 *
 * 왜 생겼나:
 *   「협업 현장 → 업체별」에서 해시줄눈·하우스픽·태그줄눈 사장님이 **전부 「사장님」 한 줄로 뭉쳐** 나왔다.
 *   (사장님 *"업체가 여러 업체인데 왜 이렇게 나올까"*)
 *
 *   뿌리 = 서버 §B(`/api/shared/partners`)가 **번호(owner_phone)로만** 묶고, 이름은 공유마다 적힌
 *   `owner_name_raw` 가 아니라 **가입자 명부**에서 가져온다. 명부에 이름이 없으면 「사장님」.
 *   그래서 같은 번호로 온 공유는 이름이 달라도 한 덩어리가 되고, 이름까지 빈다.
 *   반면 받은 현장 한 건 한 건(with-me)에는 **보낸 쪽이 적은 상호가 그대로** 실려 온다.
 *
 * 그래서 규칙:
 *   · 업체 = **번호 + 이름** 둘 다로 가른다. 번호가 다르면 다른 업체, 번호가 같아도 이름이 다르면 다른 업체.
 *   · 이름이 비거나 「사장님」뿐이면 **번호 뒤 4자리**로 구분해 적는다 — 「사장님(···1234)」.
 *   · 서버 §B 는 **합계 보강에만** 쓴다([merge]) — 번호가 로컬 한 업체에만 맞으면 그 업체 합계를 서버 값으로
 *     (with-me 창 밖 옛 현장까지 센 값), 로컬에 없는 번호면 빈 목록 업체로 더한다(옛 이력),
 *     한 번호가 로컬 두 업체 이상이면 **나눌 수 없으니 건드리지 않는다.**
 *   · 받은 일당 = **완료된 현장**의 일당 합 — 서버 §B `total_wage` 와 같은 규칙.
 *   · 달 = [SharedSiteRepository.ymOf] (서울 시간) — 달 묶는 셈은 거기 한 곳이다. 여기서 새로 안 센다.
 *
 * ⚠️ **폰에서 눈으로 못 본다** — 여러 사장님이 실제로 공유를 보내야 재현된다. 시험이 대신 본다 (§12-E②).
 */
object CollabPartnerGroups {

    /** 서버·로컬 양쪽이 이름을 못 채웠을 때 들어오는 빈 이름. */
    const val NAMELESS = "사장님"

    /** 「내가 공유한 현장」에서 아직 아무도 수락 안 한 현장들의 묶음 이름([groupByPartner]). */
    const val PENDING = "수락 대기"

    /**
     * 업체 한 줄 — [key] 는 번호 열쇠 + 이름(화면 상태로 들고 다니는 문자열).
     * [pending] = 「수락 대기」 묶음(내가 공유한 현장에서 상대가 아직 없는 것) — 아바타에 글자 대신 시계를 그린다.
     */
    data class Group(
        val key: String,
        val phoneKey: String,
        val name: String,
        val count: Int,
        val recentMs: Long,
        val wageSum: Int,
        val sites: List<SharedSite>,
        val pending: Boolean = false
    )

    /** 한 업체 안의 한 달 — [ym] 은 "yyyy-MM". */
    data class Month(
        val ym: String,
        val count: Int,
        val wageSum: Int,
        val sites: List<SharedSite>
    )

    /** 업체 열쇠 — 번호 끝 8자리 + 이름. 둘 중 하나라도 다르면 다른 업체. */
    fun keyOf(ownerPhone: String?, ownerName: String?): String =
        PhoneKey.of(ownerPhone) + "|" + cleanName(ownerName)

    /**
     * 화면에 적는 업체 이름. 이름이 있으면 그대로, 비었으면 번호 뒤 4자리로 구분한다.
     *   「사장님」 / 「사장님(···1234)」 / 「해시줄눈」
     */
    fun displayName(ownerName: String?, ownerPhone: String?): String {
        val n = cleanName(ownerName)
        if (n.isNotEmpty()) return n
        val digits = ownerPhone?.filter { it.isDigit() }.orEmpty()
        return if (digits.length >= 4) "$NAMELESS(···${digits.takeLast(4)})" else NAMELESS
    }

    /** 받은 현장 → 업체별(번호+이름). 최근 현장이 위로. */
    fun group(sites: List<SharedSite>): List<Group> =
        sites.groupBy { keyOf(it.ownerPhone, it.ownerName) }
            .map { (key, list) ->
                val first = list.first()
                Group(
                    key = key,
                    phoneKey = PhoneKey.of(first.ownerPhone),
                    name = displayName(first.ownerName, first.ownerPhone),
                    count = list.size,
                    recentMs = list.maxOf { maxOf(it.scheduledAtMs, it.createdAtMs) },
                    wageSum = wageOf(list),
                    sites = list.sortedByDescending { it.scheduledAtMs }
                )
            }
            .sortedByDescending { it.recentMs }

    /**
     * 「내가 공유한 현장」(by-me) → **내가 부른 사장님별**. (2026-10-17 사장님 "공유받은과 짝 맞추기")
     *
     *   [group] 과 **뜻이 다르다**(§12-I): 공유받은 현장엔 주인 번호가 있지만, 내가 공유한 현장엔 상대 번호가 없고
     *   **상대 상호(`partnerName`)만** 있다(수락 전엔 null). 그래서 이름으로만 가른다.
     *   · 이름이 비면(아직 수락 전) **「수락 대기」 한 묶음**([PENDING], `pending=true`) — 맨 위. 기다리는 게 제일 급한 일이다.
     *   · 나머지는 최근 현장이 위([group] 과 같은 순서).
     *   · [Group.wageSum] 은 여기서도 완료 건의 일당 합(내가 **준** 일당) — 화면이 「받은 일당」이라 적으면 안 된다.
     */
    fun groupByPartner(sites: List<SharedSite>): List<Group> {
        val groups = sites.groupBy { it.partnerName?.trim().orEmpty() }
            .map { (name, list) ->
                val pending = name.isEmpty()
                Group(
                    key = if (pending) "pending|" else "partner|$name",
                    phoneKey = "",
                    name = if (pending) PENDING else name,
                    count = list.size,
                    recentMs = list.maxOf { maxOf(it.scheduledAtMs, it.createdAtMs) },
                    wageSum = wageOf(list),
                    sites = list.sortedByDescending { it.scheduledAtMs },
                    pending = pending
                )
            }
        return groups.sortedWith(compareByDescending<Group> { it.pending }.thenByDescending { it.recentMs })
    }

    /**
     * 서버 §B 합계로 보강한다. 로컬 업체 목록은 **그대로 두고**, 번호가 딱 한 업체에만 맞을 때만 그 업체의
     * 합계(현장 수·받은 일당·최근)를 서버 값 이상으로 올린다. 로컬에 없는 번호는 빈 목록 업체로 더한다.
     */
    fun merge(local: List<Group>, server: List<SharedSiteRepository.Partner>): List<Group> {
        if (server.isEmpty()) return local
        val out = local.toMutableList()
        server.forEach { p ->
            val pk = PhoneKey.of(p.ownerPhone)
            val hits = out.withIndex().filter { it.value.phoneKey == pk }
            when (hits.size) {
                1 -> {
                    val (i, g) = hits.first()
                    out[i] = g.copy(
                        count = maxOf(g.count, p.count),
                        wageSum = maxOf(g.wageSum, p.totalWage),
                        recentMs = maxOf(g.recentMs, p.lastAtMs)
                    )
                }
                0 -> out += Group(
                    key = keyOf(p.ownerPhone, p.ownerName),
                    phoneKey = pk,
                    name = displayName(p.ownerName, p.ownerPhone),
                    count = p.count,
                    recentMs = p.lastAtMs,
                    wageSum = p.totalWage,
                    sites = emptyList()
                )
                else -> Unit   // 한 번호에 업체가 둘 이상 — 서버 합계를 나눌 수 없다. 로컬 셈 그대로.
            }
        }
        return out.sortedByDescending { it.recentMs }
    }

    /** 한 업체의 현장 → 달별. 최신 달이 위, 달 안은 최신 현장이 위. */
    fun byMonth(sites: List<SharedSite>): List<Month> =
        sites.groupBy { SharedSiteRepository.ymOf(it.scheduledAtMs) }
            .map { (ym, list) ->
                Month(
                    ym = ym,
                    count = list.size,
                    wageSum = wageOf(list),
                    sites = list.sortedByDescending { it.scheduledAtMs }
                )
            }
            .sortedByDescending { it.ym }

    /** 받은 일당 = 완료된 현장의 일당 합(만원). 서버 §B total_wage 와 같은 규칙. */
    private fun wageOf(list: List<SharedSite>): Int =
        list.filter { it.progress == SharedSiteRepository.Progress.COMPLETED }.sumOf { it.dailyWage ?: 0 }

    /** 「사장님」 빈 이름은 없는 것으로 친다 — 번호로 가르기 위해. */
    private fun cleanName(name: String?): String {
        val n = name?.trim().orEmpty()
        return if (n == NAMELESS) "" else n
    }
}
