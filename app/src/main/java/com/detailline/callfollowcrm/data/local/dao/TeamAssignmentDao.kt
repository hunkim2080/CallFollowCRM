package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamAssignmentDao {
    @Query("SELECT * FROM team_assignments")
    fun observeAll(): Flow<List<TeamAssignmentEntity>>

    @Query("SELECT * FROM team_assignments WHERE customerId = :customerId")
    suspend fun forCustomer(customerId: Long): List<TeamAssignmentEntity>

    @Query("SELECT * FROM team_assignments WHERE memberId = :memberId")
    suspend fun forMember(memberId: String): List<TeamAssignmentEntity>

    @Query("DELETE FROM team_assignments WHERE customerId = :customerId")
    suspend fun deleteForCustomer(customerId: Long)

    @Insert
    suspend fun insertAll(rows: List<TeamAssignmentEntity>)
}
