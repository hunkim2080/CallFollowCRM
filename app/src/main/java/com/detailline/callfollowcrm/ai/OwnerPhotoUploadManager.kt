package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.local.dao.SitePhotoDao
import com.detailline.callfollowcrm.data.outbox.Outbox
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * 시공막내 웹 뷰어 — 폰에 찍어둔 현장사진(로컬 site_photos)을 서버로 백필 업로드하게 **우체통에 넣는다.**
 *
 *   📮 **1-b 로 바뀜(2026-10-04)**: 전엔 이 매니저가 직접 올리고 **따로 재시도**했다(두 번째 큐).
 *     이제는 **우체통 하나**가 올리고 재시도한다(§13④). 이 매니저는 **먹여주는 쪽**만 한다 —
 *     아직 안 올린 사진(serverUploadedAt IS NULL)을 찾아 `outbox.enqueue(SITE_PHOTO, ...)` 로 넣고 깨운다.
 *     실제 올리기·백오프·판정은 `data/outbox/handlers/SitePhotoHandler`.
 *     옛 사진들은 **마이그레이션 SQL 이 아니라** 첫 kick 때 이 스캔이 자연스럽게 넣는다
 *     (NOT NULL 칸 빼먹어 앱이 안 켜지는 위험한 INSERT…SELECT 를 피했다 — 더 안전).
 *
 *   원칙(그대로): 항상 올린다(웹 안 써도) · 오래된 것부터(createdAt ASC, 전/후 순서 보존) ·
 *     손님 전화 없는 사진은 이번엔 건너뜀(다음에 전화 생기면) · 완료 표시(serverUploadedAt)로 중복 방지.
 */
class OwnerPhotoUploadManager(
    private val sitePhotoDao: SitePhotoDao,
    private val customerRepository: CustomerRepository,
    private val jobRepository: com.detailline.callfollowcrm.data.repository.JobRepository,
    private val prefs: AppPreferences,
    private val outbox: Outbox
) {
    private val mutex = Mutex()   // 겹친 트리거가 와도 한 번만 스캔

    /** 백그라운드로 1회 먹여주기(겹치면 무시). 기존 호출부(앱시작·웹로그인·설정 등) 그대로 쓴다(§12-D 입구 유지). */
    fun kick(scope: CoroutineScope) {
        scope.launch { runCatching { enqueuePending() } }
    }

    /** 아직 안 올린 사진을 우체통에 넣고 깨운다. @return 이번에 넣은 장수. */
    suspend fun enqueuePending(): Int = mutex.withLock {
        val ownerPhone = prefs.bizPhone.trim()
        if (ownerPhone.filter { it.isDigit() }.length < 9) return@withLock 0

        val pending = runCatching { sitePhotoDao.pendingUpload(300) }.getOrNull().orEmpty()
        if (pending.isEmpty()) return@withLock 0
        // 이미 우체통에 있는 사진(보내는 중이거나 죽은 것)은 또 넣지 않는다 — dead 가 계속 되살아나지 않게.
        val already = runCatching { outbox.targetsForKind(OutboxKind.SITE_PHOTO.wire) }.getOrNull()?.toHashSet().orEmpty()

        // 고객 id → 전화(숫자). 서버는 customer_phone 으로 사진↔고객을 이음(끝8 정규화).
        val phoneById = runCatching {
            customerRepository.allOnce().associate { it.id to it.phoneNumber }
        }.getOrDefault(emptyMap())
        // 건 id → 그 건의 시공일('YYYY-MM-DD'). 이게 있어야 PC 에서 1차·2차 사진이 갈린다. (2026-09-18)
        val workDateByJob = runCatching {
            jobRepository.allOnce().mapNotNull { j ->
                j.scheduledWorkDate?.let { d -> j.id to com.detailline.callfollowcrm.util.DateTimeUtils.isoDate(d) }
            }.toMap()
        }.getOrDefault(emptyMap())

        var queued = 0
        for (p in pending) {
            if (p.id.toString() in already) continue   // 이미 우체통에 있음(보내는 중/죽음) → 또 안 넣음
            val custPhone = phoneById[p.customerId]?.filter { it.isDigit() }?.takeIf { it.length >= 9 }
                ?: continue   // 전화 없는 고객 = 서버서 이을 수 없음 → 이번엔 건너뜀(다음에 전화 생기면)
            val payload = JSONObject().apply {
                put("photoId", p.id)
                put("filePath", p.filePath)
                put("custPhone", custPhone)
                put("ownerPhone", ownerPhone.filter { it.isDigit() })
                put("label", p.label ?: "시공 사진")
                p.jobId?.let { workDateByJob[it] }?.let { put("workDate", it) }
            }
            // targetKey=photoId, owner="" (사진은 기기 것 → 번호 바뀌어도 올림). KEEP_EXISTING 이라 이미 있으면 그대로.
            runCatching {
                outbox.enqueue(OutboxKind.SITE_PHOTO, p.id.toString(), payload.toString(), ownerPhone = "")
            }
            queued++
        }
        outbox.tryNow()
        queued
    }
}
