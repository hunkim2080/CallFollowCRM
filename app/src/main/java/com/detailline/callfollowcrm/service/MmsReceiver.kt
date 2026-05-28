package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 2026-05-29 Phase A 1단계 — MMS 수신 receiver stub.
 *
 * Default SMS 앱 자격 요건 (4/4 — WAP_PUSH_DELIVER) 충족용으로 박혀있음.
 * **이 stub 자체는 1단계 동안 호출 안 됨** — Settings 토글이 disabled (회색) 라 사장님 폰이 default 가 안 됨.
 *
 * 2단계 (Phase B) 에서 본격 구현:
 *   1. PDU 파싱 (M-Notification.ind 추출)
 *   2. MMSC 게이트웨이 HTTP GET (Content-Location URL) — APN 설정 의존
 *   3. M-Retrieve.conf 파싱 → 본문 + 첨부 파트 분리
 *   4. content://mms 와 content://mms/part 에 INSERT
 *   5. 첨부 이미지 다운로드 + ContentValues 저장
 *   6. NotificationHelper.showIncomingMms() 트리거
 *   7. PrepareContext 에 첨부 메타 추가 (서버가 사진 OCR/Vision 호출용)
 *
 * 만약 default 인 상태에서 이 stub 이 호출되면 (예: 2단계 미완성 + 토글 ON 사고)
 * MMS 가 silent fail — 사용자 메시지 영영 못 받음. 그래서 Settings 토글이 2단계 끝까지 hidden.
 *
 * Manifest 등록: WAP_PUSH_DELIVER + mimeType=application/vnd.wap.mms-message + BROADCAST_WAP_PUSH permission.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1단계: 호출 사실만 로그. 처리 X.
        // 호출 시점 = 사장님이 default 토글 켠 상태에서 MMS 도착. 2단계 끝까지는 절대 일어나면 안 됨.
        Log.w(
            TAG,
            "MMS WAP_PUSH_DELIVER received (Phase A 1단계 stub — no handler). " +
                "If you see this in Phase A, the default-SMS toggle was enabled prematurely. " +
                "MMS message is being lost. Disable RING-GO as default SMS app."
        )
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}
