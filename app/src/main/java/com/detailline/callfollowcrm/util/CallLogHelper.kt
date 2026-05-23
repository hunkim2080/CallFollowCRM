package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CallLog
import com.detailline.callfollowcrm.domain.model.CallType

/**
 * 통화 종료 직후 최근 통화 기록을 조회한다.
 *
 * 알려진 제약:
 *  - Android 11+ 에서는 패키지 가시성/제조사 정책에 따라 최신 row가 즉시 반영되지 않을 수 있다.
 *  - 일부 OS에서는 통화 종료 직후 callLog row가 늦게 작성되어 endedAt 차이가 수 초 발생한다.
 *  - 권한이 없거나 row가 없으면 null 반환 → 호출부는 수동 입력 fallback 으로 진입한다.
 *  - 절대 throw 하지 않는다. SecurityException/IllegalStateException 등은 null 로 처리.
 */
object CallLogHelper {

    data class RecentCall(
        val phoneNumber: String,
        val type: CallType,
        val duration: Long,
        val date: Long
    )

    /**
     * 가장 최근 N개의 통화 기록을 가져온다. 중복 번호는 가장 최근 1건만 남긴다.
     * 권한 없거나 비어 있으면 빈 리스트.
     */
    fun queryRecent(context: Context, limit: Int = 20): List<RecentCall> {
        if (!PermissionHelper.isGranted(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        return try {
            val seen = mutableSetOf<String>()
            val out = mutableListOf<RecentCall>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                dateDescSortArgs(200),
                null
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < limit) {
                    val num = cursor.getString(0).orEmpty()
                    if (num.isBlank()) continue
                    val digits = num.filter { it.isDigit() }
                    val key = if (digits.length >= 8) digits.takeLast(8) else digits
                    if (!seen.add(key)) continue
                    out.add(
                        RecentCall(
                            phoneNumber = num,
                            type = mapType(cursor.getInt(1)),
                            duration = cursor.getLong(2),
                            date = cursor.getLong(3)
                        )
                    )
                }
            }
            out
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun queryLatest(context: Context): RecentCall? {
        if (!PermissionHelper.isGranted(context, Manifest.permission.READ_CALL_LOG)) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                dateDescSortArgs(1),
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val number = cursor.getString(0).orEmpty()
                val typeInt = cursor.getInt(1)
                val duration = cursor.getLong(2)
                val date = cursor.getLong(3)
                RecentCall(
                    phoneNumber = number,
                    type = mapType(typeInt),
                    duration = duration,
                    date = date
                )
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 특정 전화번호에 대한 시스템 통화 기록을 가져온다.
     * 알림을 못 누른 통화도 시스템엔 남아있으므로 사용자가 수동 입력할 때 이걸로 채워넣는다.
     *
     * 매칭 전략:
     *  - 입력 번호와 CallLog NUMBER 둘 다 숫자만 추출
     *  - 길이 8 이상이면 끝 8자리 일치, 미만이면 완전 일치
     *  - 권한 없거나 에러면 빈 리스트 (절대 throw 안 함)
     */
    fun queryByPhone(context: Context, phoneNumber: String, limit: Int = 50): List<RecentCall> {
        if (!PermissionHelper.isGranted(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val target = phoneNumber.filter { it.isDigit() }
        if (target.length < 7) return emptyList()

        return try {
            val results = mutableListOf<RecentCall>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                // 최근 500개만 훑어도 대부분의 케이스 커버 + 매칭은 Kotlin 쪽에서.
                dateDescSortArgs(500),
                null
            )?.use { cursor ->
                while (cursor.moveToNext() && results.size < limit) {
                    val rawNum = cursor.getString(0).orEmpty()
                    val rawDigits = rawNum.filter { it.isDigit() }
                    if (!isSamePhone(target, rawDigits)) continue
                    results.add(
                        RecentCall(
                            phoneNumber = rawNum,
                            type = mapType(cursor.getInt(1)),
                            duration = cursor.getLong(2),
                            date = cursor.getLong(3)
                        )
                    )
                }
            }
            results
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Android 11+ / OneUI 일부 단말에서 sortOrder 문자열에 "LIMIT n" 을 끼워넣으면
     * 쿼리가 무시되거나 빈 결과를 돌려준다 (예: Galaxy S24, SM-S918N 보고).
     * API 26+ 표준 Bundle 인자(`QUERY_ARG_SORT_*`, `QUERY_ARG_LIMIT`) 로 전달해야 안전.
     */
    private fun dateDescSortArgs(limit: Int): Bundle = Bundle().apply {
        putStringArray(
            ContentResolver.QUERY_ARG_SORT_COLUMNS,
            arrayOf(CallLog.Calls.DATE)
        )
        putInt(
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
        )
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
    }

    private fun isSamePhone(a: String, b: String): Boolean = when {
        a.isEmpty() || b.isEmpty() -> false
        a.length >= 8 && b.length >= 8 -> a.takeLast(8) == b.takeLast(8)
        else -> a == b
    }

    private fun mapType(typeInt: Int): CallType = when (typeInt) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        else -> CallType.UNKNOWN
    }
}
