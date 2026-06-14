package com.detailline.callfollowcrm.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 길찾기 지도앱 실행 — 티맵/카카오맵/네이버지도, 미설치 시 기본 지도로 폴백. (2026-06-14 사장님)
 *   한 번 고른 앱을 prefs.navApp 에 저장해두고 다음부터 바로 그 앱으로 연다.
 */
object NavApps {
    const val TMAP = "tmap"
    const val KAKAO = "kakao"
    const val NAVER = "naver"
    const val DEFAULT = "default"

    /** 선택 시트 표기 순서. */
    val ALL = listOf(TMAP, KAKAO, NAVER, DEFAULT)

    fun label(app: String): String = when (app) {
        TMAP -> "티맵"
        KAKAO -> "카카오맵"
        NAVER -> "네이버지도"
        else -> "기본 지도"
    }

    /** 주소로 길찾기 — 선택 앱 URI 실행, 실패(미설치)면 geo: 기본 지도로 폴백. */
    fun launch(context: Context, app: String, addr: String) {
        if (addr.isBlank()) return
        val enc = Uri.encode(addr)
        val uri = when (app) {
            TMAP -> "tmap://search?name=$enc"
            KAKAO -> "kakaomap://search?q=$enc"
            NAVER -> "nmap://search?query=$enc&appname=${context.packageName}"
            else -> "geo:0,0?q=$enc"
        }
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
            true
        }.getOrDefault(false)
        // 선택 앱이 안 깔려 있으면 기본 지도(geo)로 폴백.
        if (!ok && app != DEFAULT) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$enc")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
        }
    }
}
