package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * 기기 주소록(삼성 연락처) 이름 조회. 2026-07-21 사장님 — "연락처에 저장돼 있으면 그대로 반영".
 *
 * 방식: 번호로 시스템 연락처를 조회(ContactsContract.PhoneLookup)해 저장된 이름을 돌려준다.
 *   이름은 삼성 연락처에 그대로 두고 시공막내는 "비춰주기"만 → 삼성에서 바꾸면 (캐시 만료 후) 반영, 중복 저장 없음.
 *   READ_CONTACTS 는 **선택 권한** — 없으면 항상 null(앱은 기존처럼 번호/자체저장 이름으로 동작).
 *
 * 캐시: 같은 번호 반복 조회 방지(세션). 못 찾으면 빈 문자열로 캐시해 재조회도 막는다.
 *   삼성 연락처 변경 반영을 위해 [clearCache] 를 앱 포그라운드 복귀/권한 획득 시 호출.
 */
object ContactNameResolver {

    private val cache = ConcurrentHashMap<String, String>()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** 번호로 저장된 연락처 이름. 없거나 권한 없으면 null. */
    fun lookup(context: Context, phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        if (!hasPermission(context)) return null
        val key = phone.filter { it.isDigit() }.ifBlank { phone }
        cache[key]?.let { return it.ifBlank { null } }
        val name = runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
        cache[key] = name ?: ""   // 못 찾음도 캐시(재조회 방지)
        return name
    }

    /** 삼성 연락처가 바뀌었을 수 있을 때(포그라운드 복귀·권한 획득) 캐시 비우기. */
    fun clearCache() = cache.clear()
}
