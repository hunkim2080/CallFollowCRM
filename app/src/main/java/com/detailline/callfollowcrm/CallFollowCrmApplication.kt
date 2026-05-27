package com.detailline.callfollowcrm

import android.app.Application
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.seed.DefaultPricingItems
import com.detailline.callfollowcrm.data.local.seed.DefaultTemplates
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
            DefaultPricingItems.seedIfEmpty(container.pricingItemRepository)
        }

        // SMS/MMS 캐시 prefetch — 최근 20개 번호. ChatScreen 첫 진입을 즉시 보이게 하는 토대.
        // READ_SMS 권한 없으면 silent skip.
        container.smsCachePrefetcher.prefetchRecentContacts(contactLimit = 20)
    }
}
