package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.MessageTemplateDao
import com.detailline.callfollowcrm.data.local.dao.RepQuestionDao
import com.detailline.callfollowcrm.data.local.dao.TemplateAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.entity.RepQuestionEntity
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

/** 실제 이사 함수·저장소를 사용한다. DAO와 트랜잭션 경계만 메모리로 대체해 실패 시점을 주입한다. */
class RepQuestionTemplateMergeTest {
    private class Fixture {
        val prefs = mock<AppPreferences>()
        val questionDao = mock<RepQuestionDao>()
        val templateDao = mock<MessageTemplateDao>()
        val attachmentDao = mock<TemplateAttachmentDao>()
        val originals = listOf(
            RepQuestionEntity(id = 7, question = "사진 없는 질문", answer = "첫 답", category = "가격"),
            RepQuestionEntity(id = 8, question = "사진 있는 질문", answer = "**둘째 답**",
                category = "시공", imageUri = "content://backup/photo")
        )
        val rows = linkedMapOf<Long, MessageTemplateEntity>()
        val photos = mutableListOf<TemplateAttachmentEntity>()
        var completed = false
        var receipt: Set<String>? = null
        var failPhoto = false
        var failCommit = false
        var failFlag = false
        var failReceipt = false
        var nextId = 100L

        suspend fun setup() {
            whenever(prefs.repQuestionsMergedIntoTemplates).thenAnswer { completed }
            whenever(prefs.repQuestionsTemplateMergeReceipt).thenAnswer { receipt }
            doAnswer {
                if (failFlag) error("flag write failed")
                completed = it.getArgument(0)
                null
            }.whenever(prefs).repQuestionsMergedIntoTemplates = any()
            doAnswer {
                if (failReceipt) error("receipt write failed")
                receipt = it.getArgument<Set<String>?>(0)?.toSet()
                null
            }.whenever(prefs).repQuestionsTemplateMergeReceipt = anyOrNull()
            whenever(questionDao.observeAll()).thenReturn(flowOf(originals))
            whenever(templateDao.insert(any())).thenAnswer {
                val id = ++nextId
                rows[id] = it.getArgument<MessageTemplateEntity>(0).copy(id = id)
                id
            }
            whenever(templateDao.findById(any())).thenAnswer { rows[it.getArgument<Long>(0)] }
            whenever(attachmentDao.findByTemplate(any())).thenAnswer {
                val id = it.getArgument<Long>(0)
                photos.filter { p -> p.templateId == id }
            }
            whenever(attachmentDao.insert(any())).thenAnswer {
                if (failPhoto) error("photo write failed")
                photos += it.getArgument<TemplateAttachmentEntity>(0)
                photos.size.toLong()
            }
        }

        suspend fun merge() {
            mergeRepQuestionsIntoTemplates(prefs, RepQuestionRepository(mock(), questionDao),
                MessageTemplateRepository(templateDao), TemplateAttachmentRepository(attachmentDao)) { block ->
                val beforeRows = rows.toMap()
                val beforePhotos = photos.toList()
                try {
                    block()
                    if (failCommit) error("DB commit failed")
                } catch (e: Throwable) {
                    rows.clear(); rows.putAll(beforeRows)
                    photos.clear(); photos.addAll(beforePhotos)
                    throw e
                }
            }
            verify(questionDao, never()).deleteById(any())
            verify(questionDao, never()).update(any())
        }

        suspend fun expectFailure() {
            try { merge(); fail("expected injected failure") } catch (_: IllegalStateException) { }
            assertFalse(completed)
        }
    }

    @Test fun `all copies are private and source text and photo stay intact - once only`() = runBlocking {
        val f = Fixture().apply { setup() }
        f.merge()
        val copies = f.rows.values.toList()
        assertEquals(f.originals.map { it.question }, copies.map { it.title })
        assertEquals(f.originals.map { it.answer }, copies.map { it.body })
        assertEquals(f.originals.map { it.category }, copies.map { it.category })
        assertTrue(copies.all { !it.isPublic && !it.isDefault && it.isActive })
        val photo = f.photos.single()
        assertEquals(copies[1].id, photo.templateId)
        assertEquals(f.originals[1].imageUri, photo.fileUri)
        assertEquals("사진", photo.displayName)
        assertEquals("image/*", photo.mimeType)
        assertEquals(0, photo.sortOrder)
        assertEquals(copies[1].createdAt, photo.createdAt)
        f.merge()
        assertEquals(2, f.rows.size)
        assertEquals(1, f.photos.size)
        verify(f.questionDao, times(1)).observeAll()
        Unit
    }

    @Test fun `photo failure rolls back all rows and retries successfully`() = runBlocking {
        val f = Fixture().apply { setup(); failPhoto = true }
        f.expectFailure()
        assertTrue(f.rows.isEmpty())
        assertTrue(f.photos.isEmpty())
        f.failPhoto = false
        f.merge()
        assertEquals(2, f.rows.size)
        assertEquals(1, f.photos.size)
    }

    @Test fun `DB rollback after receipt is written does not skip copying on restart`() = runBlocking {
        val f = Fixture().apply { setup(); failCommit = true }
        f.expectFailure()
        assertNotNull(f.receipt)
        assertTrue(f.rows.isEmpty())
        f.failCommit = false
        f.merge()
        assertTrue(f.completed)
        assertEquals(2, f.rows.size)
    }

    @Test fun `flag failure after DB commit retries only the flag without duplicates`() = runBlocking {
        val f = Fixture().apply { setup(); failFlag = true }
        f.expectFailure()
        val ids = f.rows.keys.toSet()
        assertEquals(2, ids.size)
        f.failFlag = false
        f.merge()
        assertTrue(f.completed)
        assertEquals(ids, f.rows.keys)
        assertEquals(1, f.photos.size)
        verify(f.questionDao, times(1)).observeAll()
        Unit
    }

    @Test fun `receipt write failure prevents DB commit`() = runBlocking {
        val f = Fixture().apply { setup(); failReceipt = true }
        f.expectFailure()
        assertTrue(f.rows.isEmpty())
        f.failReceipt = false
        f.merge()
        assertTrue(f.completed)
    }

    @Test fun `empty source completes without creating a template`() = runBlocking {
        val f = Fixture().apply { setup() }
        whenever(f.questionDao.observeAll()).thenReturn(flowOf(emptyList()))
        f.merge()
        assertTrue(f.completed)
        assertTrue(f.rows.isEmpty())
        assertTrue(f.photos.isEmpty())
    }
}
