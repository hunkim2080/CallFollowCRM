package com.detailline.callfollowcrm.domain.collab

/**
 * 협업 배정이 `dayStart` 날짜에 걸리나 — 배지 표시·일정변경·주소 전파 대상 선정이 **같은 규칙**을 쓰도록 한 곳.
 *   days 가 있으면 **그 날들에만**. 비어 있으면 **어느 날에도 안 걸린다**(떠돌이 — [CollabAssignmentLine.unassigned]).
 *
 * 🐞 2026-10-15 사장님 실기: 1차·2차 둘 다 완료인 손님에서 **1차 협업 사장이 사라지고 2차 카드에 두 이름**이 떴다.
 *   전엔 days 가 비면 「대표 건 날짜」로 폴백했는데, 둘 다 완료면 대표 = 더 나중 건(2차) 이라
 *   날짜 꼬리표 없는 1차 줄이 2차로 **따라갔다**(서버 공유는 멀쩡 — 보이는 자리만 틀렸다).
 *   사장님 결정: **날짜 없는 협업은 어디에도 자동으로 안 붙이고, 사장님이 「이 건」을 직접 고른다.**
 */
fun collabCoversDay(days: Set<Long>, dayStart: Long): Boolean = days.isNotEmpty() && dayStart in days

/**
 * 옛 입구 — `representativeDay` 는 **더 이상 쓰지 않는다**(폴백 없음). ScheduleScreen 호출부가 아직 이 모양이라 남겨둔 다리.
 *   새 코드는 [collabCoversDay] 두 인자짜리를 쓴다. (ScheduleScreen 정리 때 이 다리도 지운다)
 */
fun collabCoversDay(days: Set<Long>, dayStart: Long, @Suppress("UNUSED_PARAMETER") representativeDay: Long?): Boolean =
    collabCoversDay(days, dayStart)

