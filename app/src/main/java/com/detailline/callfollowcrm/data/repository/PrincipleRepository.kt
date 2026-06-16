package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.PrincipleDao
import com.detailline.callfollowcrm.data.local.entity.PrincipleEntity
import kotlinx.coroutines.flow.Flow

/**
 * "막내가 알아낸 사장님 원칙" 저장소 (2026-06-17). 발견 카드 ⭕ / 직접 추가로 쌓이고,
 * 켜진 원칙만 prepare-reply 에 실려 AI 답변의 판단 기준이 된다.
 */
class PrincipleRepository(private val dao: PrincipleDao) {

    fun observeAll(): Flow<List<PrincipleEntity>> = dao.observeAll()
    suspend fun count(): Int = dao.count()

    suspend fun add(text: String, source: String = "discovered"): Long {
        val t = text.trim()
        if (t.isBlank()) return -1L
        val now = System.currentTimeMillis()
        return dao.insert(
            PrincipleEntity(text = t, enabled = true, source = source, createdAt = now, updatedAt = now)
        )
    }

    suspend fun updateText(id: Long, text: String) {
        val cur = dao.findById(id) ?: return
        val t = text.trim()
        if (t.isBlank()) return
        dao.update(cur.copy(text = t, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val cur = dao.findById(id) ?: return
        dao.update(cur.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** prepare-reply 에 보낼 켜진 원칙 문장들. */
    suspend fun enabledTexts(): List<String> =
        runCatching { dao.listEnabled().map { it.text } }.getOrDefault(emptyList())
}
