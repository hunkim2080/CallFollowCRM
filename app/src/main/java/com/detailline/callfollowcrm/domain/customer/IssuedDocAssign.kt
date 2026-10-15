package com.detailline.callfollowcrm.domain.customer

import com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 📋 **발행 문서(견적서·접수서)를 어느 차수(시공 건)에 붙일지** 정하는 순수 규칙. (2026-10-14 사장님: 접수서를 차수 안으로)
 *
 * 접수서는 고객 번호에만 묶여 **차수 꼬리표가 없다** → 문서의 **시공예정일(workDateMs)** 로 그 날 시공 건에 맞춘다.
 *   · 어느 건의 시공일과 같은 날 → 그 건 카드 안에.
 *   · 날짜가 없거나(협의 후 확정) 어느 건과도 안 맞음 → '이 고객 문서(미지정)'.
 *
 * ⚠️ 같은 날 건이 둘이면(1차·2차 같은 날) 그 문서는 두 건 **모두**에 해당한다 — 화면은 한 번에 한 건만 펼치므로
 *    동시에 두 번 뜨진 않지만, 소속이 모호하다. 그래서 날짜 비교는 **0시 기준**(DateTimeUtils.startOfDay)으로만 한다.
 *
 * 화면/테스트가 같은 규칙을 보게 한 곳에 — 전엔 CustomerDetailScreen 안 두 곳에 똑같은 filter 가 박혀 있었다(§12).
 */
object IssuedDocAssign {

    /** 건의 시공일(0시). 날짜 없으면 null. */
    fun jobDayMs(job: JobEntity): Long? =
        job.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) }

    /** 문서의 시공예정일(0시). 없으면 null. */
    fun docDayMs(doc: IssuedDocEntity): Long? =
        doc.workDateMs?.takeIf { it > 0L }?.let { DateTimeUtils.startOfDay(it) }

    /** 이 건(날짜)에 붙는 문서들 — 시공예정일이 같은 날인 것. */
    fun forJobDay(docs: List<IssuedDocEntity>, jobDayMs: Long?): List<IssuedDocEntity> {
        if (jobDayMs == null) return emptyList()
        return docs.filter { docDayMs(it) == jobDayMs }
    }

    /** 어느 건에도 안 붙는 문서들 — 날짜 없음 또는 어떤 건의 날짜와도 안 맞음. ('이 고객 문서') */
    fun unmatched(docs: List<IssuedDocEntity>, jobDays: Set<Long>): List<IssuedDocEntity> =
        docs.filter { val d = docDayMs(it); d == null || d !in jobDays }

    /**
     * 이 건 카드에 보일 문서 — 날짜가 같은 것 + (이 건이 **맨 처음 건(1차)**이면) 날짜 없는/안 맞는 **떠돌이 문서까지**.
     *   (2026-10-15 사장님: "날짜 없는 견적서는 1차 안으로. 문서는 건 안에 있어야 위계가 맞다.")
     *   날짜 없는 견적서처럼 어느 건과도 안 맞는 문서를 고객 단위로 띄우지 않고 **맨 처음 건**에 모은다.
     *   맨 처음 건(1차)이 없으면(건이 아예 없거나 전부 취소) 호출 측이 '이 고객 문서'로 따로 띄운다.
     */
    fun forJob(
        docs: List<IssuedDocEntity>,
        jobDayMs: Long?,
        isEarliest: Boolean,
        allJobDays: Set<Long>
    ): List<IssuedDocEntity> {
        val matched = forJobDay(docs, jobDayMs)
        if (!isEarliest) return matched
        val orphans = unmatched(docs, allJobDays)
        return (matched + orphans).distinctBy { it.id }
    }

    /** 모든 건의 시공일(0시) 집합 — unmatched 판단용. */
    fun jobDaySet(jobs: List<JobEntity>): Set<Long> =
        jobs.mapNotNull { jobDayMs(it) }.toSet()
}
