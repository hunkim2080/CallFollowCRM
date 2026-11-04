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

    /** 선택 앱별 길찾기 URI — **스킴은 여기 한 곳**(§12). 화면·위젯이 같이 쓴다. */
    fun uriFor(context: Context, app: String, addr: String): String {
        val enc = Uri.encode(addr)
        return when (app) {
            TMAP -> "tmap://search?name=$enc"
            KAKAO -> "kakaomap://search?q=$enc"
            NAVER -> "nmap://search?query=$enc&appname=${context.packageName}"
            else -> "geo:0,0?q=$enc"
        }
    }

    /** 기본 지도(geo) URI — 폴백용. */
    fun geoUri(addr: String): String = "geo:0,0?q=${Uri.encode(addr)}"

    private fun viewIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** 주소로 길찾기 — 선택 앱 URI 실행, 실패(미설치)면 geo: 기본 지도로 폴백. */
    fun launch(context: Context, app: String, addr: String) {
        if (addr.isBlank()) return
        val ok = runCatching { context.startActivity(viewIntent(uriFor(context, app, addr))); true }.getOrDefault(false)
        // 선택 앱이 안 깔려 있으면 기본 지도(geo)로 폴백.
        if (!ok && app != DEFAULT) {
            runCatching { context.startActivity(viewIntent(geoUri(addr))) }
        }
    }

    /**
     * 위젯 버튼용 — 탭하면 **바로 네비가 열리게** 미리 구워둘 Intent.
     *   PendingIntent 는 탭 시점에 함수 호출을 못 하니, 빌드 시점에 Intent 를 완성해 둔다.
     *   선택 앱이 안 깔려 있으면(해결 안 되면) 기본 지도(geo)로 구워 둔다. 주소 없으면 null.
     */
    fun bakedNavIntent(context: Context, app: String, addr: String): Intent? {
        if (addr.isBlank()) return null
        val primary = viewIntent(uriFor(context, app, addr))
        val resolves = runCatching {
            context.packageManager.resolveActivity(primary, 0) != null
        }.getOrDefault(false)
        return if (resolves) primary else viewIntent(geoUri(addr))
    }
}
