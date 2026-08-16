package com.detailline.callfollowcrm.recording

import android.content.Context
import android.net.Uri
import android.util.Log
import com.detailline.callfollowcrm.util.LogRedact
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.domain.model.SummarySourceType
import com.detailline.callfollowcrm.service.NotificationHelper
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

    /**
     * @param interactive true(공유 경로)=이미 요약 있으면 "다시 요약?" 다이얼로그로 물음.
     *   false(통화종료 자동 스캔)=백그라운드라 묻지 않고 이미 있으면 조용히 스킵(재과금 방지).
     * @param phoneOverride 파일명에 번호가 없을 때(연락처 이름만 든 녹음) 호출부가 통화기록에서 되찾아 넘기는 번호.
     *   주면 파일명 파싱 없이 이걸 쓴다. (2026-07-16 — 폰마다 저장 규칙이 달라 번호를 안 넣는 기기가 있음)
     * @param recordedAtOverride 같은 이유의 녹음 시각. 파일명 시각이 있으면 보통 그걸 그대로 넘긴다.
     * @return 새로 저장했으면 true. (이미 있음/번호·시각 못 구함/서버 실패 = false)
     */
    suspend fun summarizeAndSave(
        context: Context,
        container: AppContainer,
        audioUri: String,
        fileName: String,
        interactive: Boolean = true,
        notifyOnComplete: Boolean = false,
        phoneOverride: String? = null,
        recordedAtOverride: Long? = null
    ): Boolean {
        // 번호·시각은 override 우선. 서버는 파일명을 안 보고 이 둘을 폼 필드로 받아 쓴다(§26).
        val parsed = AdotFilenameParser.parse(fileName)
        val phone = phoneOverride?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
            ?: parsed?.phoneNumber ?: return false
        val recordedAt = recordedAtOverride ?: parsed?.recordedAt ?: return false

        // 이미 처리된 통화(로컬에 요약 있음)를 재공유한 경우 → 조용히 건너뛰지 않고
        //   "다시 요약해드릴까요?" 를 묻는다(채팅 다이얼로그). 아니오면 기존 유지, 예면 force_refresh.
        //   자동 스캔(interactive=false)은 묻지 않고 스킵 — 백그라운드 + 재과금 방지.
        var forceRefresh = false
        if (container.callSummaryRepository.findExistingNear(phone, recordedAt) != null) {
            if (!interactive) return false
            if (!CallSummaryReprompt.ask(phone, recordedAt)) return false  // 아니오 → 기존 그대로
            forceRefresh = true
        }

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
            // 요약 시각(통화 시작)이 통화 [시작-10분 ~ 종료+10분] 안인 통화기록과 연결(긴 통화 대응).
            //   여러 통화가 가까우면 firstOrNull 은 엉뚱한 통화에 붙을 수 있어 '가장 가까운' 통화로 연결. (2026-06-15)
            val win = 10 * 60 * 1000L
            val rec = container.callRecordRepository.observeByPhone(phone).first()
                .filter { val s = it.startedAt ?: it.endedAt; recordedAt >= s - win && recordedAt <= it.endedAt + win }
                .minByOrNull { kotlin.math.abs((it.startedAt ?: it.endedAt) - recordedAt) }
            if (rec != null) {
                linkedCallRecordId = rec.id
                durationSec = (rec.duration).toInt().coerceAtLeast(0)
                direction = if (rec.callType == "OUTGOING") "outgoing" else "incoming"
            }
        }

        // 응답을 CallSummary 로 저장(있으면 merge, 없으면 insert). 두 번(캐시본/재처리본) 쓰여서 묶음.
        suspend fun save(res: com.detailline.callfollowcrm.ai.CallAudioSummaryRepository.Result) {
            val summaryText = buildString {
                res.oneLine?.let { append(it) }
                if (res.bullets.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(res.bullets.joinToString("\n"))
                }
            }.takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()
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
                title = res.title ?: res.oneLine ?: existing?.title,
                summaryText = summaryText ?: existing?.summaryText,
                recommendedMessage = res.followupSms ?: existing?.recommendedMessage,
                transcriptText = res.transcript ?: existing?.transcriptText,
                transcriptSegmentsJson = res.transcriptSegmentsJson ?: existing?.transcriptSegmentsJson,
                tagsJson = res.tags.takeIf { it.isNotEmpty() }?.let { org.json.JSONArray(it).toString() } ?: existing?.tagsJson,
                rawText = res.transcript ?: existing?.rawText,
                sourceType = SummarySourceType.AI_SERVER.name,
                updatedAt = now
            )
            container.callSummaryRepository.upsert(entity)
        }

        // 서버 받아쓰기+요약은 ~10~30초 걸린다. 그동안 채팅 통화카드가 "요약 중…" 스피너를
        // 보여주도록 진행 상태를 켠다. 성공/실패 무관하게 finally 에서 끈다.
        CallSummaryProgress.begin(phone, recordedAt)
        try {
            val resResult = container.callAudioSummaryRepository.summarize(
                audioBytes = audioBytes,
                fileName = fileName,
                phone = phone,
                startedAtMs = recordedAt,
                direction = direction,
                durationSec = durationSec,
                customerName = customer?.name,
                forceRefresh = forceRefresh
            )
            val res = resResult.getOrNull()
            if (res == null) {
                // 실패 원인을 그대로 남긴다(HTTP 코드/네트워크 예외) — 서버 핸드오프 진단용.
                Log.w(TAG, "server summarize failed: $fileName  bytes=${audioBytes.size}", resResult.exceptionOrNull())
                return false
            }
            save(res)

            // 서버가 캐시본(이미 처리)을 즉시 줬는데 아직 안 물어본 경우(로컬엔 없던 통화) →
            //   화면엔 캐시본을 띄워둔 채 "다시 요약?" 묻고, 예면 force_refresh 로 재처리해 덮어쓴다.
            if (res.cached && !forceRefresh && interactive) {
                if (CallSummaryReprompt.ask(phone, recordedAt)) {
                    container.callAudioSummaryRepository.summarize(
                        audioBytes = audioBytes,
                        fileName = fileName,
                        phone = phone,
                        startedAtMs = recordedAt,
                        direction = direction,
                        durationSec = durationSec,
                        customerName = customer?.name,
                        forceRefresh = true
                    ).getOrNull()?.let { save(it) }
                }
            }
            // 자동 통화요약(통화 끝→워커)일 때만 "요약했어요" 알림 — 잠깐 떴다 사라짐, 탭→통화방.
            if (notifyOnComplete) {
                NotificationHelper.showSummaryReadyNotification(
                    context = context,
                    phoneNumber = phone,
                    displayName = customer?.name,
                    callRecordId = linkedCallRecordId,
                    customerId = customer?.id,  // 번호 포맷 무관하게 '그 고객'으로 정확히 열기(엉뚱한 곳 이동 방지).
                    preview = res.title?.takeIf { it.isNotBlank() } ?: res.oneLine  // 에이닷처럼 요약 주제 미리보기
                )
            }
            Log.d(TAG, "saved audio summary: ${LogRedact.phone(phone)} @ $recordedAt cached=${res.cached} force=$forceRefresh notify=$notifyOnComplete")
            return true
        } finally {
            CallSummaryProgress.end(phone, recordedAt)
        }
    }
}
