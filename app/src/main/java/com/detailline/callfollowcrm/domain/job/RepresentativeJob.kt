package com.detailline.callfollowcrm.domain.job

/**
 * 🏷️ **어느 건이 「대표」인가** — 한 손님에게 시공이 여러 번일 때 고객 카드가 가리키는 그 하나.
 *
 *   사장님 카드(이름·금액·일정)는 **한 손님에 하나**인데, 시공은 1차·2차·3차가 있다.
 *   그래서 "지금 이 손님의 대표 건은 무엇인가" 를 정해야 한다.
 *
 *   **규칙은 한 줄이다:** *앞으로 올 가장 가까운 건. 다 지났으면 가장 마지막 건.*
 *
 *   ⚠️ **여기서 틀리면 돈이 어긋난다.** 2026-09-17 에 일정만 옮기고 돈을 안 옮겨
 *   **잘못된 미수 알람**이 떴고, 2026-09-18 엔 2차를 취소했는데 **1차 잔금 기록이 사라졌다.**
 *   둘 다 "어느 건이 대표냐" 가 뒤틀린 결과다.
 *
 *   §12-B 로 세보니 **같은 규칙이 세 곳**에 조금씩 다른 글자로 적혀 있었다 (2026-10-03):
 *     · `JobRepository.syncRepresentativeFromCustomer` — `?: jobs.lastOrNull()`
 *     · `JobRepository.mirrorToRepresentativeJob`      — `?: jobs.lastOrNull()`
 *     · `JobRepository.representativeJobId`            — `?: jobs.last()`
 *   한쪽만 고치면 **고객 카드와 정산이 서로 다른 건을 가리킨다.** 그래서 한 곳으로 모았다.
 *
 *   이 파일은 **DB·안드로이드에 안 기댄다** — 그래서 단위 테스트가 전부 본다.
 */
object RepresentativeJob {

    /** 날짜만 아는 최소한의 모습. 진짜 `JobEntity` 를 안 끌어와도 셈이 된다. */
    interface Dated {
        val scheduledWorkDate: Long?
    }

    /**
     * 대표 건을 고른다. **차례대로 들어온 목록**(시공일 오름차순)을 받는다.
     *
     * @param jobs     그 손님의 시공 건들 — 시공일 **오름차순**
     * @param todayMs  오늘 0시 (`DateTimeUtils.startOfDay`)
     * @return 대표 건. 건이 하나도 없으면 null
     */
    fun <T : Dated> pick(jobs: List<T>, todayMs: Long): T? {
        if (jobs.isEmpty()) return null
        // 날짜가 없는 건(=아직 날짜를 안 잡음)은 0 으로 봐서 '지난 것' 쪽에 둔다.
        //   날짜를 안 잡은 건이 앞으로 올 시공보다 먼저 대표가 되면 안 된다.
        return jobs.firstOrNull { (it.scheduledWorkDate ?: 0L) >= todayMs } ?: jobs.last()
    }
}
