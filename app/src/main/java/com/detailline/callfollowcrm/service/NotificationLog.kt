package com.detailline.callfollowcrm.service

import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import com.detailline.callfollowcrm.data.repository.AppNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 🔔 알림을 **쏘는 순간** 인앱 알림 센터에도 한 줄 남기는 단 한 곳. (2026-10-13 사장님)
 *
 * NotificationHelper 의 각 show* 가 푸시를 띄우면서 [record] 를 부른다 →
 *   푸시를 지워도 알림 센터엔 남고(= "뭐 왔었지" 해결), 탭하면 저장해둔 목적지로 이동한다.
 *
 * NotificationHelper 는 receiver/worker 등 **코루틴 밖**에서도 불리므로, 여기서 IO 스코프로 받아
 *   fire-and-forget 으로 저장한다(알림 띄우기를 막지 않게). repo 는 앱 시작 때 [init].
 */
object NotificationLog {

    @Volatile private var repo: AppNotificationRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(repository: AppNotificationRepository) { repo = repository }

    /**
     * @param type 분류·아이콘 키(new_inquiry·reply·missed_call·intake·reminder·recurring·balance_due·
     *             daily_brief·install_d1·call_summary·collab_* 등).
     * @param navTarget 탭하면 갈 곳(chat·collab·schedule·settlement·intake·home). null 이면 탭해도 삭제만.
     */
    fun record(
        type: String,
        title: String,
        body: String? = null,
        navTarget: String? = null,
        navPhone: String? = null,
        navCustomerId: Long? = null,
        navShareId: String? = null,
        at: Long = System.currentTimeMillis(),
    ) {
        val r = repo ?: return
        scope.launch {
            runCatching {
                r.add(
                    AppNotificationEntity(
                        type = type,
                        title = title,
                        body = body?.takeIf { it.isNotBlank() },
                        navTarget = navTarget,
                        navPhone = navPhone,
                        navCustomerId = navCustomerId,
                        navShareId = navShareId,
                        createdAt = at,
                    )
                )
            }
        }
    }

    /**
     * 🤝 협업 현장 **사진** — 같은 현장 사진을 한 줄(「사진 N장」)로 묶어 센터에 기록하고,
     *   묶인 수를 [onCount] 로 돌려준다(시스템 알림 본문에도 그 수를 쓰게). (2026-10-15 사장님)
     *   repo 가 아직 없으면(앱 아주 초기) 그냥 1장으로 본다.
     */
    fun bumpCollabPhoto(who: String, where: String, shareId: String, onCount: (Int) -> Unit) {
        val r = repo ?: run { onCount(1); return }
        scope.launch {
            val count = runCatching { r.bumpCollabPhoto(who, where, shareId) }.getOrDefault(1)
            onCount(count)
        }
    }
}
