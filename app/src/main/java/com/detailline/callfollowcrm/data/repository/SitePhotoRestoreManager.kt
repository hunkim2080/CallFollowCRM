package com.detailline.callfollowcrm.data.repository

import android.util.Log
import com.detailline.callfollowcrm.ai.SitePhotoServerRepository
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 🛟 **서버에만 있는 내 현장 사진을 폰(로컬)으로 되살린다 — 자동.**
 *
 *  재설치·백업복원 뒤엔 사진이 **서버에만** 남아, 고객 상세엔 보여도(서버에서 끌어옴)
 *  **내 기록 영상·인증샷·백업엔 안 들어갔다** — 그쪽은 로컬만 읽으니까.
 *  (2026-11-27 사장님: "영상에 사진이 한 장도 안 뜸" — 창고(서버)엔 있는데 책상(폰)엔 없던 것.
 *   근거: 서버 team_site_photos 엔 사진이 있고, 고객상세는 로컬+서버를 보지만 영상은 로컬만 봄.)
 *
 *  고객 상세의 **수동 '되살리기'**([com.detailline.callfollowcrm.presentation.screen.customer.CustomerDetailViewModel.restorePhotosFromServer])
 *  와 **같은 잣대**로 움직인다(§12 — 바뀌면 둘 다 같이): 폰에 **살아있는 파일이 0**인 고객만,
 *  서버의 **내가 올린(isOwner)** 사진을 [SitePhotoRepository.addFromBitmap] 으로 로컬에 쓴다(올린 순서대로).
 *
 *  ⚠️ 버스트 방지 — 부르는 쪽이 **고객 목록을 좁혀** 넘긴다(예: 보는 달의 현장 고객만). 여기서 전수 조회는 안 한다.
 */
class SitePhotoRestoreManager(
    private val sitePhotoRepository: SitePhotoRepository,
    private val serverRepository: SitePhotoServerRepository,
    private val jobRepository: JobRepository,
    private val preferences: AppPreferences,
) {
    /**
     * 주어진 고객들 중 **폰에 사진이 없는** 고객의 서버 사진을 로컬로 되살린다.
     * @return 되살린 사진 장수.
     */
    suspend fun restoreMissing(customers: List<CustomerEntity>): Int = withContext(Dispatchers.IO) {
        val owner = preferences.bizPhone.trim()
        if (owner.isBlank() || customers.isEmpty()) return@withContext 0
        var restored = 0
        for (c in customers) {
            val cust = c.phoneNumber.trim()
            if (cust.isBlank()) continue
            // 폰에 **살아있는 파일**이 하나라도 있으면 건드리지 않는다.
            //   (줄만 있고 파일이 없는 '백업복원' 상태는 0 으로 세어 복원 대상이 된다 — 수동 되살리기와 같은 셈.)
            val alive = sitePhotoRepository.listByCustomer(c.id).count {
                runCatching { File(it.filePath).exists() }.getOrDefault(false)
            }
            if (alive > 0) continue
            val server = serverRepository.fetch(owner, cust).getOrNull() ?: continue
            val mine = server.filter { it.isOwner }   // 남(팀원·협업)이 올린 건 복원 대상 아님
            if (mine.isEmpty()) continue
            val jid = runCatching { jobRepository.representativeJobId(c.id) }.getOrNull()
            // 느린 네트워크로 화면을 떠나도 끝까지 — 받아둔 비트맵을 버리지 않는다.
            withContext(NonCancellable) {
                mine.sortedBy { it.uploadedAtMs }.forEach { p ->
                    val bmp = p.bitmap ?: return@forEach
                    if (sitePhotoRepository.addFromBitmap(c.id, bmp, jid, p.uploadedAtMs)) restored++
                }
            }
        }
        if (restored > 0) Log.i("SitePhotoRestore", "서버→폰 현장사진 ${restored}장 되살림")
        restored
    }
}
