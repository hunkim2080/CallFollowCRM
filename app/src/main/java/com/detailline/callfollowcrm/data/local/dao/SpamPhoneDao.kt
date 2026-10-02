package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamPhoneDao {

    @Query("SELECT phoneSuffix FROM spam_phones")
    fun observeSuffixes(): Flow<List<String>>

    /**
     * 👥 **지인(옛 「사생활」) 번호만.** (2026-10-02 사장님 "사생활을 없애고 지인으로 통일하자")
     *   이 번호들은 상담함·자동문자·AI 에서 빠지지만 **문자함 [지인] 에는 보여야 한다** —
     *   사장님이 예전에 찍어둔 것까지 한 목록에서 다 보시게.
     */
    @Query("SELECT phoneSuffix FROM spam_phones WHERE kind = 'personal'")
    fun observePersonalSuffixes(): Flow<List<String>>

    /** '스팸 목록' 화면용 — 최근 등록순 전체. (2026-06-23 사장님) */
    @Query("SELECT * FROM spam_phones ORDER BY markedAt DESC")
    fun observeAll(): Flow<List<SpamPhoneEntity>>

    @Query("SELECT phoneSuffix FROM spam_phones")
    suspend fun getAllSuffixes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamPhoneEntity)

    @Query("DELETE FROM spam_phones WHERE phoneSuffix = :suffix")
    suspend fun deleteBySuffix(suffix: String)
}
