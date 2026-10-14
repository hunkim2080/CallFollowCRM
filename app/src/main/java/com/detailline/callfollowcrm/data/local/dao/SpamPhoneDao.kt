package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamPhoneDao {

    // 스팸 집합 — parcel/ad(문자함 라벨 교정)는 스팸이 아니므로 제외. personal 은 그대로(기존 동작). (2026-10-14 사장님)
    @Query("SELECT phoneSuffix FROM spam_phones WHERE kind NOT IN ('parcel', 'ad')")
    fun observeSuffixes(): Flow<List<String>>

    /**
     * 👥 **지인(옛 「사생활」) 번호만.** (2026-10-02 사장님 "사생활을 없애고 지인으로 통일하자")
     *   이 번호들은 상담함·자동문자·AI 에서 빠지지만 **문자함 [지인] 에는 보여야 한다** —
     *   사장님이 예전에 찍어둔 것까지 한 목록에서 다 보시게.
     */
    @Query("SELECT phoneSuffix FROM spam_phones WHERE kind = 'personal'")
    fun observePersonalSuffixes(): Flow<List<String>>

    /** 📦 문자함 [택배]로 사장님이 직접 옮긴 번호. (2026-10-14 사장님 "틀린 라벨 고치기") */
    @Query("SELECT phoneSuffix FROM spam_phones WHERE kind = 'parcel'")
    fun observeParcelSuffixes(): Flow<List<String>>

    /** 📢 문자함 [광고]로 사장님이 직접 옮긴 번호(자동 택배 판정을 덮음). (2026-10-14 사장님) */
    @Query("SELECT phoneSuffix FROM spam_phones WHERE kind = 'ad'")
    fun observeAdSuffixes(): Flow<List<String>>

    /** '스팸 목록' 화면용 — 최근 등록순. parcel/ad(라벨 교정)는 스팸 목록에 안 섞는다. (2026-06-23 사장님 · 2026-10-14) */
    @Query("SELECT * FROM spam_phones WHERE kind NOT IN ('parcel', 'ad') ORDER BY markedAt DESC")
    fun observeAll(): Flow<List<SpamPhoneEntity>>

    @Query("SELECT phoneSuffix FROM spam_phones")
    suspend fun getAllSuffixes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamPhoneEntity)

    @Query("DELETE FROM spam_phones WHERE phoneSuffix = :suffix")
    suspend fun deleteBySuffix(suffix: String)
}
