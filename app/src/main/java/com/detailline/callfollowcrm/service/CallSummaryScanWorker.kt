package com.detailline.callfollowcrm.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import com.detailline.callfollowcrm.recording.AdotTextFolderScanner
import kotlinx.coroutines.delay

/**
 * 통화 종료 후 자동 통화요약 (2026-06-14 사장님).
 *   에이닷이 폴더에 떨군 통화내용(.txt) / 녹음(.m4a)을 주워다 서버로 요약 → 통화카드에 자동으로 붙인다.
 *   → 사장님이 공유 버튼을 누르지 않아도 통화만 끝나면 요약이 쌓인다.
 *
 * [CallStateReceiver] 가 통화 종료를 감지하면 ~15초 지연으로 enqueue.
 *   에이닷이 파일을 쓰는 데 시간이 걸려(수초~수십초) 한 워커 안에서 2패스(즉시 + 25초 뒤) 시도.
 *
 * no-op 조건: prefs.autoSummaryEnabled OFF · 에이닷 폴더 미연결.
 *   (텍스트 경로는 스캔 시 LLM 요약까지, 녹음 경로는 서버 STT+요약. 둘 다 이미 있으면 스킵 → 재과금 방지.)
 */
class CallSummaryScanWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CallFollowCrmApplication ?: return Result.success()
        val container = app.container
        if (!container.preferences.autoSummaryEnabled) return Result.success()

        val ctx = applicationContext
        val textConnected = AdotTextFolderScanner.isConnected(ctx)
        val audioConnected = AdotFolderScanner.isConnected(ctx)
        if (!textConnected && !audioConnected) return Result.success()

        // 에이닷 파일 쓰기 지연 대응 — 즉시 + 25초 뒤 두 번 시도.
        repeat(2) { pass ->
            if (textConnected) runCatching { AdotTextFolderScanner.scanNow(ctx, container) }
            if (audioConnected) runCatching { AdotFolderScanner.scanAndSummarizeNow(ctx, container) }
            if (pass == 0) delay(25_000)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK = "auto_call_summary_scan"
    }
}
