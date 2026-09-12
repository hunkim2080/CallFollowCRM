package com.detailline.callfollowcrm.util

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play 인앱 업데이트 — **앱을 켜면 Play 시트가 앱 화면 위로 올라와 [업데이트] 를 바로 누르게** 한다. (2026-09-12 사장님)
 *
 * 사장님 요청 그대로: "다른 앱처럼, 배너로 안내만 하는 게 아니라 켜자마자 Play 가 반쯤 열리고
 *   업데이트 버튼이 바로 보여서 누를 수밖에 없게".
 *
 * 동작:
 *  - FLEXIBLE(기본): 앱 위에 Play 시트가 뜨고, 받는 동안 앱은 계속 쓸 수 있다. 다 받으면 재시작만 하면 됨.
 *  - 다 받아둔 상태로 앱을 다시 켜면([checkOnResume]) 바로 설치 완료 처리.
 *
 * 전제/한계 (정직하게):
 *  - **Play 로 설치된 앱에서만** 동작한다(사이드로드 APK 는 Play 가 무시). 우리는 Play 일원화라 OK.
 *  - Play 가 "업데이트 있음"을 인지하기까지 배포 후 시간이 걸릴 수 있다(즉시 아님).
 *  - 어떤 이유로든 실패해도 **앱 동작에 영향 없음** — 전부 runCatching 으로 삼킨다(기존 배너가 폴백).
 */
object InAppUpdater {

    /** Activity 가 이 코드로 결과를 받는다. (요청 코드는 아무 값이나 고유하면 됨) */
    const val REQUEST_CODE = 4711

    private const val TAG = "InAppUpdate"

    private fun manager(activity: Activity): AppUpdateManager =
        AppUpdateManagerFactory.create(activity.applicationContext)

    /**
     * 앱 시작 시 1회 — 업데이트가 있으면 Play 시트를 띄운다.
     * @param onDownloaded 다 받아서 '재시작만 하면 되는' 상태가 되면 호출(스낵바 등 안내용). 없으면 무시.
     */
    fun checkOnStart(activity: Activity, onDownloaded: (() -> Unit)? = null) {
        runCatching {
            val mgr = manager(activity)
            if (onDownloaded != null) {
                mgr.registerListener { state ->
                    if (state.installStatus() == InstallStatus.DOWNLOADED) runCatching { onDownloaded() }
                }
            }
            mgr.appUpdateInfo
                .addOnSuccessListener { info ->
                    runCatching {
                        val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        val allowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                        Log.i(TAG, "avail=${info.updateAvailability()} flexibleAllowed=$allowed code=${info.availableVersionCode()}")
                        if (available && allowed) {
                            mgr.startUpdateFlowForResult(
                                info,
                                activity,
                                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                                REQUEST_CODE
                            )
                        }
                    }
                }
                .addOnFailureListener { e -> Log.i(TAG, "check failed (무시): ${e.message}") }
        }
    }

    /**
     * 다시 앱으로 돌아왔을 때 — 이미 다 받아둔 업데이트가 있으면 설치를 마무리한다.
     *   (사용자가 Play 시트에서 받다가 앱으로 돌아온 경우)
     */
    fun completeIfDownloaded(activity: Activity) {
        runCatching {
            val mgr = manager(activity)
            mgr.appUpdateInfo.addOnSuccessListener { info ->
                runCatching {
                    if (info.installStatus() == InstallStatus.DOWNLOADED) mgr.completeUpdate()
                }
            }
        }
    }
}
