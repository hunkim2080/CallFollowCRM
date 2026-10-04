package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SitePhotoServerRepository
import com.detailline.callfollowcrm.data.local.dao.SitePhotoDao
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import com.detailline.callfollowcrm.util.ImageEncoder
import org.json.JSONObject
import java.io.File

/**
 * 📸 현장 사진 백필 업로드 → 서버(team_site_photos, OWNER). targetKey = photoId.
 *   payload: {photoId, filePath, custPhone, label, workDate?} — **넣는 순간의 값**(§137).
 *   보낼 때 읽는 건 **파일 바이트뿐.** 손님 번호·시공일은 feeder(OwnerPhotoUploadManager)가 넣을 때 풀어둔다.
 *
 *   OwnerPhotoUploadManager.uploadPending() 의 **한 장 로직을 그대로 옮겼다**(§337 verbatim) —
 *   성공하면 serverUploadedAt 도장, 파일 없으면 -1 표식, 서버 403(티어)·5xx·끊김은 재시도.
 */
class SitePhotoHandler(
    private val sitePhotoDao: SitePhotoDao,
    private val serverRepo: SitePhotoServerRepository
) : OutboxHandler {
    override val kind = OutboxKind.SITE_PHOTO

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val o = JSONObject(payloadJson)
        val photoId = o.optLong("photoId")
        val filePath = o.optString("filePath")
        val custPhone = o.optString("custPhone")
        val owner = o.optString("ownerPhone").ifBlank { ownerPhone }
        if (photoId <= 0 || filePath.isBlank() || custPhone.isBlank() || owner.isBlank()) {
            return Verdict.DEAD to 400
        }
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            sitePhotoDao.markUploaded(photoId, -1L)     // 파일 사라짐 → 재시도 제외(원본 동작)
            return Verdict.DONE to 200                   // 보낼 게 없으니 우체통에서 뺀다
        }
        val b64 = ImageEncoder.fileToJpegBase64(file)
            ?: return Verdict.RETRY to null              // 디코드 실패 → 다음 기회
        val dataUrl = "data:image/jpeg;base64,$b64"
        // 🧬 서버 한도는 ImageEncoder.CAP_CHARS 한 곳.
        if (dataUrl.length > ImageEncoder.CAP_CHARS) return Verdict.DEAD to 413

        val label = o.optString("label").ifBlank { "시공 사진" }
        val workDate = o.optString("workDate").takeIf { it.isNotBlank() }
        // opKey = 재시도 멱등(UUID) · clientKey = 사진 고정키(돌려서 재업로드해도 서버가 덮어씀). §5-D
        val res = serverRepo.uploadOwnerPhoto(
            owner, custPhone, dataUrl, label, workDate, opKey = opKey, clientKey = "photo:$photoId"
        )
        if (res.isSuccess) sitePhotoDao.markUploaded(photoId, System.currentTimeMillis())
        return res.toVerdict(kind)                       // 403=티어→재시도 · 5xx/끊김→재시도 · 4xx→dead
    }
}
