package com.detailline.callfollowcrm.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * 2026-07-05 — MMS 다운로드 임시 PDU 파일용 ContentProvider.
 *
 * SmsManager.downloadMultimediaMessage(url, contentUri, ...) 가 다운로드한 PDU 를 이 content URI 로 write.
 * SmsManager 클라이언트측이 **앱 프로세스에서** 이 URI 의 FD 를 열어 phone 서비스에 넘기므로
 *   (AOSP Messaging MmsFileProvider 패턴) cross-process URI 권한을 따로 grant 할 필요 없음.
 *
 * URI: content://${'$'}{applicationId}.mmsdownload/<파일명>  →  cacheDir/<파일명>
 */
class MmsDownloadFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("no context")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("no file in $uri")
        val safe = File(name).name // path-traversal 방지: 파일명만
        val file = File(ctx.cacheDir, safe)
        // AOSP MmsFileProvider 패턴: parseMode("w") = WRITE_ONLY|CREATE|TRUNCATE.
        //   기존 MODE_READ_WRITE|MODE_CREATE 는 TRUNCATE 누락 → 다운로드 PDU 에 잔여 tail 이 남아
        //   사진이 '앞부분만 + 지지직'으로 손상될 수 있었음. (2026-08-24 사장님 · 표준과 일치)
        val flags = runCatching { ParcelFileDescriptor.parseMode(mode) }.getOrElse {
            if (mode.contains("w"))
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            else
                ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, flags)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
