package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssuedDocDao {
    /** 고객상세 발행 이력 — 그 고객에게 발행한 문서(최신순). */
    @Query("SELECT * FROM issued_docs WHERE customerId = :customerId ORDER BY issuedAtMs DESC")
    fun observeByCustomer(customerId: Long): Flow<List<IssuedDocEntity>>

    /** 채팅 타임라인 카드(다음 단계) — 끝 8자리 매칭. */
    @Query("SELECT * FROM issued_docs WHERE phoneSuffix = :suffix ORDER BY issuedAtMs DESC")
    fun observeBySuffix(suffix: String): Flow<List<IssuedDocEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: IssuedDocEntity): Long

    /** 같은 접수서 링크(token) 카드 모두 — 수정 시 하나로 합치기(upsert+중복정리)용. 오래된 순. */
    @Query("SELECT * FROM issued_docs WHERE token = :token ORDER BY issuedAtMs ASC")
    suspend fun findAllByToken(token: String): List<IssuedDocEntity>

    @Query("DELETE FROM issued_docs WHERE id = :id")
    suspend fun delete(id: Long)
}
