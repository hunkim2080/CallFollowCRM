package com.detailline.callfollowcrm.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * 📷 **사진첩(MediaStore) 날짜 쿼리** — 「시공한 날 사진만」 선택창용.
 *
 * 시스템 Photo Picker 는 촬영 날짜로 못 거른다(남의 화면) → 날짜 필터를 하려면 사진첩을 직접 읽어야 하고,
 * 그래서 `READ_MEDIA_IMAGES`(33+) / `READ_EXTERNAL_STORAGE`(≤32) 권한이 필요하다. 권한 확인은 **부른 쪽**이 한다.
 *
 * DATE_TAKEN(촬영 시각, ms)이 비는 사진(스캔·다운로드)은 DATE_ADDED(초)로 폴백한다.
 */
object DeviceGalleryRepository {

    data class GalleryPhoto(val uri: Uri, val takenMs: Long)

    /**
     * [range] (ms, 양끝 포함) 안의 사진을 **최신순**으로. range=null 이면 전체(최근 [limit]장).
     * 권한이 없거나 실패하면 빈 목록.
     */
    fun query(context: Context, range: LongRange?, limit: Int = 2000): List<GalleryPhoto> {
        val col = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val idCol = MediaStore.Images.Media._ID
        val takenCol = MediaStore.Images.Media.DATE_TAKEN   // ms
        val addedCol = MediaStore.Images.Media.DATE_ADDED   // sec
        val projection = arrayOf(idCol, takenCol, addedCol)

        var selection: String? = null
        var args: Array<String>? = null
        if (range != null) {
            // DATE_TAKEN(ms) 범위 OR (DATE_TAKEN 없음 AND DATE_ADDED(초) 범위)
            selection = "($takenCol BETWEEN ? AND ?) OR ($takenCol IS NULL AND $addedCol BETWEEN ? AND ?)"
            args = arrayOf(
                range.first.toString(), range.last.toString(),
                (range.first / 1000).toString(), (range.last / 1000).toString()
            )
        }
        val sort = "$takenCol DESC, $addedCol DESC"

        return runCatching {
            val out = ArrayList<GalleryPhoto>()
            context.contentResolver.query(col, projection, selection, args, sort)?.use { c ->
                val iId = c.getColumnIndexOrThrow(idCol)
                val iTaken = c.getColumnIndexOrThrow(takenCol)
                val iAdded = c.getColumnIndexOrThrow(addedCol)
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(iId)
                    val taken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken)
                    val takenMs = if (taken > 0L) taken else c.getLong(iAdded) * 1000L
                    out.add(GalleryPhoto(ContentUris.withAppendedId(col, id), takenMs))
                }
            }
            out
        }.getOrDefault(emptyList())
    }
}
