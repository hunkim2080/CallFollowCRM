package com.detailline.callfollowcrm.domain.collab

/**
 * 협업 배정이 `dayStart` 날짜에 걸리나 — 배지 표시·일정변경 대상 선정이 **같은 규칙**을 쓰도록 한 곳.
 *   days 가 있으면 **그 날들에만**. 비어 있으면(구버전) **대표 건 날짜 하나로만**(representativeDay),
 *   representativeDay 가 null 이면 걸리지 않는다(옛날처럼 '전체 날짜'로 퍼지지 않게).
 */
fun collabCoversDay(days: Set<Long>, dayStart: Long, representativeDay: Long?): Boolean =
    if (days.isNotEmpty()) dayStart in days
    else representativeDay != null && dayStart == representativeDay

/**
 * 협업 배정 한 줄 — `preferences.collabAssignments` 의 문자열 형식 파싱/포맷/판단을 **한 곳**에서.
 *   형식: `"customerId|phone|name|shareId|days"`  (days = startOfDay 들을 콤마로)
 *   구버전 `"id|name"`(2토큰)·`"id|phone|name|shareId"`(4토큰, days 없음)도 호환.
 *
 * 핵심 원칙(2026-10-09 사장님): **협업은 "고객"이 아니라 "그 시공 건(날짜)"에 붙는다.**
 *   그래서 어느 날 건인지는 `days` 로 구분한다 — 같은 고객의 1차·2차가 협업자를 섞지 않게.
 *   days 가 비어 있으면(구버전·백필 전) **대표 건 날짜 하나로만** 본다(절대 '전체 날짜'로 퍼뜨리지 않음).
 */
data class CollabAssignmentLine(
    val customerId: Long,
    val phone: String,
    val name: String,
    val shareId: String,
    val days: Set<Long>
) {
    fun format(): String = "$customerId|$phone|$name|$shareId|${days.sorted().joinToString(",")}"

    fun withDays(newDays: Set<Long>): CollabAssignmentLine = copy(days = newDays)

    /**
     * 이 배정이 `dayStart` 날짜의 그 고객 카드에 떠야 하나.
     *   days 가 있으면 **그 날들에만**. 비어 있으면(구버전) **대표 건 날짜(representativeDay) 하나로만**.
     *   representativeDay 가 null 이면(날짜 모름) 뜨지 않는다 — 옛날처럼 전체로 퍼지지 않게.
     */
    fun coversDay(dayStart: Long, representativeDay: Long?): Boolean =
        collabCoversDay(days, dayStart, representativeDay)

    companion object {
        /**
         * 🎯 **이 건(날짜)에 걸린 협업 배정만** — 카드의 협업 칩과 주소/일정 전파가 **같은 타깃팅**을 쓴다(§12). (2026-10-15)
         *   왜: A 가 2차 건의 주소를 바꿨는데 B 에게 안 갔다 — 건별 주소 저장(setJobAddress)이 전파를 안 했고,
         *   고객 주소 전파는 **그 고객의 shareId 전부**로 보내 1차 협업까지 과전파였다. 둘을 이 한 함수로 좁힌다.
         *   @param jobDayStart 그 건 시공일 0시. **null 이면 날짜를 모르는 것 → 아무도 안 고른다**(전체로 퍼뜨리지 않음).
         *   @param representativeDay 대표 건 날짜(구버전 days 없는 줄의 폴백 — [coversDay]).
         *   같은 사장이 두 줄이면(shareId 같거나, shareId 없으면 번호 같음) 하나로.
         */
        fun forJob(
            raw: Collection<String>, customerId: Long, jobDayStart: Long?, representativeDay: Long?
        ): List<CollabAssignmentLine> {
            if (jobDayStart == null) return emptyList()
            return raw.mapNotNull { parse(it) }
                .filter { it.customerId == customerId && it.coversDay(jobDayStart, representativeDay) }
                .distinctBy { it.shareId.ifBlank { it.phone } }
        }

        /** [forJob] 중 서버에 말을 걸 수 있는(shareId 있는) 것의 shareId 들 — 우체통 전파 대상. */
        fun shareIdsForJob(
            raw: Collection<String>, customerId: Long, jobDayStart: Long?, representativeDay: Long?
        ): List<String> =
            forJob(raw, customerId, jobDayStart, representativeDay).map { it.shareId }.filter { it.isNotBlank() }.distinct()

        fun parse(raw: String): CollabAssignmentLine? {
            val p = raw.split('|')
            val id = p.getOrNull(0)?.toLongOrNull() ?: return null
            val phone = if (p.size >= 3) p[1].filter { it.isDigit() } else ""
            val name = if (p.size >= 3) p[2] else p.getOrNull(1).orEmpty()
            val shareId = p.getOrNull(3)?.trim().orEmpty()
            val days = p.getOrNull(4)?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()
            return CollabAssignmentLine(id, phone, name, shareId, days)
        }
    }
}
