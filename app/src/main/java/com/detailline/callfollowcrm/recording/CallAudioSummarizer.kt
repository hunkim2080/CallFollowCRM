package com.detailline.callfollowcrm.recording

import android.content.Context
import android.net.Uri
import android.util.Log
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.domain.model.SummarySourceType
import kotlinx.coroutines.flow.first

/**
 * 무료 녹음(m4a) → 맥미니 §26 (`/api/call-audio-summary`) 로 보내 받아쓰기+요약 → CallSummary 저장.
 *
 * 에이닷 "녹음 파일 공유"(무료) 로 들어온 m4a 를 [RecordingShareHandler] / [AdotFolderScanner] 가
 * RecordingAttachment 로 저장한 뒤 이걸 호출한다. 저장된 CallSummary 는 채팅 통화카드의 "AI 요약됨"
 * 상태로 그대로 표시됨(txt 경로와 동일 화면 재사용).
 *
 * 텍스트(txt) 경로([AdotSummaryImporter]) 와 달리 서버가 **이미 요약까지** 끝내서 주므로,
 * 여기서는 LLM 을 다시 부르지 않고 응답을 바로 CallSummary 로 변환한다.
 *
 * 중복(=재과금) 방지: 파일명의 번호+시각으로 이미 요약이 있으면 스킵(서버에도 캐시 있음).
 */
object CallAudioSummarizer {

    private const val TAG = "CallAudioSum"

    /** @return 새로 저장했으면 true. (이미 있음/파일명 패턴 불일치/서버 실패 = false) */
    suspend fun summarizeAndSave(
        context: Context,
        container: AppContainer,
        audioUri: String,
        fileName: String
    ): Boolean {
        val parsed = AdotFilenameParser.parse(fileName) ?: return false
        val phone = parsed.phoneNumber
        val recordedAt = parsed.recordedAt

        // 이미 요약된 통화면 스킵 (txt 로 먼저 받았거나 재처리).
        if (container.callSummaryRepository.findExistingNear(phone, recordedAt) != null) return false

        val audioBytes = runCatching {
            context.contentResolver.openInputStream(Uri.parse(audioUri))?.use { it.readBytes() }
        }.getOrNull()
        if (audioBytes == null || audioBytes.isEmpty()) {
            Log.w(TAG, "audio read failed/empty: $fileName")
            return false
        }

        // 고객/통화기록 매칭 (±10분) — 방향·길이·고객명 채우기.
        val customer = runCatching { container.customerRepository.findByPhone(phone) }.getOrNull()
        var linkedCallRecordId: Long? = null
        var direction = "incoming"
        var durationSec = 0
        runCatching {
            val from = recordedAt - 10 * 60 * 1000
            val to = recordedAt + 10 * 60 * 1000
            val rec = container.callRecordRepository.observeByPhone(phone).first()
                .firstOrNull { it.endedAt in from..to }
            if (rec != null) {
                linkedCallRecordId = rec.id
                durationSec = (rec.duration).toInt().coerceAtLeast(0)
                direction = if (rec.callType == "OUTGOING") "outgoing" else "incoming"
            }
        }

        val res = container.callAudioSummaryRepository.summarize(
            audioBytes = audioBytes,
            fileName = fileName,
            phone = phone,
            startedAtMs = recordedAt,
            direction = direction,
            durationSec = durationSec,
            customerName = customer?.name
        ).getOrNull()
        if (res == null) {
            Log.w(TAG, "server summarize failed: $fileName")
            return false
        }

        val summaryText = buildString {
            res.oneLine?.let { append(it) }
            if (res.bullets.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(res.bullets.joinToString("\n"))
            }
        }.takeIf { it.isNotBlank() }

        val now = System.currentTimeMillis()
        // 혹시 그 사이 txt 경로가 먼저 저장했으면 update, 아니면 insert.
        val existing = container.callSummaryRepository.findExistingNear(phone, recordedAt)
        val entity = (existing ?: CallSummaryEntity(
            customerId = null,
            phoneNumber = phone,
            recordedAt = recordedAt,
            sourceType = SummarySourceType.AI_SERVER.name,
            createdAt = now,
            updatedAt = now
        )).copy(
            customerId = customer?.id ?: existing?.customerId,
            callRecordId = linkedCallRecordId ?: existing?.callRecordId,
            phoneNumber = phone,
            recordedAt = recordedAt,
            title = res.oneLine ?: existing?.title,
            summaryText = summaryText ?: existing?.summaryText,
            recommendedMessage = res.followupSms ?: existing?.recommendedMessage,
            transcriptText = res.transcript ?: existing?.transcriptText,
            rawText = res.transcript ?: existing?.rawText,
            sourceType = SummarySourceType.AI_SERVER.name,
            updatedAt = now
        )
        container.callSummaryRepository.upsert(entity)
        Log.d(TAG, "saved audio summary: $phone @ $recordedAt")
        return true
    }
}
