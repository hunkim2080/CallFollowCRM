package com.detailline.callfollowcrm.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.detailline.callfollowcrm.data.local.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 내 데이터 내보내기 / 가져오기 (데이터 안전 1단계 — 2026-08-10 사장님).
 *
 * 목적: 앱 데이터가 "이 폰 한 대"에만 있어 재설치/기기변경/데이터삭제 시 통째로 사라지는 문제
 *   (데이터 안전 감사 1.5/5, 치명적 1·2·3) 의 즉효 방어. 서버 없이 앱만으로 사장님이 사본을 뺀다.
 *
 * 형식: `.zip` 안에
 *   - backup.json   : 전 테이블 덤프(복원용). {format, dbVersion, exportedAt, tables:{name:[rows]}}
 *   - 고객요약.csv    : 사람이 엑셀로 여는 요약(돈 분쟁 증거·눈으로 확인용)
 *
 * 복원(import): backup.json 을 읽어 각 테이블에 INSERT OR REPLACE (PK 기준 upsert).
 *   - 삭제하지 않는다 — 같은 id 는 덮고, 없는 건 추가만. 잘못된 파일로도 기존 데이터가 지워지지 않게.
 *   - 현재 스키마에 있는 컬럼만 넣는다(버전 차이 안전). 없는 테이블은 건너뜀.
 *
 * 라이브러리 없이 raw SQL(SupportSQLiteDatabase) 로 전 테이블을 훑어 테이블 목록을 하드코딩하지 않는다
 *   → 앞으로 테이블이 늘어도 자동 포함.
 */
object DataBackup {

    private const val PREFS = "data_backup"
    private const val KEY_LAST = "last_backup_at"
    private const val BACKUP_JSON = "backup.json"
    private const val CSV_NAME = "고객요약.csv"

    /** 우리 백업 봉투(envelope) 버전. 앞으로 형식이 바뀌면 올린다. 이보다 높은 백업은 복원 거부. */
    private const val FORMAT = 1

    /**
     * 백업에서 제외할 테이블 — 시스템에서 다시 채워지는 캐시 / 분석 이벤트.
     * (없는 이름은 매칭 안 돼 무해. 나머지 전부 백업 = 참조 무결성 보존.)
     */
    private val SKIP_TABLES = setOf(
        "cached_messages",      // 문자 캐시 — 시스템 SMS/MMS 에서 재동기화됨
        "sms_contacts_cache",   // 연락처 캐시 — 시스템에서 재생성
        "suggestion_events",    // 추천 채택 분석 이벤트 — 통계용, 소실돼도 무방
        "android_metadata", "room_master_table", "sqlite_sequence", "sqlite_stat1"
    )

    data class ExportResult(val uri: Uri, val fileName: String, val rows: Int, val tables: Int, val customers: Int)
    data class ImportResult(val rows: Int, val tables: Int, val customers: Int)

    class NewerBackupException : Exception("더 최신 버전에서 만든 백업이에요")
    class EmptyBackupException : Exception("백업 파일에서 데이터를 찾지 못했어요")

    // ─────────────────────────── 내보내기 ───────────────────────────

    /** IO 스레드에서 호출. 성공 시 공유 가능한 zip URI 반환. */
    fun export(context: Context): ExportResult {
        val db = AppDatabase.getInstance(context.applicationContext).openHelper.writableDatabase

        val tablesObj = JSONObject()
        var totalRows = 0
        var customerCount = 0
        var customersArr: JSONArray? = null

        for (table in userTables(db)) {
            val arr = JSONArray()
            db.query("SELECT * FROM `$table`").use { c ->
                val cols = c.columnNames
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in cols.indices) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(cols[i], JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(cols[i], c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(cols[i], c.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> row.put(cols[i], c.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> row.put(
                                cols[i],
                                JSONObject().put("__blob_b64", Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                            )
                        }
                    }
                    arr.put(row)
                    totalRows++
                }
            }
            tablesObj.put(table, arr)
            if (table == "customers") { customersArr = arr; customerCount = arr.length() }
        }

        val root = JSONObject().apply {
            put("format", FORMAT)
            put("dbVersion", db.version)
            put("app", "시공막내")
            put("exportedAt", System.currentTimeMillis())
            put("tables", tablesObj)
        }

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val fileName = "시공막내_백업_$stamp.zip"
        val zipFile = File(dir, fileName)
        ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry(BACKUP_JSON))
            zos.write(root.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(CSV_NAME))
            zos.write(buildCustomerCsv(customersArr))
            zos.closeEntry()
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        setLastBackupAt(context, System.currentTimeMillis())
        return ExportResult(uri, fileName, totalRows, tablesObj.length(), customerCount)
    }

    // ─────────────────────────── 가져오기(복원) ───────────────────────────

    /** IO 스레드에서 호출. uri = 사용자가 고른 백업 파일(zip 또는 json). */
    fun import(context: Context, uri: Uri): ImportResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw EmptyBackupException()
        val jsonText = extractBackupJson(bytes) ?: throw EmptyBackupException()
        val root = JSONObject(jsonText)

