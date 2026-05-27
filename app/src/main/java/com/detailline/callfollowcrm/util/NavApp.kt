package com.detailline.callfollowcrm.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 카드 펼침 액션의 [📍 길찾기] 가 사용할 외부 네비 앱.
 *
 * 사장님 결정 (2026-05-27): 3개 옵션만 — 카카오내비/네이버지도/티맵.
 * 한 시공자가 자기 손에 익은 앱 1개를 [설정] 에서 골라 두면, 그 후로는 1탭으로 launch.
 *
 * 데이터 흐름:
 *   - 좌표 (lat/lng) 있음 → 각 앱의 navigate(목적지) 모드 = 즉시 안내 시작
 *   - 좌표 없음 + 주소/이름만 → search 모드 = 검색 결과 화면 (사장님이 한 번 더 "출발" 탭)
 *   - 둘 다 없음 → 호출하지 않음 (HomeScreen 측에서 토스트로 처리)
 *
 * 카카오내비 한정 — 공식 URL scheme 이 좌표 전제라 search 모드가 없음.
 * 좌표 없을 때 카카오내비 선택 사용자에겐 시스템 geo: URI 로 fallback (폰의 디폴트 지도앱 chooser).
 */
enum class NavApp(
    val key: String,
    val label: String,
    val packageName: String
) {
    KAKAO_NAVI("kakao_navi", "카카오내비", "com.locnall.KimGiSa"),
    NAVER_MAP("naver_map",  "네이버지도", "com.nhn.android.nmap"),
    TMAP("tmap",            "티맵",       "com.skt.tmap.ku");

    companion object {
        fun fromKey(key: String?): NavApp? = values().firstOrNull { it.key == key }
    }
}

/**
 * 길찾기 Intent 빌드 + launch. 설치 안 됐으면 Play 스토어로 안내.
 *
 * @param destinationName 검색/표시용 이름 (예: "마곡엠밸리 8단지" / "서울 강서구 마곡동 740")
 * @param lat WGS84 위도. null 이면 search 모드 fallback
 * @param lng WGS84 경도. null 이면 search 모드 fallback
 */
object NavLauncher {

    fun launch(
        context: Context,
        navApp: NavApp,
        destinationName: String?,
        lat: Double? = null,
        lng: Double? = null
    ) {
        val hasCoord = lat != null && lng != null
        val hasName = !destinationName.isNullOrBlank()
        if (!hasCoord && !hasName) {
            Toast.makeText(context, "주소 정보가 없어요", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = buildUri(navApp, destinationName, lat, lng)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // app-specific scheme 이면 package 명시. 시스템 geo: 는 chooser 띄움.
            if (uri.scheme != "geo" && uri.scheme != "https") {
                setPackage(navApp.packageName)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 미설치 → 스토어 직행 (사장님이 한 번 깔면 다음부턴 정상)
            Toast.makeText(
                context,
                "${navApp.label} 앱이 설치되지 않았어요. 스토어를 엽니다.",
                Toast.LENGTH_LONG
            ).show()
            openStore(context, navApp.packageName)
        }
    }

    /**
     * URL scheme 매트릭스:
     *   - 카카오내비: 좌표 전제. `kakaomap://route?ep=Y,X&by=CAR` (카카오맵 길찾기로 갈 수도 있지만
     *     사장님이 카카오내비 깔았다면 보통 카카오맵 같이 깔려있음. 좌표 없으면 geo: fallback)
     *     좌표 없을 때 → `geo:0,0?q=NAME` 로 시스템 chooser.
     *   - 네이버지도: nmap://route/car?dlat=&dlng=&dname=&appname=...  /  nmap://search?query=...
     *   - 티맵: tmap://route?goalname=&goalx=&goaly=  /  tmap://search?name=...
     *
     * appname 파라미터는 네이버지도 deeplink 규격 (호출자 식별).
     */
    private fun buildUri(
        navApp: NavApp,
        destinationName: String?,
        lat: Double?,
        lng: Double?
    ): Uri {
        val hasCoord = lat != null && lng != null
        val name = destinationName?.trim().orEmpty()
        val encName = Uri.encode(name)

        return when (navApp) {
            NavApp.KAKAO_NAVI -> {
                if (hasCoord) {
                    // 카카오내비 공식 URL: 좌표를 직접 navigate. ep = "y,x" (lat,lng).
                    //   원래는 kakaonavi-sdk:// 인데 그건 SDK 통합용. 외부 호출은 카카오맵 deeplink 가 안정적.
                    Uri.parse("kakaomap://route?ep=${lat},${lng}&by=CAR")
                } else {
                    // 좌표 없음 → 시스템 geo: 로 chooser (카카오맵 포함 모든 지도 앱이 받음)
                    Uri.parse("geo:0,0?q=$encName")
                }
            }
            NavApp.NAVER_MAP -> {
                if (hasCoord) {
                    Uri.parse(
                        "nmap://route/car?dlat=${lat}&dlng=${lng}&dname=$encName" +
                                "&appname=com.detailline.callfollowcrm"
                    )
                } else {
                    Uri.parse("nmap://search?query=$encName&appname=com.detailline.callfollowcrm")
                }
            }
            NavApp.TMAP -> {
                if (hasCoord) {
                    Uri.parse("tmap://route?goalname=$encName&goalx=${lng}&goaly=${lat}")
                } else {
                    Uri.parse("tmap://search?name=$encName")
                }
            }
        }
    }

    private fun openStore(context: Context, packageName: String) {
        val storeUri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, storeUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Play 스토어 앱도 없으면 웹으로
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
