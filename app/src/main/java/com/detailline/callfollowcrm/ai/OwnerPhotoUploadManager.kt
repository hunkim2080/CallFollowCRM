package com.detailline.callfollowcrm.ai

import android.content.Context
import com.detailline.callfollowcrm.data.local.dao.SitePhotoDao
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.util.ImageEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 시공막내 웹 뷰어 — 사장님이 폰에 찍어둔 현장사진(로컬 site_photos)을 서버(team_site_photos, member_id='OWNER')로
 *   백필 업로드. 그래야 PC 웹에서 사장님 사진을 날짜별로 보고 블로그용으로 내려받음. (2026-08-13 사장님)
 *
 * 원칙:
 *   - **웹 로그인(webViewerActive) 상태일 때만** 올림 → 웹 안 쓰는 사람은 서버 비용/개인정보 0 ([[WebFeedSyncManager]] 와 동일 게이트).
 *   - 오래된 것부터(createdAt ASC) 올려 전/후 자동추정 순서 보존.
 *   - 1280px 압축([ImageEncoder]) → ≤1MB. 완료 표시(serverUploadedAt)로 중복 방지.
 *   - 서버 오류(예: 티어 게이트 403)면 그 라운드 중단 → 다음 기회(앱 재시작/재로그인)에 재시도.
 */
class OwnerPhotoUploadManager(
    private val context: Context,
    private val sitePhotoDao: SitePhotoDao,
    private val customerRepository: CustomerRepository,
    private val serverRepo: SitePhotoServerRepository,
    private val prefs: AppPreferences
) {
    private val mutex = Mutex()   // start·로그인 트리거가 겹쳐도 1회만

    /** 백그라운드로 백필 1회 시도(겹치면 무시). */
    fun kick(scope: CoroutineScope) {
        scope.launch { runCatching { uploadPending() } }
    }

    /** 미업로드 로컬 사진을 순서대로 서버에 올림. @return 이번에 올린 장수. */
    suspend fun uploadPending(): Int = mutex.withLock {
        if (!prefs.webViewerActive) return@withLock 0
        val ownerPhone = prefs.bizPhone.trim()
        if (ownerPhone.filter { it.isDigit() }.length < 9) return@withLock 0

        val pending = runCatching { sitePhotoDao.pendingUpload(300) }.getOrNull().orEmpty()
        if (pending.isEmpty()) return@withLock 0

        // 고객 id → 전화(숫자). 서버는 customer_phone 으로 사진↔고객을 이음(끝8 정규화).
        val phoneById = runCatching {
            customerRepository.allOnce().associate { it.id to it.phoneNumber }
        }.getOrDefault(emptyMap())

        var uploaded = 0
        for (p in pending) {
            val custPhone = phoneById[p.customerId]?.filter { it.isDigit() }?.takeIf { it.length >= 9 }
            if (custPhone == null) continue    // 전화 없는 고객 = 서버서 이을 수 없음 → 이번엔 건너뜀(다음에 전화 생기면)

            val file = File(p.filePath)
            if (!file.exists() || file.length() == 0L) {
                sitePhotoDao.markUploaded(p.id, -1L)   // 파일 사라짐 → 재시도 제외
                continue
            }
            val b64 = ImageEncoder.fileToJpegBase64(file) ?: continue   // 디코드 실패 → 다음 기회
            val dataUrl = "data:image/jpeg;base64,$b64"
            if (dataUrl.length > 1_400_000) continue   // 압축해도 1MB 초과면 이번엔 skip

            val res = serverRepo.uploadOwnerPhoto(ownerPhone, custPhone, dataUrl, p.label ?: "시공 사진")
            if (res.isSuccess) {
                sitePhotoDao.markUploaded(p.id, System.currentTimeMillis())
                uploaded++
            } else {
                break   // 서버 오류(403/5xx 등) → 라운드 중단, 다음 기회에 재시도
            }
        }
        uploaded
    }
}