/**
 * 협업 배정 한 줄 — `preferences.collabAssignments` 의 문자열 형식 파싱/포맷/판단을 **한 곳**에서.
 *   형식: `"customerId|phone|name|shareId|days"`  (days = startOfDay 들을 콤마로)
 *   구버전 `"id|name"`(2토큰)·`"id|phone|name|shareId"`(4토큰, days 없음)도 호환.
 *
 * 핵심 원칙(2026-10-09 사장님): **협업은 "고객"이 아니라 "그 시공 건(날짜)"에 붙는다.**
 *   그래서 어느 날 건인지는 `days` 로 구분한다 — 같은 고객의 1차·2차가 협업자를 섞지 않게.
 *   days 가 비어 있으면(구버전·백필 전) **떠돌이** — 어느 건 카드에도 안 뜨고, 고객 상세의 「어느 건인지 모르는 협업」에서
 *   사장님이 건을 고르면 그 건 날짜가 적힌다([assignDays]). shareId 가 있고 서버에 날짜가 있으면
 *   ScheduleViewModel 이 먼저 자동으로 채운다(자가치유) — 그걸로 안 되는 것만 손으로.
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

    /** 날짜 꼬리표가 없어 어느 건에도 안 붙는 줄. */
    val isUnassigned: Boolean get() = days.isEmpty()

    /** 이 배정이 `dayStart` 날짜의 그 건 카드에 떠야 하나 — days 가 있으면 그 날들에만, 비면 안 뜬다. */
    fun coversDay(dayStart: Long): Boolean = collabCoversDay(days, dayStart)

    /** 같은 사장님 줄인가 — shareId 가 있으면 그걸로, 없으면(아주 옛 줄) 번호 숫자로. */
    fun sameAs(other: CollabAssignmentLine): Boolean =
        customerId == other.customerId &&
            (if (shareId.isNotBlank() || other.shareId.isNotBlank()) shareId == other.shareId
             else phone.filter { it.isDigit() } == other.phone.filter { it.isDigit() })

    companion object {
        /**
         * 🎯 **이 건(날짜)에 걸린 협업 배정만** — 카드의 협업 칩과 주소/일정 전파가 **같은 타깃팅**을 쓴다(§12). (2026-10-15)
         *   왜: A 가 2차 건의 주소를 바꿨는데 B 에게 안 갔다 — 건별 주소 저장(setJobAddress)이 전파를 안 했고,
         *   고객 주소 전파는 **그 고객의 shareId 전부**로 보내 1차 협업까지 과전파였다. 둘을 이 한 함수로 좁힌다.
         *   @param jobDayStart 그 건 시공일 0시. **null 이면 날짜를 모르는 것 → 아무도 안 고른다**(전체로 퍼뜨리지 않음).
         *   같은 사장이 두 줄이면(shareId 같거나, shareId 없으면 번호 같음) 하나로.
         */
        fun forJob(raw: Collection<String>, customerId: Long, jobDayStart: Long?): List<CollabAssignmentLine> =
            forJob(raw, customerId, jobDayStart?.let { setOf(it) } ?: emptySet())

        /**
         * 🎯 **이 건의 날짜들 중 하나라도** 맡은 협업 — 멀티데이 건(2일 이상)에서 **2일차만 맡은 협업**도 그 건에 뜬다. (2026-10-31 사장님)
         *   🐞 전엔 `jobDayStart`(시작일) **한 날만** 봐서, 11/12~13 건에 11/13만 부른 협업이 건 카드/전파에서 사라졌다.
         *   날짜 없는 떠돌이(days 빈 것)는 제외. 같은 사장은 하나로. 서로 다른 건은 날짜가 안 겹쳐 안 섞인다(1차/2차 분리 유지).
         */
        fun forJob(raw: Collection<String>, customerId: Long, jobDays: Set<Long>): List<CollabAssignmentLine> {
            if (jobDays.isEmpty()) return emptyList()
            return raw.mapNotNull { parse(it) }
                .filter { it.customerId == customerId && it.days.isNotEmpty() && it.days.any { d -> d in jobDays } }
                .distinctBy { it.shareId.ifBlank { it.phone } }
        }

        /** [forJob] 중 서버에 말을 걸 수 있는(shareId 있는) 것의 shareId 들 — 우체통 전파 대상. (single-day 입구) */
        fun shareIdsForJob(raw: Collection<String>, customerId: Long, jobDayStart: Long?): List<String> =
            shareIdsForJob(raw, customerId, jobDayStart?.let { setOf(it) } ?: emptySet())

        /** [forJob] (건 날짜 집합) 중 shareId 있는 것들 — 멀티데이 건 주소/일정 전파가 2일차 협업까지 닿게. (2026-10-31) */
        fun shareIdsForJob(raw: Collection<String>, customerId: Long, jobDays: Set<Long>): List<String> =
            forJob(raw, customerId, jobDays).map { it.shareId }.filter { it.isNotBlank() }.distinct()

        /**
         * 🧭 **떠돌이** — 이 고객의 줄 중 날짜 꼬리표가 없는 것(어느 건 카드에도 안 뜨는 것). 고객 상세가 보여주고
         *   사장님이 「이 건」을 고르면 [assignDays] 로 붙인다. 같은 사장은 하나로.
         */
        fun unassigned(raw: Collection<String>, customerId: Long): List<CollabAssignmentLine> =
            raw.mapNotNull { parse(it) }
                .filter { it.customerId == customerId && it.isUnassigned }
                .distinctBy { it.shareId.ifBlank { it.phone } }

        /**
         * 「이 건에 붙이기」 — `line` 과 같은 사장님 줄(들)에 `days` 를 적어 되돌려준다. 줄 수는 그대로(바꿔 끼움).
         *   days 가 비면 아무것도 안 바꾼다(떠돌이를 떠돌이로 되돌리는 길은 없다 — 해제는 카드에서).
         */
        fun assignDays(raw: Set<String>, line: CollabAssignmentLine, days: Set<Long>): Set<String> {
            if (days.isEmpty()) return raw
            return raw.map { r ->
                val p = parse(r) ?: return@map r
                if (p.sameAs(line)) p.withDays(days).format() else r
            }.toSet()
        }

        /**
         * 그 건의 날짜들(0시) — 시공일부터 기간(일수)만큼. 협업을 건에 붙일 때 적는 days 는 **이걸로만** 만든다
         *   (카드 협업 칩·「이 건에 붙이기」·일정 시트 초대가 같은 셈). 시공일 없으면 빈 집합(= 떠돌이).
         */
        fun daysOfJob(jobDayStart: Long?, nDays: Int): Set<Long> {
            if (jobDayStart == null) return emptySet()
            return (0 until nDays.coerceAtLeast(1))
                .map { jobDayStart + it * com.detailline.callfollowcrm.util.DateTimeUtils.DAY_MS }.toSet()
        }

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
