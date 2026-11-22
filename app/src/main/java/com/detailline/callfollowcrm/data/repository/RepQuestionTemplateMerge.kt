package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first

/** 앱 시작 시 한 번. 원본 행·사진은 백업으로 남기고, 새 문구는 모두 비공개다. */
internal suspend fun mergeRepQuestionsIntoTemplates(
    preferences: AppPreferences,
    questions: RepQuestionRepository,
    templates: MessageTemplateRepository,
    attachments: TemplateAttachmentRepository,
    transaction: suspend (suspend () -> Unit) -> Unit
) {
    if (preferences.repQuestionsMergedIntoTemplates) return
    // 재시도 판정은 트랜잭션 **밖**(suspend 쿼리). Room과 prefs는 함께 커밋되지 않는다 —
    //   DB 커밋 직후 종료됐으면 완료 flag만 재시도한다. 영수증은 DB 커밋 전에 저장하므로,
    //   롤백됐으면 그 행이 없어(findById null) 다시 복사한다.
    val receipt = preferences.repQuestionsTemplateMergeReceipt
    val alreadyCopied = receipt != null && receipt.all { entry ->
        val id = entry.substringBefore(':').toLongOrNull() ?: return@all false
        val createdAt = entry.substringAfter(':').toLongOrNull() ?: return@all false
        templates.findById(id)?.createdAt == createdAt
    }
    if (!alreadyCopied) {
        // ⚠️ rep_questions 읽기도 트랜잭션 **밖**. Room `withTransaction` 안에서 관찰 Flow(`observeAll().first()`)를
        //   부르면 교착 위험(트랜잭션 스레드가 Flow 쿼리를 기다린다). 원본은 안 바꾸니 밖 읽기로 충분하고,
        //   재시도(alreadyCopied)면 아예 안 읽는다(= observeAll 은 복사할 때 한 번만).
        val source = questions.observeAll().first()
        transaction {
            val now = System.currentTimeMillis()
            val copied = mutableSetOf<String>()
            source.forEach { question ->
                val newId = templates.insert(
                    MessageTemplateEntity(
                        title = question.question, body = question.answer,
                        category = question.category, isDefault = false, isActive = true,
                        createdAt = now, updatedAt = now, isPublic = false
                    )
                )
                question.imageUri?.let { uri ->
                    attachments.add(newId, uri, "사진", "image/*", now)
                }
                copied += "$newId:$now"
            }
            preferences.repQuestionsTemplateMergeReceipt = copied
        }
    }
    preferences.repQuestionsMergedIntoTemplates = true
}
