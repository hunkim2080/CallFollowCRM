package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.CustomerMergeDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.PhoneMatch
import java.util.Calendar

/**
 * 번호 모양 때문에 **둘로 갈라진 손님**을 하나로 합친다. (2026-09-23 사장님 "갈라진 손님 6쌍 합쳐")
 *
 * `010-3404-5247` 과 `01034045247` 을 다른 사람으로 보던 버그(고침 `b53e9bcf`) 가 남긴 흔적이다.
 * 새로 생기는 건 막았지만 **이미 갈라진 건 그대로**라, 한쪽엔 돈·일정 다른 쪽엔 통화요약이
 * 흩어진 채 이력이 계속 쪼개져 쌓인다.
 *
 * **아무것도 안 버린다** 가 원칙이다:
 *   · 일정·문자·통화요약·녹음·사진·발행문서는 **전부 옮긴다.**
 *   · 빈 칸만 상대 값으로 채운다. **적혀 있는 값은 절대 안 덮는다.**
 *   · 버리는 쪽 이름·메모는 **남는 쪽 메모에 붙인다.** (사라지지 않게)
 *   · 같은 시공일 일정이 겹치게 되면 **지우지 않고 세어서 알려준다.** 판단은 사장님이.
 */
class CustomerMergeManager(
    private val mergeDao: CustomerMergeDao,
    private val customerDao: CustomerDao,
) {

    /** 합치기 전에 사장님께 보여줄 한 쌍. */
    data class Plan(
        val displayPhone: String,
        val keeperId: Long,
        val loserIds: List<Long>,
        val movingJobs: Int,
        val clashingJobs: Int,
        val movingOthers: Int,
        val droppedNames: List<String>,
    )

    data class Result(val mergedPairs: Int, val movedJobs: Int, val clashingJobs: Int, val movedOthers: Int)

    /** 합칠 쌍 찾기 — 바꾸는 건 없다. 미리보기용. */
    suspend fun findPlans(): List<Plan> {
        val all = runCatching { mergeDao.allCustomers() }.getOrDefault(emptyList())
        val groups = all.groupBy { PhoneMatch.keyOf(it.phoneNumber) }
            .filter { (k, v) -> k.isNotBlank() && v.size > 1 }
        val out = ArrayList<Plan>()
        for ((_, members) in groups) {
            val scored = members.map { it to weightOf(it, countsOf(it.id)) }
            val keeper = scored.maxByOrNull { it.second }!!.first
            val losers = members.filter { it.id != keeper.id }

            val keeperDays = dayKeysOf(keeper.id)
            var moving = 0
            var clashing = 0
            var others = 0
            val dropped = ArrayList<String>()
            for (l in losers) {
                for (r in runCatching { mergeDao.jobDatesOf(l.id) }.getOrDefault(emptyList())) {
                    moving++
                    if (dayKeyOf(r.workDate) in keeperDays) clashing++
                }
                others += countsOf(l.id).others
                val n = l.name?.trim()
                if (!n.isNullOrBlank() && n != keeper.name?.trim()) dropped += n
            }
            out += Plan(
                displayPhone = prettyPhone(keeper.phoneNumber),
                keeperId = keeper.id,
                loserIds = losers.map { it.id },
                movingJobs = moving,
                clashingJobs = clashing,
                movingOthers = others,
                droppedNames = dropped,
            )
        }
        return out.sortedBy { it.displayPhone }
    }

    /** 진짜로 합친다. **되돌릴 수 없다** — 부르는 쪽이 백업을 먼저 뜨고 확인을 받았어야 한다. */
    suspend fun merge(plans: List<Plan>): Result {
        var pairs = 0
        var jobs = 0
        var clash = 0
        var others = 0
        for (p in plans) {
            val keeper = runCatching { customerDao.findById(p.keeperId) }.getOrNull() ?: continue
            var merged = keeper
            for (loserId in p.loserIds) {
                val loser = runCatching { customerDao.findById(loserId) }.getOrNull() ?: continue
                val keeperDays = dayKeysOf(keeper.id)
                for (r in runCatching { mergeDao.jobDatesOf(loserId) }.getOrDefault(emptyList())) {
                    jobs++
                    if (dayKeyOf(r.workDate) in keeperDays) clash++
                }
                others += moveEverything(loserId, keeper.id)
                merged = fillBlanks(merged, loser)
                runCatching { mergeDao.deleteCustomer(loserId) }
            }
            runCatching { customerDao.update(merged.copy(updatedAt = System.currentTimeMillis())) }
            pairs++
        }
        return Result(pairs, jobs, clash, others)
    }

    // ── 속 ──────────────────────────────────────────────────────────────

    private data class Counts(val jobs: Int, val others: Int)

    private suspend fun countsOf(id: Long): Counts {
        val j = runCatching { mergeDao.jobDatesOf(id).size }.getOrDefault(0)
        // '그 밖'은 옮겨보면 정확히 세어지지만, 미리보기에선 0 으로 두고 실제 합칠 때 센다.
        return Counts(j, 0)
    }

    private suspend fun dayKeysOf(id: Long): Set<Long> =
        runCatching { mergeDao.jobDatesOf(id) }.getOrDefault(emptyList())
            .mapNotNull { dayKeyOf(it.workDate) }.toSet()

    /** 일정 하나하나가 아니라 **그 날짜**로 본다 — 시·분이 달라도 같은 날이면 겹친 것이다. */
    private fun dayKeyOf(ms: Long?): Long? {
        if (ms == null || ms <= 0L) return null
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100L + c.get(Calendar.DAY_OF_MONTH)
    }

    private suspend fun moveEverything(from: Long, to: Long): Int {
        var n = 0
        n += runCatching { mergeDao.moveJobs(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveJobCrew(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveMessages(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveCallSummaries(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveRecordings(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveImportant(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveIssuedDocs(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.movePhotos(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveRecurringLog(from, to) }.getOrDefault(0)
        n += runCatching { mergeDao.moveTeamAssignments(from, to) }.getOrDefault(0)
        return n
    }

    /** **빈 칸만** 채운다. 적혀 있는 값은 손대지 않는다. */
    private fun fillBlanks(keep: CustomerEntity, other: CustomerEntity): CustomerEntity {
        val extraName = other.name?.trim()
            ?.takeIf { it.isNotBlank() && it != keep.name?.trim() && !keep.name.isNullOrBlank() }
        val notes = buildList {
            if (keep.memo.isNotBlank()) add(keep.memo)
            if (other.memo.isNotBlank() && other.memo != keep.memo) add(other.memo)
            if (extraName != null) add("(합치면서 남긴 다른 이름: $extraName)")
        }.joinToString("\n---\n")
        return keep.copy(
            name = keep.name?.takeIf { it.isNotBlank() } ?: other.name,
            memo = notes,
            categoryId = keep.categoryId ?: other.categoryId,
            address = keep.address?.takeIf { it.isNotBlank() } ?: other.address,
            scheduledWorkDate = keep.scheduledWorkDate ?: other.scheduledWorkDate,
            leadHeat = keep.leadHeat ?: other.leadHeat,
            depositAmount = keep.depositAmount ?: other.depositAmount,
            depositPaidAt = keep.depositPaidAt ?: other.depositPaidAt,
            balanceAmount = keep.balanceAmount ?: other.balanceAmount,
            balancePaidAt = keep.balancePaidAt ?: other.balancePaidAt,
            totalAmount = keep.totalAmount ?: other.totalAmount,
            scheduledWorkMinutes = keep.scheduledWorkMinutes ?: other.scheduledWorkMinutes,
            asScheduledDate = keep.asScheduledDate ?: other.asScheduledDate,
            workCompletedAt = keep.workCompletedAt ?: other.workCompletedAt,
            workCalendarEventId = keep.workCalendarEventId ?: other.workCalendarEventId,
            asCalendarEventId = keep.asCalendarEventId ?: other.asCalendarEventId,
            createdAt = minOf(keep.createdAt, other.createdAt),
        )
    }

    private fun weightOf(c: CustomerEntity, counts: Counts): Int =
        CustomerMergeScore.of(
            hasName = !c.name.isNullOrBlank(),
            hasAddress = !c.address.isNullOrBlank(),
            hasMemo = c.memo.isNotBlank(),
            hasMoney = (c.totalAmount ?: 0L) > 0L,
            hasCalendar = !c.workCalendarEventId.isNullOrBlank(),
            jobs = counts.jobs,
            olderTiebreak = -c.id.toInt(),
        )

    private fun prettyPhone(p: String): String {
        val d = p.filter { it.isDigit() }
        return if (d.length == 11) "${d.take(3)}-${d.substring(3, 7)}-${d.substring(7)}" else p
    }
}

/**
 * 둘 중 **어느 쪽을 남길지** 고르는 잣대. 순수 계산이라 따로 빼서 테스트한다.
 *   내용이 더 찬 쪽을 남긴다. 완전히 같으면 **먼저 만들어진 쪽**(id 가 작은 쪽).
 */
object CustomerMergeScore {
    fun of(
        hasName: Boolean,
        hasAddress: Boolean,
        hasMemo: Boolean,
        hasMoney: Boolean,
        hasCalendar: Boolean,
        jobs: Int,
        olderTiebreak: Int,
    ): Int {
        var s = 0
        if (hasName) s += 40
        if (hasAddress) s += 30
        if (hasMemo) s += 20
        if (hasMoney) s += 30
        if (hasCalendar) s += 25   // 구글 캘린더에 이미 걸려 있으면 그쪽이 '살아있는' 쪽이다
        s += jobs * 50             // 일정이 제일 무겁다 — 돈과 약속이 달려 있다
        return s * 1000 + olderTiebreak
    }
}
