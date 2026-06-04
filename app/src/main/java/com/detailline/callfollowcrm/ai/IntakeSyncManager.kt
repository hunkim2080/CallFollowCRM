package com.detailline.callfollowcrm.ai

import android.content.Context
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.service.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 시공접수서 제출 동기화 (2026-06-03) — GET /api/quote/submissions 폴링.
 *   고객이 링크로 주소·시공일을 제출하면 → 그 고객 카드에 자동 반영(주소·시공일·메모) + 알림.
 *   이미 임포트한 token 은 prefs 로 추적해 중복 알림 방지. 사업자 번호 미설정이면 skip.
 */
class IntakeSyncManager(private val container: AppContainer) {

    suspend fun sync(context: Context) {
        val prefs = container.preferences
        val devicePhone = prefs.bizPhone
        if (devicePhone.isBlank()) return // 사업자정보 미설정 — 폴링 키(devicePhone) 없음
        val since = prefs.intakeSyncSinceMs
        val list = container.intakeFormRepository.submissions(devicePhone, since).getOrNull() ?: return
        if (list.isEmpty()) return

        val imported = prefs.intakeImportedTokens.toMutableSet()
        var maxSubmitted = since
        var changed = false
        for (s in list) {
            val submitted = s.submittedAtMs ?: continue
            if (submitted > maxSubmitted) maxSubmitted = submitted
            if (s.token in imported || s.customerPhone.isBlank()) continue

            val c = container.customerRepository.upsertByPhone(phoneNumber = s.customerPhone)
            val fullAddr = listOfNotNull(s.address, s.dong).joinToString(" ").trim()
            if (fullAddr.isNotBlank()) container.customerRepository.updateAddress(c.id, fullAddr)
            val workMs = workMsOf(s)
            workMs?.let { container.customerRepository.updateScheduledWorkDate(c.id, it) }
            if (!s.memo.isNullOrBlank() && c.memo.isNullOrBlank()) {
                container.customerRepository.updateMemo(c.id, "📋 접수: ${s.memo}")
            }

            imported.add(s.token)
            changed = true
            val nm = s.customerName.ifBlank { s.customerPhone }
            val dateLabel = workMs?.let {
                SimpleDateFormat("M/d(E)", Locale.KOREA).format(java.util.Date(it))
            }

            // 채팅 타임라인 카드용 이벤트 기록 — "고객이 접수서 작성을 완료했어요" 를 제출 시각에 표시.
            //   token unique IGNORE → 폴링 반복돼도 카드 중복 안 됨. suffix = ChatViewModel 매칭 규칙과 동일.
            runCatching {
                val suffix = s.customerPhone.filter { it.isDigit() }.takeLast(8)
                container.intakeEventRepository.record(
                    com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity(
                        phoneSuffix = suffix,
                        token = s.token,
                        customerName = nm,
                        submittedAtMs = submitted,
                        address = fullAddr.ifBlank { null },
                        dateLabel = dateLabel,
                        totalManwon = s.total.takeIf { it > 0 },
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            NotificationHelper.showIntakeSubmitted(
                context, s.token, s.customerPhone, nm,
                address = fullAddr.ifBlank { "주소 미입력" },
                dateLabel = dateLabel, totalManwon = s.total
            )
        }
        if (changed || maxSubmitted != since) {
            prefs.intakeImportedTokens = imported
            prefs.intakeSyncSinceMs = maxSubmitted
        }
    }

    /** confirmedDate(yyyy-MM-dd) 우선, 없으면 workYear/Month/Day → KST 0시 epoch. */
    private fun workMsOf(s: IntakeFormRepository.QuoteSubmission): Long? {
        s.confirmedDateIso?.let { iso ->
            runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Seoul")
                }
                sdf.parse(iso)?.let { return it.time }
            }
        }
        if (s.workYear > 0 && s.workMonth in 1..12 && s.workDay in 1..31) {
            return Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                set(s.workYear, s.workMonth - 1, s.workDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return null
    }
}
