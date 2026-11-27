package com.detailline.callfollowcrm.util

import android.content.Context
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.ai.AuthRepository
import kotlinx.coroutines.launch

/**
 * 🔓 통화요약 '녹음 서버 전송 동의'를 **계정(서버)에 기억**해, 업데이트·재설치 때 토글이 풀리지 않게 한다.
 *   (2026-11-27 사장님 "업데이트하면 통화요약이 풀렸다" — 동의 기본 false + Manifest allowBackup=false 라
 *    재설치/큰 버전 점프 때 로컬 동의가 초기화돼, 안 건드렸는데 토글이 꺼진 것처럼 보이던 것.)
 *
 *   저장소는 **기존 동의 인프라 그대로** — POST /api/consent · GET /api/consent/status (doc_type = [DOC_TYPE]).
 *   새 테이블·새 엔드포인트 안 만든다(§12). 플레이 정책도 OK — "한 번 실제로 받은 동의"를 계정이 기억하는 것이라,
 *   기본 true 로 박던 2026-09-17 이전 위반과 다르다.
 *
 *   한 곳(§12): 설정·대화 화면의 동의/철회는 [push], 앱 시작 복원은 [restore] 만 부른다.
 *   ⚠️ [DOC_TYPE] 은 서버 main.py `_CONSENT_DOC_TYPES` 에도 같은 값이 있어야 한다.
 */
object CallSummaryConsentSync {
    const val DOC_TYPE = "call_summary_upload"

    /** 동의(true)/철회(false)를 계정에 기록 — best-effort(로컬은 이미 반영됨, 서버는 '기억'용). 로그인 전이면 no-op. */
    fun push(context: Context, agreed: Boolean) {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val phone = app.container.preferences.bizPhone.filter { it.isDigit() }
        if (phone.isBlank()) return
        app.applicationScope.launch {
            AuthRepository().postConsent(phone, DOC_TYPE, agreed)
        }
    }

    /**
     * 앱 시작 시 한 번: 서버가 '동의함'이면 로컬 복원 · 서버에 기록 없고 로컬이 동의면 서버로 올린다(backfill).
     *   서버가 '철회'면 로컬을 억지로 바꾸지 않는다(사장님이 방금 다시 켰을 수 있어 로컬이 더 최신).
     *   네트워크 실패는 조용히 넘긴다 — 다음 켤 때 다시 시도(로컬 토글은 그대로라 손해 없음).
     */
    suspend fun restore(context: Context) {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val prefs = app.container.preferences
        val phone = prefs.bizPhone.filter { it.isDigit() }
        if (phone.isBlank()) return
        val auth = AuthRepository()
        val server = auth.getConsent(phone, DOC_TYPE).getOrNull()
        when {
            server == true && !prefs.callSummaryConsented -> prefs.callSummaryConsented = true
            server == null && prefs.callSummaryConsented -> auth.postConsent(phone, DOC_TYPE, true)
        }
    }
}
