package com.detailline.callfollowcrm.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 현장 도착 안내 — 지오펜싱(2026-06-03).
 *   다가오는 시공(오늘~+7일, 주소 있음) 현장을 5km 반경 지오펜스로 등록 → 진입 시
 *   GeofenceBroadcastReceiver 가 도착 알림. 주소→좌표는 Android Geocoder.
 *   백그라운드 동작엔 ACCESS_BACKGROUND_LOCATION("항상 허용") 권장.
 */
object GeofenceManager {

    private const val RADIUS_M = 5000f
    const val ACTION = "com.detailline.callfollowcrm.ARRIVAL_GEOFENCE"

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply { action = ACTION }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 7701, intent, flags)
    }

    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 다가오는 시공 현장 지오펜스를 다시 등록. 토글 OFF/권한 없음/대상 없음이면 제거만. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? CallFollowCrmApplication ?: return@withContext
        val container = app.container
        val client = LocationServices.getGeofencingClient(context)

        // 항상 기존 펜스 먼저 제거(중복/철 지난 현장 정리).
        runCatching { client.removeGeofences(pendingIntent(context)) }

        if (!container.preferences.arrivalAutoEnabled) return@withContext
        if (!hasFineLocation(context)) return@withContext

        val now = System.currentTimeMillis()
        val todayStart = DateTimeUtils.startOfDay(now)
        val weekEnd = todayStart + 7 * DateTimeUtils.DAY_MS
        val customers = runCatching { container.customerRepository.allOnce() }.getOrDefault(emptyList())

        val geocoder = runCatching { Geocoder(context, Locale.KOREA) }.getOrNull() ?: return@withContext
        val fences = customers.mapNotNull { c ->
            val addr = c.address?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val scheduled = c.scheduledWorkDate ?: return@mapNotNull null
            val day = DateTimeUtils.startOfDay(scheduled)
            if (day < todayStart || day > weekEnd) return@mapNotNull null
            val latLng = geocode(geocoder, addr) ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId("site_${c.id}")
                .setCircularRegion(latLng.first, latLng.second, RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        if (fences.isEmpty()) return@withContext

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // 등록 시 이미 안에 있어도 즉시 트리거 X
            .addGeofences(fences)
            .build()
        try {
            client.addGeofences(request, pendingIntent(context))
        } catch (_: SecurityException) {
            // 권한 회수 등 — 무시
        }
    }

    @Suppress("DEPRECATION")
    private fun geocode(geocoder: Geocoder, address: String): Pair<Double, Double>? {
        val results = runCatching { geocoder.getFromLocationName(address, 1) }.getOrNull()
        val a = results?.firstOrNull() ?: return null
        return a.latitude to a.longitude
    }
}
