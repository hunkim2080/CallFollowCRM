package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.SpamPhoneDao
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 광고/스팸 마킹된 phone suffix 관리.
 *   사장님 결정 2026-05-25: 미확인 카드에서만 영구 제외. 전체/카테고리 탭에는 그대로 표시.
 *
 * suffix 정규화는 호출자 책임 — HomeViewModel.phoneSuffix() 와 같은 정의 사용.
 */
class SpamPhoneRepository(private val dao: SpamPhoneDao) {

    val suffixes: Flow<Set<String>> = dao.observeSuffixes().map { it.toHashSet() }

    suspend fun mark(suffix: String) {
        if (suffix.isBlank()) return
        dao.insert(SpamPhoneEntity(phoneSuffix = suffix, markedAt = System.currentTimeMillis()))
    }

    suspend fun unmark(suffix: String) {
        if (suffix.isBlank()) return
        dao.deleteBySuffix(suffix)
    }
}
