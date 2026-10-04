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

    /** 📮 사장님이 「그만 보내기」 한 사진 — 도장(-1)을 찍어 다음 스캔에 다시 안 뜨게. (우체통 2단계) */
    suspend fun markGivenUp(photoId: Long) = dao.markUploaded(photoId, -1L)

    /** 전부, 올린 순서대로. 「내 기록」이 대표 사진을 고를 때 쓴다. */
    fun observeAllOldestFirst(): Flow<List<SitePhotoEntity>> = dao.observeAllOldestFirst()

    companion object {
        /**
         * **그 현장의 대표 사진 고르는 규칙 — 여기 하나뿐이다.**
         *
         * · 그 건(件)에 붙은 사진이 먼저. 없으면 건 표시가 없는 옛 사진.
         * · 그 안에서는 **제일 먼저 올린 것** (사장님 확정: "내가 첫번째로 넣는게 대표사진").
         *
         * ⚠️ 2026-09-25 점검: 고객상세의 「대표」 딱지와 인증샷이 **서로 다른 규칙**을 쓰고 있었다.
         *   옛 사진과 새 사진이 섞이면 딱지는 이걸, 인증샷은 저걸 가리켰다.
         *   🔒 새로 대표 사진을 쓰는 곳이 생기면 **반드시 이 함수를** 쓴다.
         *
         * @param photos 올린 순서(오래된 것 먼저)로 정렬된 목록이어야 한다.
         */
        fun representativeOf(
            photos: List<SitePhotoEntity>, customerId: Long, jobId: Long?
        ): SitePhotoEntity? =
            photos.firstOrNull { it.jobId != null && it.jobId == jobId }
                ?: photos.firstOrNull { it.jobId == null && it.customerId == customerId }
                // 마지막 수단 — **그 고객의 아무 사진.** (2026-09-25 "5집인데 사진 4개")
                //   사장님이 1차 탭에서 올린 사진이 2차 건에 안 붙어 **조용히 빠지던** 경우를 건진다.
                //   그 고객 현장 사진인 건 맞으니, 아예 안 나오는 것보다 낫다.
                ?: photos.firstOrNull { it.customerId == customerId }
    }

    private fun photoDir(): File = File(context.filesDir, "site_photos").apply { mkdirs() }

    /** 갤러리 등에서 고른 URI 를 내부 저장소로 복사 + DB 기록. 성공 시 true. */
    /**
     * @param jobId 어느 시공 건의 사진인지. null 이면 '미분류'.
     *   🔴 전엔 이 값이 **항상 null** 이라, 건 탭을 만들어도 1차·2차 사진이 안 갈렸다.
     *   (2026-09-18 사장님: "현장사진도 1차 2차 개별로 들어가야해")
     */
    suspend fun addFromUri(customerId: Long, uri: Uri, jobId: Long? = null): Boolean = withContext(Dispatchers.IO) {
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
                SitePhotoEntity(
                    customerId = customerId, jobId = jobId,
                    filePath = file.absolutePath, createdAt = nowMs
                )
            )
            true
        }.getOrDefault(false)
    }

    /**
     * 🛟 **서버에 있던 사진을 폰으로 되살린다.** (2026-09-28 사장님)
     *   폰을 재설치하면 앱 안 사진이 통째로 지워진다. 서버에 올라간 것만 살아남는데,
     *   그건 **보기만** 되고 내 기록·영상·백업엔 안 들어갔다. 로컬로 내려받아야 온전해진다.
     *   @param takenAt 서버에 올린 시각 — 그래야 순서가 원래대로 선다.
     */
    suspend fun addFromBitmap(
        customerId: Long,
        bitmap: android.graphics.Bitmap,
        jobId: Long? = null,
        takenAt: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(photoDir(), "${customerId}_${takenAt}_${System.nanoTime()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (file.length() == 0L) {
                runCatching { file.delete() }
                return@runCatching false
            }
            dao.insert(
                SitePhotoEntity(
                    customerId = customerId, jobId = jobId,
                    filePath = file.absolutePath, createdAt = takenAt,
                    // 서버에서 온 것이니 **다시 올릴 필요 없다** 고 표시.
                    serverUploadedAt = System.currentTimeMillis()
                )
            )
            true
        }.getOrDefault(false)
    }

    /**
     * 🔄 **사진을 돌려서 파일에 그대로 쓴다.** (2026-09-30 사장님)
     *
     *   돌아서 들어온 사진은 EXIF(방향 표시)가 없거나 틀린 것이라 **자동으로는 못 고친다.**
     *   그래서 사장님이 돌린 대로 **픽셀 자체를 돌려** 다시 쓴다 — 그래야 어디서 봐도 똑바로다.
     *
     *   서버에 올린 표시(serverUploadedAt)를 **지운다** → 업로더가 알아서 다시 올린다.
     *   안 그러면 폰은 바로 섰는데 **PC 웹은 누운 채로** 남는다.
     *
     * @param degrees 90 의 배수. 0 이면 아무것도 안 한다(네 번 눌러 제자리로 온 경우).
     * @return 성공 여부.
     */
    suspend fun rotate(photoId: Long, degrees: Int): Boolean = withContext(Dispatchers.IO) {
        val deg = ((degrees % 360) + 360) % 360
        if (deg == 0) return@withContext true
        runCatching {
            val path = dao.filePathOf(photoId) ?: return@runCatching false
            val file = File(path)
            if (!file.exists()) return@runCatching false
            val src = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: return@runCatching false
            val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
            val out = android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            // 임시 파일에 먼저 쓴다 — 쓰다 죽으면 원본이 깨진 채로 남으면 안 된다.
            val tmp = File(file.absolutePath + ".rot")
            tmp.outputStream().use { o ->
                out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, o)
            }
            if (tmp.length() == 0L) { runCatching { tmp.delete() }; return@runCatching false }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true); runCatching { tmp.delete() }
            }
            if (src !== out) runCatching { src.recycle() }
            // 서버에 다시 올리게 표시를 지운다.
            runCatching { dao.markUploaded(photoId, null) }
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
