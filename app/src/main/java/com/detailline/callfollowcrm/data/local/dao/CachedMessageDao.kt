package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.CachedMessageEntity

@Dao
interface CachedMessageDao {
    @Query("SELECT * FROM cached_messages WHERE phoneSuffix = :suffix ORDER BY dateMs DESC LIMIT :limit")
    suspend fun queryBySuffix(suffix: String, limit: Int = 500): List<CachedMessageEntity>

    @Query("SELECT * FROM cached_messages WHERE phoneSuffix = :suffix AND isMms = :isMms ORDER BY dateMs DESC LIMIT :limit")
    suspend fun queryBySuffixAndType(suffix: String, isMms: Boolean, limit: Int = 500): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CachedMessageEntity>)

    /** 특정 번호의 캐시 다 비우기. 백그라운드 동기화 시 stale 제거 용도. */
    @Query("DELETE FROM cached_messages WHERE phoneSuffix = :suffix")
    suspend fun clearForSuffix(suffix: String)

    /** 특정 번호의 SMS 만 / MMS 만 비우기. 부분 동기화 (3-stage 로드) 용도. */
    @Query("DELETE FROM cached_messages WHERE phoneSuffix = :suffix AND isMms = :isMms")
    suspend fun clearForSuffixByType(suffix: String, isMms: Boolean)

    /**
     * 특정 번호의 SMS 중 *시스템 provider 출신*(systemId >= 0)만 비운다.
     * 음수 systemId = 앱에서 보냈지만 기본 문자앱이 아니라 시스템 문자함에 못 적은 발신 보존본 → 살린다.
     * (replaceSmsOnlyForSuffix 가 provider 결과로 갱신할 때 우리 발신본이 함께 지워지던 버그 fix.)
     */
    @Query("DELETE FROM cached_messages WHERE phoneSuffix = :suffix AND isMms = 0 AND systemId >= 0")
    suspend fun clearProviderSmsForSuffix(suffix: String)

    /** 로컬 보존된 발신(provider 미반영, systemId < 0) 메시지만 최신순. */
    @Query("SELECT * FROM cached_messages WHERE phoneSuffix = :suffix AND systemId < 0 ORDER BY dateMs DESC LIMIT :limit")
    suspend fun queryLocalSentBySuffix(suffix: String, limit: Int = 200): List<CachedMessageEntity>

    /** 가장 오래된 캐시 N건 삭제 (LRU 비슷한 cap 관리). 다음 마일스톤에서 활용 가능. */
    @Query("DELETE FROM cached_messages WHERE localId IN (SELECT localId FROM cached_messages ORDER BY cachedAtMs ASC LIMIT :n)")
    suspend fun trimOldest(n: Int)
}
