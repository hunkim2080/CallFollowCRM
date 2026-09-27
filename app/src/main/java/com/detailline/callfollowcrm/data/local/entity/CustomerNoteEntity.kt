package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 📝 **쪽지 한 장** — 손님/현장 메모를 한 덩어리 글에서 **시각이 붙는 쪽지**로. (2026-09-27 사장님)
 *
 * 사장님: "계속 상황이 바뀌거나 업데이트할 내용이 있는데, 메모란에 적으니까
 *         **언제 내가 작성했는지 모르겠고** 눈에 안 들어오는 것 같은데."
 *
 * 사장님이 적는 건 **사건**이다 — 「오늘 2시로 바뀜」, 「어제 잔금 얘기」.
 * 사건은 **시각이 반**이고, 한 줄씩 쌓여야 읽힌다. 한 덩어리 글로 적으면 문장 속에 묻힌다.
 *
 * ⚠️ `customers.memo` / `jobs.memo` 는 **없애지 않는다.** 캘린더·미러링·AI 답변추천·웹피드가
 *   그 글을 읽고 있다. 쪽지가 바뀔 때마다 **사람이 읽을 수 있는 글로 다시 적어 넣는다**
 *   ([CustomerNoteRepository.renderMirror]). 그래서 읽는 쪽은 한 곳도 안 깨진다.
 */
@Entity(
    tableName = "customer_notes",
    indices = [Index("customerId"), Index("jobId")]
)
data class CustomerNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 어느 손님의 쪽지인가. 현장 쪽지도 손님을 함께 매단다(합치기·지우기 때 따라가라고). */
    val customerId: Long,
    /** 📍 그 **건**에서만 쓰는 쪽지면 건 id. null = 👤 손님 쪽지(현장이 바뀌어도 그대로). */
    val jobId: Long? = null,
    val body: String,
    /** 📌 늘 봐야 하는 것 — 계좌·현관 비번처럼. 시각과 상관없이 맨 위에 못 박힌다. */
    val pinned: Boolean = false,
    /**
     * 어디서 적혔나.
     *   `""`        = 사장님이 직접
     *   `"call"`    = 통화 카드에서 (「통화 중」 딱지가 붙는다)
     *   `"legacy"`  = 옛 메모 한 덩어리를 옮겨온 것 (적은 시각을 모른다 → 「예전에 적음」)
     */
    val source: String = "",
    /** 적은 시각. `legacy` 는 0 — 시각을 모르므로 맨 아래로 간다. */
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val SOURCE_CALL = "call"
        const val SOURCE_LEGACY = "legacy"
    }
}
