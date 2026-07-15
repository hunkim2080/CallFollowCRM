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

    /**
     * 에이닷 "통화 내용 텍스트 저장" 으로 폴더에 떨어진 .txt 파일을 [AdotTextFolderScanner] 가 읽어 호출.
     *  - 파일명에서 뽑은 전화번호/시각을 fallback 으로 넘김(본문 파싱이 실패해도 매칭/중복판정 가능).
     *  - 토스트는 띄우지 않는다(스캐너가 결과 개수를 묶어서 알림). suspend 로 직접 결과 반환.
     */
    suspend fun importTextFile(
        container: AppContainer,
        text: String,
        knownPhone: String?,
        knownRecordedAt: Long?
    ): Result =
        importInternal(
            container = container,
            text = text,
            source = SummarySourceType.ADOT_SHARE,
            knownPhone = knownPhone,
            knownRecordedAt = knownRecordedAt
        )

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
        forceCustomerId: Long? = null,
        knownPhone: String? = null,
        knownRecordedAt: Long? = null
    ): Result {
        val parsedRaw = AdotShareTextParser.parse(text)
        // 파일명이 가장 신뢰도 높음 → 파일명에서 온 번호/시각이 있으면 그게 이긴다. 본문은 파일명이 없을 때만(공유 경로).
        //
        // ⚠️ 예전엔 본문(parsedRaw)이 이겼는데, 이게 "요약 알림 폭주 + 재과금"의 진짜 원인이었다. (2026-07-15 사장님/테스터)
        //   본문 시각은 "5. 15.(금) 오전 11:16" 라 **분 단위(초=0)** 로만 저장된다(AdotShareTextParser:143).
        //   그런데 스캐너들은 **파일명 시각(초까지)** 으로 중복을 찾는다(AdotTextFolderScanner:168, AdotFolderScanner:289).
        //   저장 11:16:00 vs 조회 11:16:37 → 37초 차이 > ±20초 창(2026-06-18 ecbf653 에서 ±2분→±20초로 좁힘) → **못 찾음**.
        //   → 이미 요약한 옛 통화를 앱 열 때마다 다시 요약(서버 LLM=돈) + "✨ 통화요약 완료!" 알림 재발사.
        //   파일명 초가 21~59 인 통화(약 65%)가 전부 해당됐다.
        //   파일명 시각을 저장하면 조회 키와 정확히 같아져(±0초) 중복이 확실히 잡힌다.
        //   (번호도 같은 이유로 파일명 우선 — 본문 phoneRegex 는 대화 중 언급된 남의 번호를 잡을 수 있다.)
        val parsed = parsedRaw.copy(
            phoneNumber = knownPhone?.filter { it.isDigit() }?.takeIf { it.isNotBlank() } ?: parsedRaw.phoneNumber,
            recordedAt = knownRecordedAt ?: parsedRaw.recordedAt
        )
        val now = System.currentTimeMillis()

        // 1) Customer 결정 — 기존 Customer 만 매칭. 없어도 자동 생성하지 않는다.
        //    추후 후속 처리 흐름에서 Customer 가 만들어질 때 phoneNumber 로 orphan 자동 연결.
        val customerId: Long? = when {
            forceCustomerId != null -> forceCustomerId
            parsed.phoneNumber != null -> container.customerRepository.findByPhone(parsed.phoneNumber)?.id
            else -> null
        }

        // 2) CallRecord 매칭 — 요약 시각(통화 시작)이 통화 [시작-10분 ~ 종료+10분] 안이면 연결(긴 통화 대응).
        var linkedCallRecordId: Long? = null
        val recAt = parsed.recordedAt
        if (parsed.phoneNumber != null && recAt != null) {
            val win = 10 * 60 * 1000L
            runCatching {
                val list = container.callRecordRepository.observeByPhone(parsed.phoneNumber).first()
                linkedCallRecordId = list.firstOrNull { val s = it.startedAt ?: it.endedAt; recAt >= s - win && recAt <= it.endedAt + win }?.id
            }
        }

        // 3) 중복 체크 — 같은 번호/시간 윈도우에 이미 있으면 update
        val existingNear: CallSummaryEntity? = if (parsed.phoneNumber != null && parsed.recordedAt != null) {
            container.callSummaryRepository.findExistingNear(parsed.phoneNumber, parsed.recordedAt)
        } else null

        // §18 맥미니 통화요약 — 에이닷 원문을 Haiku 로 한 줄+불릿+후속문자 초안으로 압축 (best-effort).
        //   실패하면 parsed.summaryText 그대로 사용 (graceful). 캐시: 같은 통화 재호출은 LLM 비용 0.
        //   ⚠️ 이미 요약이 저장된 통화면 서버를 아예 안 부른다 — 예전엔 existingNear 가 있어도(=update 경로라
        //      화면엔 아무 변화 없음) 무조건 호출해서, 조용히 돈만 나갔다. (2026-07-15 재과금 fix)
        val alreadySummarized = existingNear != null && !existingNear.summaryText.isNullOrBlank()
        val serverSummary = if (!alreadySummarized && !parsed.phoneNumber.isNullOrBlank() && !parsed.rawText.isNullOrBlank()) {
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
                title = serverSummary?.title ?: parsed.title ?: existingNear.title,
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
                title = serverSummary?.title ?: parsed.title,
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
