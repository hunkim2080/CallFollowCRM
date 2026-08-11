package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 사용자 여정 이벤트 발사 — 화면 진입·버튼 클릭·캡쳐·LLM 사용을 모아 30초마다 배치 전송.
 *   서버: POST /api/event { owner_phone, events:[{event_name, screen?, target?, extra?, timestamp_ms?}] }
 *   → admin/user 페이지 "🚶 사용자 여정" 타임라인. (2026-06-23 사장님 / cowork 서버 완비)
 *
 * 설계:
 *  - track() 은 메인스레드에서 즉시 버퍼에 쌓기만(네트워크 X). 30초 루프(Application)가 flush.
 *  - owner_phone(사업자 번호) 없으면 보관만(전송 skip) → 번호 생기면 다음 flush 에 같이 감.
 *  - fail-open: 실패하면 되돌려 담아 다음 30초에 재시도. 버퍼 상한 200(오프라인 폭주 방지, 오래된 것부터 버림).
 */
class JourneyEventRepository(
    private val baseUrl: String = com.detailline.callfollowcrm.AppConfig.BASE_URL
) {
    private val client = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)   // 전체 호출 상한 — 재시도/route 누적 hang 방지 (2026-08-12 오프라인 감사)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val buffer = ArrayList<JSONObject>()
    private val lock = Any()
    private val maxBuffer = 200

    /** 이벤트 1건 적재(즉시 반환, 네트워크 X). screen/target/extra 는 선택. */
    fun track(
        eventName: String,
        screen: String? = null,
        target: String? = null,
        extra: Map<String, Any?>? = null
    ) {
        if (eventName.isBlank()) return
        val o = JSONObject()
        o.put("event_name", eventName)
        screen?.takeIf { it.isNotBlank() }?.let { o.put("screen", it) }
        target?.takeIf { it.isNotBlank() }?.let { o.put("target", it) }
        extra?.let { m ->
            val ex = JSONObject()
            m.forEach { (k, v) -> if (v != null) ex.put(k, v) }
            if (ex.length() > 0) o.put("extra", ex)
        }
        o.put("timestamp_ms", System.currentTimeMillis())
        synchronized(lock) {
            buffer.add(o)
            while (buffer.size > maxBuffer) buffer.removeAt(0)  // 오래된 것부터 버림
        }
    }

    /**
     * 버퍼를 비워 배치 전송. 실패 시 되돌려 담아 다음에 재시도. owner_phone 없으면 보관만.
     * @return 전송 성공(또는 보낼 것 없음)=true, 번호없음/전송실패=false. (백필이 '성공해야 flag' 판단에 사용)
     */
    suspend fun flush(ownerPhone: String): Boolean = withContext(Dispatchers.IO) {
        val digits = ownerPhone.filter { it.isDigit() }
        if (digits.length < 9) return@withContext false   // 사업자 번호 미설정 — 보관(전송 X)
        val batch: List<JSONObject> = synchronized(lock) {
            if (buffer.isEmpty()) return@withContext true   // 보낼 것 없음 = 성공 취급
            ArrayList(buffer).also { buffer.clear() }
        }
        val ok = runCatching {
            val arr = JSONArray()
            batch.forEach { arr.put(it) }
            val payload = JSONObject()
                .put("owner_phone", digits)
                .put("events", arr)
                .toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$baseUrl/api/event").post(payload).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        if (!ok) {
            // 전송 실패 → 앞에 되돌려 담아 다음 30초에 재시도(상한 적용).
            synchronized(lock) {
                buffer.addAll(0, batch)
                while (buffer.size > maxBuffer) buffer.removeAt(0)
            }
        }
        ok
    }
}
