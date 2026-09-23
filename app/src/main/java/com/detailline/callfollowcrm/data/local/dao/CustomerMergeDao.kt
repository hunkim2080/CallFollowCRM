package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity

/**
 * 같은 사람이 번호 모양 때문에 **손님 둘로 갈라진 것**을 합칠 때 쓰는 문들. (2026-09-23 사장님)
 *
 * 왜 생겼나 — `010-3404-5247` 과 `01034045247` 을 다른 사람으로 보던 버그(고침 `b53e9bcf`)가
 * 만들어 둔 흔적이 6쌍 남아 있다. 한쪽엔 돈·일정, 다른 쪽엔 통화요약이 흩어져 있어
 * **이력이 계속 쪼개진 채 쌓인다.** 고침은 새로 생기는 것만 막았지 옛 것을 치우진 않는다.
 *
 * ⚠️ 여기 쿼리는 전부 **되돌릴 수 없다.** 부르는 쪽(`CustomerMergeManager`)이
 *    합치기 직전에 백업을 뜨고, 사장님이 미리보기를 보고 확인한 뒤에만 부른다.
 */
@Dao
interface CustomerMergeDao {

    @Query("SELECT * FROM customers")
    suspend fun allCustomers(): List<CustomerEntity>

    /** 한 손님의 일정 — 같은 날 일정이 겹치는지 보려고 시공일만 본다. */
    @Query("SELECT id AS jobId, scheduledWorkDate AS workDate FROM jobs WHERE customerId = :customerId")
    suspend fun jobDatesOf(customerId: Long): List<JobDateRow>

    // ── 고객을 가리키는 표 10개. 전부 새 주인으로 옮긴다. ──
    @Query("UPDATE jobs SET customerId = :to WHERE customerId = :from")
    suspend fun moveJobs(from: Long, to: Long): Int

    @Query("UPDATE job_crew SET customerId = :to WHERE customerId = :from")
    suspend fun moveJobCrew(from: Long, to: Long): Int

    @Query("UPDATE message_histories SET customerId = :to WHERE customerId = :from")
    suspend fun moveMessages(from: Long, to: Long): Int

    @Query("UPDATE call_summaries SET customerId = :to WHERE customerId = :from")
    suspend fun moveCallSummaries(from: Long, to: Long): Int

    @Query("UPDATE recording_attachments SET customerId = :to WHERE customerId = :from")
    suspend fun moveRecordings(from: Long, to: Long): Int

    @Query("UPDATE important_messages SET customerId = :to WHERE customerId = :from")
    suspend fun moveImportant(from: Long, to: Long): Int

    @Query("UPDATE issued_docs SET customerId = :to WHERE customerId = :from")
    suspend fun moveIssuedDocs(from: Long, to: Long): Int

    @Query("UPDATE site_photos SET customerId = :to WHERE customerId = :from")
    suspend fun movePhotos(from: Long, to: Long): Int

    @Query("UPDATE recurring_message_log SET customerId = :to WHERE customerId = :from")
    suspend fun moveRecurringLog(from: Long, to: Long): Int

    @Query("UPDATE team_assignments SET customerId = :to WHERE customerId = :from")
    suspend fun moveTeamAssignments(from: Long, to: Long): Int

    /** 빈 껍데기가 된 쪽을 지운다. **옮기기가 다 끝난 뒤에만.** */
    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long): Int
}

/** [CustomerMergeDao.jobDatesOf] 결과 한 줄. */
data class JobDateRow(val jobId: Long, val workDate: Long?)
