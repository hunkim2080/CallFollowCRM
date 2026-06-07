package com.detailline.callfollowcrm.recording

import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import kotlinx.coroutines.flow.first

/**
 * 녹음 파일을 받아서 기존 고객 / 통화 기록과 자동 매칭한다.
 *
 * 매칭 규칙 (v0.4 변경):
 *  - 파일명에서 전화번호 추출 → 해당 번호의 Customer 가 "이미 있으면" 사용. **없어도 자동 생성하지 않는다.**
 *  - 파일명에서 녹음 시간 추출 → 해당 번호의 CallRecord 중 가장 가까운 시간(±10분) 찾으면 연결
 *  - 추출된 phoneNumber 는 attachment.phoneNumber 로 그대로 저장. 이후 같은 번호로
 *    Customer 가 후속 처리 흐름에서 만들어질 때 [com.detailline.callfollowcrm.data.repository.CustomerRepository.upsertByPhone]
 *    이 orphan 첨부를 자동 연결한다.
 */
object RecordingMatcher {

    data class MatchResult(
        val attachmentId: Long,
        val customerId: Long?,
        val phoneNumber: String?,
        val callRecordLinked: Boolean
    )

    suspend fun attach(
        container: AppContainer,
        fileUri: String,
        displayName: String,
        sourceType: RecordingSourceType
    ): MatchResult {
        val parsed = AdotFilenameParser.parse(displayName)
        val phone = parsed?.phoneNumber

        // 기존 Customer 만 매칭. 없으면 customerId=null 로 두고 phoneNumber 만 보관.
        val customerId: Long? = phone?.let { container.customerRepository.findByPhone(it)?.id }

        // CallRecord 찾기 — 녹음 시각(통화 시작)이 통화 [시작-10분 ~ 종료+10분] 안이면 연결(긴 통화 대응).
        var linkedCallRecordId: Long? = null
        if (phone != null && parsed != null) {
            val recAt = parsed.recordedAt
            val win = 10 * 60 * 1000L
            runCatching {
                val list = container.callRecordRepository.observeByPhone(phone).first()
                linkedCallRecordId = list.firstOrNull { val s = it.startedAt ?: it.endedAt; recAt >= s - win && recAt <= it.endedAt + win }?.id
            }
        }

        val attachmentId = container.recordingRepository.add(
            fileUri = fileUri,
            fileName = displayName,
            sourceType = sourceType,
            customerId = customerId,
            callRecordId = linkedCallRecordId,
            phoneNumber = phone,
            duration = null
        )

        return MatchResult(
            attachmentId = attachmentId,
            customerId = customerId,
            phoneNumber = phone,
            callRecordLinked = linkedCallRecordId != null
        )
    }
}
