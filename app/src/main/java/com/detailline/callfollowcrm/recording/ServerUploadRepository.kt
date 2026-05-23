package com.detailline.callfollowcrm.recording

/**
 * Phase 4 스켈레톤. 실제 서버 업로드 구현은 추후.
 *
 * 향후 구조:
 *  Android 앱 → 사용자 소유 서버 → STT API → 요약 API → 서버 DB → 앱
 *
 * 절대 하지 않는 일:
 *  - 앱 안에 API 키 보관
 *  - 외부 AI API 직접 호출
 */
interface ServerUploadRepository {
    suspend fun uploadRecording(request: RecordingUploadRequest): Result<RecordingUploadResult>
}

data class RecordingUploadRequest(
    val recordingAttachmentId: Long,
    val fileUri: String,
    val customerId: Long?,
    val callRecordId: Long?
)

data class RecordingUploadResult(
    val serverRecordingId: String,
    val acceptedAt: Long
)

class NoOpServerUploadRepository : ServerUploadRepository {
    override suspend fun uploadRecording(request: RecordingUploadRequest): Result<RecordingUploadResult> {
        // TODO: Phase 4 — 실제 서버 업로드. 현재는 동작하지 않는다.
        return Result.failure(UnsupportedOperationException("Server upload is not implemented in v1"))
    }
}
