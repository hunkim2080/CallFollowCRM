package com.detailline.callfollowcrm.recording

import android.content.Context
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 수동 입력한 전화번호의 과거 데이터를 한 번에 끌어온다.
 *
 * 시나리오:
 *  - 사용자가 알림을 못 눌러서 BroadcastReceiver가 만든 CallRecord가 없거나
 *  - 처음부터 통화 종료 감지가 동작하지 않은 경우
 *  - 그래도 시스템 통화기록과 에이닷 녹음은 폰에 남아 있으므로
 *  - 수동 입력 직후에 그 두 곳에서 가져와 연결한다.
 */
object CustomerBackfiller {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Result(
        val newCallRecords: Int,
        val newRecordings: Int
    )

    fun backfill(
        context: Context,
        container: AppContainer,
        phoneNumber: String,
        customerId: Long,
        onComplete: ((Result) -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        scope.launch {
            val callsAdded = runCatching {
                container.callRecordRepository.syncFromCallLog(appCtx, phoneNumber, customerId)
            }.getOrDefault(0)

            // 녹음 백필은 scanByPhone 자체가 비동기/콜백이지만, 결과를 합쳐서 한 번에 알리기 위해
            // 직접 동일 로직을 호출하는 대신 콜백 변환.
            val recordings = if (AdotFolderScanner.isConnected(appCtx)) {
                kotlinx.coroutines.suspendCancellableCoroutine<Int> { cont ->
                    AdotFolderScanner.scanByPhone(appCtx, container, phoneNumber, customerId) { n ->
                        if (cont.isActive) cont.resumeWith(kotlin.Result.success(n))
                    }
                }
            } else 0

            withContext(Dispatchers.Main) {
                onComplete?.invoke(Result(callsAdded, recordings))
            }
        }
    }
}
