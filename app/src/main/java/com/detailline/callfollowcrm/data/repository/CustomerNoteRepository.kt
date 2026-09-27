package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CustomerNoteDao
import com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow

/**
 * 📝 **쪽지 메모** — 한 덩어리 글 대신, 시각이 붙는 쪽지로. (2026-09-27 사장님)
 *
 * ⚠️ **가장 중요한 약속** — 쪽지가 바뀔 때마다 옛 메모 칸(`customers.memo` / `jobs.memo`)에
 *   **사람이 읽는 글로 다시 적어 넣는다**([renderMirror]).
 *   그 칸을 읽는 곳이 많기 때문이다 — 구글 캘린더 설명, 본폰 미러링, AI 답변추천,
 *   웹 피드, 고객 합치기. 한 곳이라도 안 고치면 거기서 메모가 **사라진 것처럼** 보인다.
 *   (같은 실수를 [reference_jobs_sot_move_carry_money_too] 에서 한 번 했다 — 일정만 옮기고 돈을 안 옮겼다)
 *
 * 다시 적어 넣는 글은 **지금 화면과 같은 차례**다:
 * ```
 * 📌 국민 123456-78-901234
 * 9/27 11:20 · 시공 전날 다시 전화해서 2시로
 * 9/25 14:08 · 잔금 치르고 1시에 들어갈 수 있다고 함
 * ```
 */
class CustomerNoteRepository(
    private val dao: CustomerNoteDao,
    private val customerRepository: CustomerRepository,
    private val jobRepository: JobRepository
) {

    fun observeForCustomer(customerId: Long): Flow<List<CustomerNoteEntity>> =
        dao.observeForCustomer(customerId)

    fun observeForJob(jobId: Long): Flow<List<CustomerNoteEntity>> = dao.observeForJob(jobId)

    /**
     * 쪽지 한 장 적기. 빈 글은 넣지 않는다 — 빈 줄이 쌓이면 목록이 쓰레기가 된다.
     * @return 넣었으면 true.
     */
    suspend fun add(
        customerId: Long,
        jobId: Long? = null,
        body: String,
        source: String = "",
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val text = body.trim()
        if (text.isEmpty() || customerId <= 0L) return false
        dao.insert(
            CustomerNoteEntity(
                customerId = customerId, jobId = jobId, body = text,
                source = source, createdAt = now, updatedAt = now
            )
        )
        mirror(customerId, jobId)
        return true
    }

    /** 고쳐 적기. 적은 시각은 **그대로 둔다** — 언제 들은 얘기인지가 정보다. */
    suspend fun edit(id: Long, body: String, now: Long = System.currentTimeMillis()) {
        val n = dao.byId(id) ?: return
        val text = body.trim()
        if (text.isEmpty()) { remove(id); return }
        dao.update(n.copy(body = text, updatedAt = now))
        mirror(n.customerId, n.jobId)
    }

    /** 📌 못 박기 / 풀기. */
    suspend fun togglePin(id: Long, now: Long = System.currentTimeMillis()) {
        val n = dao.byId(id) ?: return
        dao.update(n.copy(pinned = !n.pinned, updatedAt = now))
        mirror(n.customerId, n.jobId)
    }

    suspend fun remove(id: Long) {
        val n = dao.byId(id) ?: return
        dao.delete(id)
        mirror(n.customerId, n.jobId)
    }

    /** 고객을 합칠 때 — 없어지는 쪽 쪽지를 남는 쪽으로. */
    suspend fun moveCustomer(fromId: Long, keepId: Long) {
        dao.moveCustomer(fromId, keepId)
        mirror(keepId, null)
    }

    suspend fun deleteForCustomer(customerId: Long) = dao.deleteForCustomer(customerId)

    // ── 옛 메모 칸에 다시 적어 넣기 ──────────────────────────────────

    private suspend fun mirror(customerId: Long, jobId: Long?) {
        if (jobId != null) {
            jobRepository.updateMemo(jobId, renderMirror(dao.forJobOnce(jobId)))
        } else {
            customerRepository.updateMemo(customerId, renderMirror(dao.forCustomerOnce(customerId)))
        }
    }

    companion object {
        /**
         * 쪽지들 → 사람이 읽는 한 덩어리 글. 캘린더·AI·미러링이 이 글을 읽는다.
         *   · 📌 은 「📌 」를 앞에 붙인다(시각 없이 — 늘 유효한 얘기니까)
         *   · 나머지는 「9/27 11:20 · 본문」
         *   · 시각을 모르는 옛 메모(legacy)는 시각 없이 그대로
         */
        fun renderMirror(notes: List<CustomerNoteEntity>): String = notes.joinToString("\n") { n ->
            when {
                n.pinned -> "📌 " + n.body
                n.createdAt <= 0L -> n.body
                else -> DateTimeUtils.formatMonthDay(n.createdAt) + " " +
                    DateTimeUtils.formatTime(n.createdAt) + " · " + n.body
            }
        }
    }
}
