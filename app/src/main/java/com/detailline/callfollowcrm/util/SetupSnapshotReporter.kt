package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.Context
import android.provider.Settings
import com.detailline.callfollowcrm.ai.JourneyEventRepository
import com.detailline.callfollowcrm.data.preferences.AppPreferences

/**
 * 📋 **설정 스냅샷** — 이 사장님이 앱을 **어떻게 설정했는지**를 관리자 대시보드가 볼 수 있게 서버로 보낸다.
 *   (2026-10-29 사장님: "이 사람이 어떻게 설정했는지 다 봐야 '여기 잘못 설정돼서 오류 난 거예요' 짚어주지.
 *    서버에 자동저장돼야 업종별 통계도 꾸리고.")
 *
 * 담는 것 = **설정 상태 + 사업자정보(상호·번호) + 고객 '수'** 뿐.
 *   - 업종 / 기본 문자앱 여부 / 권한 각각(허용·거부) / 오버레이 / 온보딩 완료 / 활동지역 / 상호 / 사업자번호 / 고객 수
 * 🚫 **손님 데이터(전화번호·대화·문의 내용)는 절대 안 보낸다** — 집계용 '수'와 '설정 상태'만. (사장님 확정한 선)
 *
 * 보내는 길 = 이미 있는 여정 통로([JourneyEventRepository] → `/api/event`)에 `setup_snapshot` 이벤트로 실어 보낸다.
 *   서버(Phase 2)가 이걸 모아 **유저별 최신 설정 상태 + 업종별 벤치마크 통계**로 보여준다.
 *
 * 스팸 방지: **바뀌었을 때만** 보낸다(업종 고름·권한 허용·기본앱 설정 등으로 지문이 달라질 때). 똑같으면 안 보냄.
 *   앱 켤 때 1회 호출 — [com.detailline.callfollowcrm.CallFollowCrmApplication].
 */
object SetupSnapshotReporter {

    /** @param customerCount 전체 고객 수(CustomerRepository.count()) — 호출부가 suspend 에서 구해 넘긴다. */
    fun report(
        context: Context,
        prefs: AppPreferences,
        journey: JourneyEventRepository,
        customerCount: Int
    ) {
        val snap = linkedMapOf<String, Any?>(
            "trade" to prefs.ownerTrades.firstOrNull().orEmpty(),
            "regions" to prefs.ownerRegions.joinToString(","),
            "default_sms" to DefaultSmsAppHelper.isCurrentDefault(context),
            "perm_contacts" to PermissionHelper.isGranted(context, Manifest.permission.READ_CONTACTS),
            "perm_sms" to PermissionHelper.isGranted(context, Manifest.permission.READ_SMS),
            "perm_calllog" to PermissionHelper.isGranted(context, Manifest.permission.READ_CALL_LOG),
            "perm_phone" to PermissionHelper.isGranted(context, Manifest.permission.READ_PHONE_STATE),
            "perm_notif" to PermissionHelper.hasNotifications(context),
            "perm_overlay" to Settings.canDrawOverlays(context),
            "onboarded" to prefs.hasOnboarded,
            "biz_name" to prefs.bizName,
            "biz_no" to prefs.bizNo,
            "customers" to customerCount
        )
        // 바뀐 것만 보낸다 — 같은 스냅샷이면 건너뛴다.
        val fingerprint = snap.entries.joinToString("|") { "${it.key}=${it.value}" }
        if (fingerprint == prefs.lastSetupSnapshot) return
        prefs.lastSetupSnapshot = fingerprint
        journey.track("setup_snapshot", screen = "app", extra = snap)
    }
}
