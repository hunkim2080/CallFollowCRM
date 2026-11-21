package com.detailline.callfollowcrm.data.repository

import android.content.Context
import com.detailline.callfollowcrm.data.local.dao.RepQuestionDao
import com.detailline.callfollowcrm.data.local.entity.RepQuestionEntity
import com.detailline.callfollowcrm.domain.faq.RepQuestionRules
import com.detailline.callfollowcrm.util.TemplatePhotoStore
import kotlinx.coroutines.flow.Flow

/**
 * 💬 대표질문(FAQ) 저장소 — 관리 화면·등록 폼·채팅 시트가 **이 한 곳**을 본다.
 *
 *   글자 수 한도([RepQuestionRules])는 화면에서도 막지만 **여기서 한 번 더** 자른다 — 어느 길로 들어와도 같게.
 *   사진 파일은 `TemplatePhotoStore`(문구 사진과 같은 창고)에 있고, 줄을 지우거나 사진을 바꾸면 **옛 파일도 치운다**.
 *   저장 실패는 **삼키지 않는다** — 부르는 ViewModel 이 `SaveGuard` 로 화면에 말한다(§13①).
 */
class RepQuestionRepository(
    private val context: Context,
    private val dao: RepQuestionDao
) {
    fun observeAll(): Flow<List<RepQuestionEntity>> = dao.observeAll()

    /** 등록된 분류들(빈 분류 제외) — 등록 폼이 "이미 쓰던 분류" 칩으로 보여준다. */
    fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    suspend fun findById(id: Long): RepQuestionEntity? = dao.findById(id)

    /** 새로 등록 — 맨 아래에 붙는다. */
    suspend fun add(question: String, answer: String, imageUri: String?, category: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            RepQuestionEntity(
                question = RepQuestionRules.clampQuestion(question).trim(),
                answer = RepQuestionRules.clampAnswer(answer).trim(),
                imageUri = imageUri,
                category = RepQuestionRules.clampCategory(category),
                sortOrder = dao.maxSortOrder() + 1,
                createdAt = now, updatedAt = now
            )
        )
    }

    /** 고치기 — 순서·만든 시각은 그대로. 사진이 바뀌었으면 옛 파일을 치운다. */
    suspend fun edit(id: Long, question: String, answer: String, imageUri: String?, category: String) {
        val old = dao.findById(id) ?: error("대표질문 $id 가 없습니다")
        dao.update(
            old.copy(
                question = RepQuestionRules.clampQuestion(question).trim(),
                answer = RepQuestionRules.clampAnswer(answer).trim(),
                imageUri = imageUri,
                category = RepQuestionRules.clampCategory(category),
                updatedAt = System.currentTimeMillis()
            )
        )
        if (old.imageUri != null && old.imageUri != imageUri) discardImage(old.imageUri)
    }

    suspend fun delete(entity: RepQuestionEntity) {
        dao.deleteById(entity.id)
        entity.imageUri?.let { discardImage(it) }
    }

    suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    /** 안 쓰게 된 사진 파일 치우기 — **지워도 되는 것**이라 실패해도 조용히(남으면 용량만 조금). */
    fun discardImage(uri: String) {
        runCatching { TemplatePhotoStore.fileFor(context, uri)?.delete() }
    }
}
