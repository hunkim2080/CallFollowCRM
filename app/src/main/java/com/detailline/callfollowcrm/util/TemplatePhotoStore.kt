package com.detailline.callfollowcrm.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 문구(템플릿) 첨부 사진 저장소 — 갤러리에서 고른 사진을 **앱 내부(filesDir/template_photos)** 에 복사하고
 *   FileProvider URI 로 감싼다. (2026-07-17 사장님)
 *
 * 왜: SAF(OpenDocument) URI 를 그대로 저장하면 나중에 그 문구를 쓸 때 권한이 풀려 사진이 안 읽힌다.
 *   앱 내부 복사본은 몇 주 뒤에도 확실히 읽힘(발송 SmsSender.decodeMmsBitmap·미리보기 공통).
 *
 * 두 곳에서 공용: 문구 편집 화면(TemplateEditViewModel) + 채팅 "입력창 글을 문구로 저장"(ChatViewModel).
 */
object TemplatePhotoStore {

    data class Copied(val uri: String, val displayName: String, val mimeType: String)

    private const val DIR = "template_photos"

    /** 고른 사진을 앱 내부로 복사하고 FileProvider URI 로 감싼다. 실패 시 null. IO 스레드에서 호출. */
    fun copyToAppStorage(context: Context, src: Uri): Copied? {
        val mime = context.contentResolver.getType(src) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; else -> "jpg"
        }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "tpl_${System.currentTimeMillis()}_${(0..999999).random()}.$ext")
        val ok = runCatching {
            context.contentResolver.openInputStream(src)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!ok || !file.exists() || file.length() == 0L) { runCatching { file.delete() }; return null }
        val fpUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Copied(uri = fpUri.toString(), displayName = file.name, mimeType = mime)
    }

    /** 우리 앱 내부 template_photos 파일이면 그 File, 아니면 null(옛 SAF URI 등). 삭제 정리용. */
    fun fileFor(context: Context, uriStr: String): File? {
        val name = uriStr.substringAfterLast('/').substringBefore('?').takeIf { it.startsWith("tpl_") } ?: return null
        val f = File(File(context.filesDir, DIR), name)
        return if (f.exists()) f else null
    }
}
