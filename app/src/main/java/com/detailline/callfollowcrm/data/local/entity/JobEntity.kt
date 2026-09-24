package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 시공 "건(job)" — 한 고객이 여러 번(다른 날·다른 장소) 시공받는 경우의 **이력 보관**용. DB v42 (2026-07-20).
 *
 * 배경: 기존엔 시공 정보가 전부 CustomerEntity 단일 컬럼(scheduledWorkDate/address/금액/workCompletedAt)에만 있어,
 *   같은 고객에 두 번째 시공을 잡으면 첫 번째가 덮여 사라졌다. (프로토는 원래 "건 중심" — jobs[day] 배열)
 *
 * Phase 1(최소·안전): 이 테이블은 **완료된 지난 시공을 보관**한다. 현재 진행/예정 건은 여전히 CustomerEntity 가 들고 있고
 *   (캘린더·정산은 그대로 CustomerEntity 를 읽음), "완료된 고객에 새 일정 등록" 시 완료 건을 여기로 옮겨 담고
 *   CustomerEntity 를 새 건용으로 리셋한다 → 첫 시공이 유실되지 않고 "지난 시공 N건"으로 보인다.
 *   컬럼 구성은 CustomerEntity 의 시공 관련 필드와 1:1.
 */
@Entity(
    tableName = "jobs",
    indices = [
        Index(value = ["customerId"], name = "index_jobs_customerId"),
        Index(value = ["scheduledWorkDate"], name = "index_jobs_scheduledWorkDate")
    ]
)
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val scheduledWorkDate: Long? = null,
    val scheduledWorkMinutes: Int? = null,
    val scheduledWorkDays: Int = 1,
    val address: String? = null,
    val totalAmount: Long? = null,
    val depositAmount: Long? = null,
    val depositPaidAt: Long? = null,
    val balanceAmount: Long? = null,
    val balancePaidAt: Long? = null,
    val workCompletedAt: Long? = null,
    /** 이 건 전용 메모 — 고객 공통이 아니라 **현장마다** 따로. (v50, 2026-09-14 사장님) */
    val memo: String = "",
    /**
     * 이 건의 구글 캘린더 일정 번호. (v55, 2026-09-18)
     *   전엔 고객 표에 칸이 하나뿐(`customers.workCalendarEventId`)이라, 2차를 잡으면
     *   **1차 일정이 2차 날짜로 옮겨졌다**(새 일정이 안 생김). 건마다 따로 들고 있어야 한다.
     */
    val calendarEventId: String? = null,
    /**
     * 예약을 **취소한 시각**. (v57, 2026-09-18 사장님)
     *   취소는 기록을 남기려고 날짜만 비운다 → 날짜 없는 건이 '아직 날짜를 안 정한 새 건'과
     *   구별이 안 돼, 취소한 건이 탭에서 차수를 차지했다. 이 칸으로 둘을 가른다.
     *   다시 날짜를 잡으면 null 로 돌아간다(되살리기).
     */
    val cancelledAt: Long? = null,
    /**
     * **현장 번호** — 001, 002 … 완료한 순서대로 붙는 일련번호. (v58, 2026-09-24 사장님)
     *   "이번 달 12집" 은 이번 달로 끝나지만 **번호는 끝이 없다.** 039, 040 이 계속 생긴다.
     *   ⚠️ **번호는 '시공한 날짜' 순이다.** (2026-09-24 바뀜)
     *     처음엔 '완료를 누른 순서' 로 박고 안 바꿨다. 그런데 사장님은 그날 앱을 못 열면
     *     며칠 뒤에 누르신다 — 그러면 8월 현장이 9월 현장보다 뒤 번호를 받아
     *     목록이 뒤죽박죽이 된다(성북 8/29 가 025). 번호는 "내가 몇 번째로 다녀온 집인가" 라서
     *     다녀온 날 순서가 맞다.
     *     옛 건을 뒤늦게 완료하면 그 뒤 번호들이 한 칸씩 밀린다 — 번호가 **내 기록 목록 안에서만**
     *     보이기 때문에 괜찮다(인증샷에는 번호를 안 넣는다. 큰 숫자는 '올해 N집' 등을 고른다).
     *     매기는 곳 = JobRepository.recordNumbersByWorkDate (단위 테스트 있음).
     *   null = 아직 완료 안 한 건(또는 옛 데이터 중 완료 시각이 없는 건).
     */
    val recordNo: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)
