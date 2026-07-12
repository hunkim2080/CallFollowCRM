package com.detailline.callfollowcrm.data.repository

import android.util.Log
import com.detailline.callfollowcrm.data.local.dao.ThreadBucketDao
import com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity
import com.detailline.callfollowcrm.domain.inbox.BucketPolicy
import com.detailline.callfollowcrm.domain.inbox.InboxClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 상담함/문자함 분류 저장소 — 로컬 1차 분류 적용 + 사장님 수동 이동. (2026-07-11 사장님)
 *   기본(행 없음)=상담함. GENERAL 행만 "문자함으로 뺀 대화" 를 뜻한다.
 */
class ThreadBucketRepository(private val dao: ThreadBucketDao) {

    /** suffix → 분류행. HomeViewModel 이 GENERAL suffix 집합으로 상담함/문자함 가른다. */
    fun observeBuckets(): Flow<Map<String, ThreadBucketEntity>> =
        dao.observeAll().map { list -> list.associateBy { it.suffix } }

    suspend fun allOnce(): List<ThreadBucketEntity> = runCatching { dao.allOnce() }.getOrDefault(emptyList())

    /** 이 번호가 문자함(GENERAL)으로 분류돼 있는가 — 문자함 채팅은 AI·고객정보 다 끄기 판단용. (2026-07-12 사장님) */
    suspend fun isGeneral(phone: String): Boolean {
        val suffix = suffixOf(phone)
        if (suffix.isBlank()) return false
        return runCatching { dao.findBySuffix(suffix)?.bucket == BucketPolicy.GENERAL }.getOrDefault(false)
    }

    /**
     * 수신 문자 1차 분류 적용(로컬). GENERAL 이면 문자함으로, 자동 CONSULT 승격이면 기존 GENERAL 제거.
     *   UNSURE 는 아무것도 안 함(상담함 유지). OWNER 이동은 이 함수가 절대 덮지 않는다(BucketPolicy).
     */
    suspend fun classifyLocal(
        phone: String,
        body: String,
        isSavedCustomer: Boolean,
        hasOwnerReply: Boolean,
        now: Long = System.currentTimeMillis()
    ): InboxClassifier.Verdict {
        val suffix = suffixOf(phone)
        if (suffix.isBlank()) return InboxClassifier.Verdict.UNSURE
        val verdict = InboxClassifier.classify(body, phone, isSavedCustomer, hasOwnerReply)
        // OWNER 가 이미 상담함으로 고정한 번호면 GENERAL 로 안 내려가야(효과 없음) — 표시용 verdict 는 CONSULT 로.
        if (verdict == InboxClassifier.Verdict.UNSURE) return verdict

        val existing = runCatching { dao.findBySuffix(suffix) }.getOrNull()
        if (existing?.source == BucketPolicy.SRC_OWNER) {
            return if (existing.bucket == BucketPolicy.GENERAL) InboxClassifier.Verdict.GENERAL
            else InboxClassifier.Verdict.CONSULT
        }
        val bucket = if (verdict == InboxClassifier.Verdict.GENERAL) BucketPolicy.GENERAL else BucketPolicy.CONSULT
        val reason = if (verdict == InboxClassifier.Verdict.GENERAL)
            InboxClassifier.generalReason(body, phone) else null

        when (val action = BucketPolicy.decide(
            existing = existing,
            suffix = suffix,
            verdictBucket = bucket,
            source = BucketPolicy.SRC_LOCAL,
            reason = reason,
            bodyHash = body.hashCode(),
            now = now
        )) {
            is BucketPolicy.Action.Write -> runCatching { dao.upsert(action.entity) }
                .onSuccess { Log.i(TAG, "classify $suffix → ${action.entity.bucket} (${action.entity.reason})") }
            BucketPolicy.Action.Delete -> runCatching { dao.deleteBySuffix(suffix) }
                .onSuccess { Log.i(TAG, "classify $suffix → 상담함 복귀(자동)") }
            BucketPolicy.Action.Noop -> {}
        }
        return verdict
    }

    /** 사장님이 "이건 고객이야" → 상담함으로(영구 보호). 문자함 스와이프/채팅 메뉴. */
    suspend fun moveToConsult(phone: String, now: Long = System.currentTimeMillis()) {
        val suffix = suffixOf(phone)
        if (suffix.isBlank()) return
        runCatching {
            dao.upsert(ThreadBucketEntity(suffix, BucketPolicy.CONSULT, BucketPolicy.SRC_OWNER, "사장님 이동", now, null))
        }
    }

    /** adAllowlist("광고 아님" 예외) 1회 이관 — suffix 를 상담함으로 영구 보호(OWNER). */
    suspend fun protectAsConsult(suffix: String, now: Long = System.currentTimeMillis()) {
        if (suffix.isBlank()) return
        val existing = runCatching { dao.findBySuffix(suffix) }.getOrNull()
        if (existing?.source == BucketPolicy.SRC_OWNER) return
        runCatching {
            dao.upsert(ThreadBucketEntity(suffix, BucketPolicy.CONSULT, BucketPolicy.SRC_OWNER, "광고 아님(이관)", now, null))
        }
    }

    /** 사장님이 "이건 고객 아니야" → 문자함으로(영구). 상담함 카드 스와이프. */
    suspend fun moveToGeneral(phone: String, now: Long = System.currentTimeMillis()) {
        val suffix = suffixOf(phone)
        if (suffix.isBlank()) return
        runCatching {
            dao.upsert(ThreadBucketEntity(suffix, BucketPolicy.GENERAL, BucketPolicy.SRC_OWNER, "사장님 이동", now, null))
        }
    }

    private fun suffixOf(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= 8) d.takeLast(8) else d
    }

    companion object { private const val TAG = "ThreadBucket" }
}
