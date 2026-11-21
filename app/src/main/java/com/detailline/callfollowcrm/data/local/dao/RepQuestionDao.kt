package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.RepQuestionEntity
import kotlinx.coroutines.flow.Flow

/** 💬 대표질문(FAQ). 순서는 [RepQuestionEntity.sortOrder] 오름차순(같으면 먼저 만든 것). */
@Dao
interface RepQuestionDao {
    @Query("SELECT * FROM rep_questions ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<RepQuestionEntity>>

    @Query("SELECT * FROM rep_questions WHERE id = :id")
    suspend fun findById(id: Long): RepQuestionEntity?

    /** 맨 뒤 순서. 하나도 없으면 -1 (→ 첫 줄이 0). */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM rep_questions")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(entity: RepQuestionEntity): Long

    @Update
    suspend fun update(entity: RepQuestionEntity)

    @Query("DELETE FROM rep_questions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE rep_questions SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** 끌어서 바꾼 순서를 **한 번에** 적는다 — 중간에 끊겨 반만 바뀌는 일이 없게. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}