        if (root.optInt("format", 1) > FORMAT) throw NewerBackupException()
        val tables = root.optJSONObject("tables") ?: throw EmptyBackupException()
        if (tables.length() == 0) throw EmptyBackupException()

        val db = AppDatabase.getInstance(context.applicationContext).openHelper.writableDatabase

        var totalRows = 0
        var customerCount = 0
        var tableCount = 0

        // REPLACE 가 자식행을 지우지 않도록 FK 잠시 끔 (트랜잭션 밖에서만 토글 가능).
        db.setForeignKeyConstraintsEnabled(false)
        db.beginTransaction()
        try {
            val names = tables.keys()
            while (names.hasNext()) {
                val table = names.next()
                if (table in SKIP_TABLES) continue
                val curCols = tableColumns(db, table)
                if (curCols.isEmpty()) continue   // 현재 스키마에 없는 테이블 → 건너뜀
                val arr = tables.optJSONArray(table) ?: continue
                var restoredInThisTable = 0
                for (r in 0 until arr.length()) {
                    val row = arr.optJSONObject(r) ?: continue
                    val cols = ArrayList<String>()
                    val keys = row.keys()
                    while (keys.hasNext()) { val k = keys.next(); if (k in curCols) cols.add(k) }
                    if (cols.isEmpty()) continue
                    val placeholders = cols.joinToString(",") { "?" }
                    val colList = cols.joinToString(",") { "`$it`" }
                    val args = arrayOfNulls<Any?>(cols.size)
                    for (i in cols.indices) args[i] = bindValue(row.get(cols[i]))
                    db.execSQL("INSERT OR REPLACE INTO `$table` ($colList) VALUES ($placeholders)", args)
                    restoredInThisTable++
                    totalRows++
                }
                if (restoredInThisTable > 0) tableCount++
                if (table == "customers") customerCount = restoredInThisTable
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.setForeignKeyConstraintsEnabled(true)
        }
        return ImportResult(totalRows, tableCount, customerCount)
    }

    // ─────────────────────────── 마지막 백업 시각 ───────────────────────────

    fun lastBackupAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    private fun setLastBackupAt(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST, ms).apply()
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    /** 사용자 테이블 목록(시스템/스킵 제외). */
    private fun userTables(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> {
        val out = ArrayList<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0)
                if (n in SKIP_TABLES) continue
                if (n.startsWith("android_") || n.startsWith("sqlite_") || n == "room_master_table") continue
                out.add(n)
            }
        }
        return out
    }

    private fun tableColumns(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Set<String> {
        val cols = LinkedHashSet<String>()
        try {
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                if (nameIdx < 0) return emptySet()
                while (c.moveToNext()) cols.add(c.getString(nameIdx))
            }
        } catch (_: Exception) { return emptySet() }
        return cols
    }

    /** JSON 값 → SQLite bind 인자(null / Long / Double / String / ByteArray). */
    private fun bindValue(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> {
            val b64 = v.optString("__blob_b64", "")
            if (b64.isNotEmpty()) Base64.decode(b64, Base64.NO_WRAP) else v.toString()
        }
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && !d.isInfinite()) v.toLong() else d
        }
        is Boolean -> if (v) 1L else 0L
        else -> v.toString()
    }

    /** zip 이면 backup.json 추출, 아니면 raw json 텍스트로 간주. */
    private fun extractBackupJson(bytes: ByteArray): String? {
        val isZip = bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() // "PK"
        if (!isZip) return String(bytes, Charsets.UTF_8)
        ZipInputStream(bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == BACKUP_JSON || e.name.endsWith("/$BACKUP_JSON")) {
                    val buf = ByteArrayOutputStream()
                    zis.copyTo(buf)
                    return buf.toString("UTF-8")
                }
                e = zis.nextEntry
            }
        }
        return null
    }

    /** 고객 요약 CSV (엑셀에서 한글 안 깨지게 UTF-8 BOM). */
    private fun buildCustomerCsv(customers: JSONArray?): ByteArray {
        val sb = StringBuilder()
        sb.append('﻿') // BOM
        sb.append("이름,전화번호,시공예약일,총금액(원),계약금(원),잔금(원),메모\r\n")
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        if (customers != null) {
            for (i in 0 until customers.length()) {
                val c = customers.optJSONObject(i) ?: continue
                val name = c.optString("name", "").takeIf { it != "null" } ?: ""
                val phone = c.optString("phoneNumber", "")
                val schedMs = c.optLong("scheduledWorkDate", 0L)
                val sched = if (schedMs > 0) df.format(Date(schedMs)) else ""
                val total = if (c.isNull("totalAmount")) "" else c.optLong("totalAmount").toString()
                val deposit = if (c.isNull("depositAmount")) "" else c.optLong("depositAmount").toString()
                val balance = if (c.isNull("balanceAmount")) "" else c.optLong("balanceAmount").toString()
                val memo = c.optString("memo", "").takeIf { it != "null" } ?: ""
                sb.append(csv(name)).append(',')
                    .append(csv(phone)).append(',')
                    .append(csv(sched)).append(',')
                    .append(csv(total)).append(',')
                    .append(csv(deposit)).append(',')
                    .append(csv(balance)).append(',')
                    .append(csv(memo)).append("\r\n")
            }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
}
