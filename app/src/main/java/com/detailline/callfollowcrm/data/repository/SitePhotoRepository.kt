package com.detailline.callfollowcrm.data.repository

import android.content.Context
import android.net.Uri
import com.detailline.callfollowcrm.data.local.dao.SitePhotoDao
import com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 현장 사진 로컬 저장소 (2026-06-04). 갤러리에서 고른 사진을 앱 내부 저장소로 복사 후 경로 보관.
 *   - 외부 content:// URI 는 권한이 휘발되므로 반드시 복사해서 영구 보관.
 *   - 팀원↔사장님 공유는 서버 보강 후 별도 연동(team_site_photos).
 */
class SitePhotoRepository(
    private val context: Context,
    private val dao: SitePhotoDao
) {
    fun observe(customerId: Long): Flow<List<SitePhotoEntity>> = dao.observeByCustomer(customerId)

    private fun photoDir(): File = File(context.filesDir, "site_photos").apply { mkdirs() }

    /** 갤러리 등에서 고른 URI 를 내부 저장소로 복사 + DB 기록. 성공 시 true. */
    suspend fun addFromUri(customerId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val nowMs = System.currentTimeMillis()
            val file = File(photoDir(), "${customerId}_${nowMs}_${System.nanoTime()}.jpg")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || file.length() == 0L) {
                runCatching { file.delete() }
                return@runCatching false
            }
            dao.insert(
                SitePhotoEntity(customerId = customerId, filePath = file.absolutePath, createdAt = nowMs)
            )
            true
        }.getOrDefault(false)
    }

    /** DB 행 + 실제 파일 삭제. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        runCatching {
            dao.filePathOf(id)?.let { path -> runCatching { File(path).delete() } }
            dao.deleteById(id)
        }
        Unit
    }
}
