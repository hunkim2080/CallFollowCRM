package com.detailline.callfollowcrm

import android.app.Application
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.seed.DefaultTemplates
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import com.detailline.callfollowcrm.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallFollowCrmApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)

        appScope.launch {
            DefaultTemplates.seedIfEmpty(container.messageTemplateRepository)
        }

        // 앱 시작 시 에이닷 폴더가 연결돼 있으면 자동 스캔 (백그라운드).
        if (AdotFolderScanner.isConnected(this)) {
            AdotFolderScanner.scanIfConnected(this, container) { /* silent */ }
        }

        // SMS/MMS 캐시 prefetch — 최근 20개 번호. ChatScreen 첫 진입을 즉시 보이게 하는 토대.
        // READ_SMS 권한 없으면 silent skip.
        container.smsCachePrefetcher.prefetchRecentContacts(contactLimit = 20)
    }
}
