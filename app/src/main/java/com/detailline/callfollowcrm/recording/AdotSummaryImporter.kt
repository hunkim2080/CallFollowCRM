package com.detailline.callfollowcrm.recording

import android.content.Context
import android.widget.Toast
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.domain.model.SummarySourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 에이닷 공유 텍스트 또는 사용자가 직접 붙여넣은 텍스트를 받아
 *  1) 파싱 → 2) 번호로 Customer 매칭(없으면 생성, 번호 못 찾으면 unlinked) →
 *  3) 통화기록(±10분) 자동 연결 → 4) 중복이면 update, 아니면 insert.
 *
 * 모든 경로에서 토스트로 결과를 알려준다.
 */
object AdotSummaryImporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Result(
        val summaryId: Long,
        val customerId: Long?,
        val callRecordLinked: Boolean,
        val updatedExisting: Boolean
    )

    fun importFromShare(context: Context, container: AppContainer, text: String) {
        val appCtx = context.applicationContext
        if (text.isBlank()) {
            Toast.makeText(appCtx, "공유된 텍스트가 비어 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val result = runCatching { importInternal(container, text, SummarySourceType.ADOT_SHARE) }
                .getOrNull()
            withContext(Dispatchers.Main) {
                val msg = buildMessage(result)
                Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** CustomerDetail 에서 사용자 직접 붙여넣기. customerId 강제 지정(번호 매칭 실패해도 이 고객에 붙임). */
    fun importPasted(
        context: Context,
        container: AppContainer,
        text: String,
        customerId: Long,
        onDone: ((Result?) -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        if (text.isBlank()) {
            Toast.makeText(appCtx, "붙여넣은 텍스트가 비어 있어요", Toast.LENGTH_SHORT).show()
            onDone?.invoke(null)
            return
        }
        scope.launch {
            val result = runCatching {
                importInternal(container, text, SummarySourceType.MANUAL_PASTE, forceCustomerId = customerId)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                Toast.makeText(appCtx, buildMessage(result), Toast.LENGTH_LONG).show()
                onDone?.invoke(result)
            }
        }
    }

    private suspend fun importInternal(
        container: AppContainer,
        text: String,
        source: SummarySourceType,
        forceCustomerId: Long? = null
    ): Result {
        val parsed = AdotShareTextParser.parse(text)
        val now = System.currentTimeMillis()

        // 1) Customer 결정 — 기존 Customer 만 매칭. 없어도 자동 생성하지 않는다.
        //    추후 후속 처리 흐름에서 Customer 가 만들어질 때 phoneNumber 로 orphan 자동 연결.
        val customerId: Long? = when {
            forceCustomerId != null -> forceCustomerId
            parsed.phoneNumber != null -> container.customerRepository.findByPhone(parsed.phoneNumber)?.id
            else -> null
        }

        // 2) CallRecord 매칭 (±10분)
        var linkedCallRecordId: Long? = null
        if (parsed.phoneNumber != null && parsed.recordedAt != null) {
            val from = parsed.recordedAt - 10 * 60 * 1000
            val to = parsed.recordedAt + 10 * 60 * 1000
            runCatching {
                val list = container.callRecordRepository.observeByPhone(parsed.phoneNumber).first()
                linkedCallRecordId = list.firstOrNull { it.endedAt in from..to }?.id
            }
        }

        // 3) 중복 체크 — 같은 번호/시간 윈도우에 이미 있으면 update
        val existingNear: CallSummaryEntity? = if (parsed.phoneNumber != null && parsed.recordedAt != null) {
            container.callSummaryRepository.findExistingNear(parsed.phoneNumber, parsed.recordedAt)
        } else null

        // §18 맥미니 통화요약 — 에이닷 원문을 Haiku 로 한 줄+불릿+후속문자 초안으로 압축 (best-effort).
        //   실패하면 parsed.summaryText 그대로 사용 (graceful). 캐시: 같은 통화 재호출은 LLM 비용 0.
        val serverSummary = if (!parsed.phoneNumber.isNullOrBlank() && !parsed.rawText.isNullOrBlank()) {
            val name = runCatching { container.customerRepository.findByPhone(parsed.phoneNumber!!)?.name }.getOrNull()
            container.callSummaryServerRepository.summarize(
                phone = parsed.phoneNumber!!,
                rawText = parsed.rawText!!,
                startedAtMs = parsed.recordedAt ?: now,
                customerName = name
            ).getOrNull()
        } else null
        val enrichedSummaryText = serverSummary?.let { s ->
            buildString {
                s.oneLine?.let { append(it) }
                if (s.bullets.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(s.bullets.joinToString("\n"))
                }
            }.takeIf { it.isNotBlank() }
        } ?: parsed.summaryText
        val enrichedFollowup = serverSummary?.followupSms

        val entity = if (existingNear != null) {
            existingNear.copy(
                customerId = customerId ?: existingNear.customerId,
                callRecordId = linkedCallRecordId ?: existingNear.callRecordId,
                phoneNumber = parsed.phoneNumber ?: existingNear.phoneNumber,
                recordedAt = parsed.recordedAt ?: existingNear.recordedAt,
                title = parsed.title ?: existingNear.title,
                summaryText = enrichedSummaryText ?: existingNear.summaryText,
                recommendedMessage = enrichedFollowup ?: existingNear.recommendedMessage,
                transcriptText = parsed.transcriptText ?: existingNear.transcriptText,
                rawText = parsed.rawText,
                sourceType = source.name,
                updatedAt = now
            )
        } else {
            CallSummaryEntity(
                customerId = customerId,
                callRecordId = linkedCallRecordId,
                phoneNumber = parsed.phoneNumber,
                recordedAt = parsed.recordedAt,
                title = parsed.title,
                summaryText = enrichedSummaryText,
                recommendedMessage = enrichedFollowup,
                transcriptText = parsed.transcriptText,
                rawText = parsed.rawText,
                sourceType = source.name,
                createdAt = now,
                updatedAt = now
            )
        }
        val id = container.callSummaryRepository.upsert(entity)

        return Result(
            summaryId = id,
            customerId = customerId,
            callRecordLinked = linkedCallRecordId != null,
            updatedExisting = existingNear != null
        )
    }

    private fun buildMessage(r: Result?): String {
        if (r == null) return "에이닷 요약을 가져오지 못했어요"
        return buildString {
            append(if (r.updatedExisting) "기존 요약 업데이트" else "에이닷 요약 저장")
            if (r.customerId == null) append(" · 고객 미연결 (후속 처리 시 자동 연결)")
            if (r.callRecordLinked) append(" · 통화기록 연결")
        }
    }
}
