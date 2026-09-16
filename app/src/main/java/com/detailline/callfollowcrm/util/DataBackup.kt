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
    data class CategoryRestoreResult(val categories: Int, val tagged: Int)

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
            put("prefs", dumpPrefs(context))   // 설정칸(협업 연결 등) 포함. (2026-08-24 사장님)
            put("files", dumpFiles(context, db, FILES_TOTAL_EXPORT))  // 문구·현장 첨부 사진 **파일 자체**.
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

    /**
     * 서버 백업용 — 전 테이블 덤프 JSON(raw, zip 아님) 바이트. (사진은 filePath=텍스트라 작음.)
     * 복원은 importBytes 가 raw json 도 읽음. (데이터 안전 2단계, 2026-08-21 사장님)
     */
    fun serverBlobBytes(context: Context): ByteArray {
        val db = AppDatabase.getInstance(context.applicationContext).openHelper.writableDatabase
        val tablesObj = JSONObject()
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
                }
            }
            tablesObj.put(table, arr)
        }
        fun build(withFiles: Boolean): ByteArray {
            val root = JSONObject().apply {
                put("format", FORMAT); put("dbVersion", db.version); put("app", "시공막내")
                put("exportedAt", System.currentTimeMillis()); put("tables", tablesObj)
                put("prefs", dumpPrefs(context))   // 설정칸(협업 연결·스팸·자동문자·업체정보) 포함.
                if (withFiles) put("files", dumpFiles(context, db, FILES_TOTAL_SERVER))
            }
            return root.toString().toByteArray(Charsets.UTF_8)
        }
        // 사진까지 담아보고, 메모리가 모자라면 **사진 없이** 다시 만든다.
        //   백업이 아예 안 되는 것보다 사진만 빠지는 게 낫다. (2026-09-15 사장님 크래시)
        return try {
            build(true)
        } catch (e: OutOfMemoryError) {
            android.util.Log.w("DataBackup", "사진 포함 백업이 메모리 부족 → 사진 빼고 재시도", e)
            System.gc()
            build(false)
        }
    }

    // ─────────────────────────── 가져오기(복원) ───────────────────────────

    /** IO 스레드에서 호출. uri = 사용자가 고른 백업 파일(zip 또는 json). */
    fun import(context: Context, uri: Uri): ImportResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw EmptyBackupException()
        return importBytes(context, bytes)
    }

    /** 서버 복원용 — 바이트(zip 또는 raw json) 직접 가져오기. import(uri) 와 같은 안전 upsert(안 지움). */
    fun importBytes(context: Context, bytes: ByteArray): ImportResult {
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
        // 설정칸(협업 연결·스팸목록·자동문자·업체정보·설정 토글) 복원 — DB 밖(SharedPreferences)이라 트랜잭션 후.
        //   고객은 원래 id 그대로 복원되므로 collab_assignments 의 customerId 참조가 그대로 유효. (2026-08-24 사장님)
        restorePrefs(context, root.optJSONObject("prefs"))
        // 첨부 사진 복원 — 파일을 풀고 DB 주소를 새 위치로 갱신(주소만 되돌리면 죽은 주소라 안 보임).
        runCatching { restoreFiles(context, db, root.optJSONObject("files")) }
        // 일정 장부(jobs) 메우기 — **옛 백업에는 jobs 표가 아예 없다.**
        //   그대로 두면 고객은 돌아왔는데 일정 탭·달력이 텅 비고, D-1 안내·잔금 알림도 안 나간다
        //   (둘 다 jobs 를 돈다). 마이그레이션은 복원에는 안 돌아가므로 여기서 한 번 더. (2026-09-17)
        runCatching { backfillJobsFromCustomers(db) }
        return ImportResult(totalRows, tableCount, customerCount)
    }

    /**
     * 카테고리·태그만 복원 (2026-09-01 사장님 — 일당 등 카테고리가 사라졌는데 다른 데이터는 안 되돌리고 싶을 때).
     *   1) categories 테이블 복원(INSERT OR REPLACE) — 지워졌거나 id 가 뒤바뀐 카테고리 되살림.
     *   2) customers.categoryId 복원 — 현재 '미분류(null)'인 고객만 백업값으로(현재 태그는 안 건드림).
     *   나머지 테이블/컬럼(금액·메모·일정 등)은 절대 안 건드림 → 전체복원의 '되돌림' 부작용 없음.
     */
    fun importCategoriesOnly(context: Context, bytes: ByteArray): CategoryRestoreResult {
        val jsonText = extractBackupJson(bytes) ?: throw EmptyBackupException()
        val root = JSONObject(jsonText)
        if (root.optInt("format", 1) > FORMAT) throw NewerBackupException()
        val tables = root.optJSONObject("tables") ?: throw EmptyBackupException()

        val db = AppDatabase.getInstance(context.applicationContext).openHelper.writableDatabase
        var catCount = 0
        var restored = 0
        db.beginTransaction()
        try {
            // 1) 카테고리는 **이름**으로 맞춘다. id 로 덮어쓰면 절대 안 된다.
            //    🔴 2026-09-14 사고: 예전 구현은 INSERT OR REPLACE 로 백업의 id 를 그대로 밀어넣었다.
            //       백업 id=3 "일당" ↔ 현재 id=3 "인테리어 업체" 처럼 번호가 어긋나 있으면
            //       그 번호를 쓰던 카테고리가 통째로 갈아치워져서, 인테리어 업체 고객이 전부
            //       "일당" 으로 보이고 원래 "일당" 행은 지워져 그 고객들은 미분류가 됐다.
            //    → 이제: 이름이 이미 있으면 **그대로 둔다**(건드리지 않음). 없는 이름만 새로 만든다.
            //       그리고 백업 id → 현재 id 로 번역표를 만들어 고객 태그를 붙인다.
            val catCols = tableColumns(db, "categories")
            val catArr = tables.optJSONArray("categories")
            val nameToLocalId = HashMap<String, Long>()
            db.query("SELECT `id`, `name` FROM `categories`").use { c ->
                while (c.moveToNext()) nameToLocalId[c.getString(1).trim()] = c.getLong(0)
            }
            val backupIdToLocalId = HashMap<Long, Long>()
            if (catCols.isNotEmpty() && catArr != null) {
                for (r in 0 until catArr.length()) {
                    val row = catArr.optJSONObject(r) ?: continue
                    val name = row.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val backupId = row.optLong("id", -1L)
                    val existing = nameToLocalId[name]
                    if (existing != null) {
                        if (backupId >= 0L) backupIdToLocalId[backupId] = existing
                        continue // 이미 있는 카테고리는 이름·이모지·순서 어느 것도 안 덮는다.
                    }
                    // 없는 이름만 새로 만든다 — id 는 DB 가 새로 매긴다(백업 id 재사용 금지).
                    val cols = ArrayList<String>()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k in catCols && k != "id") cols.add(k)
                    }
                    if (cols.isEmpty()) continue
                    val placeholders = cols.joinToString(",") { "?" }
                    val colList = cols.joinToString(",") { "`$it`" }
                    val args = arrayOfNulls<Any?>(cols.size)
                    for (i in cols.indices) args[i] = bindValue(row.get(cols[i]))
                    db.execSQL("INSERT OR IGNORE INTO `categories` ($colList) VALUES ($placeholders)", args)
                    val newId = db.query("SELECT `id` FROM `categories` WHERE `name`=?", arrayOf<Any?>(name))
                        .use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
                    if (newId >= 0L) {
                        nameToLocalId[name] = newId
                        if (backupId >= 0L) backupIdToLocalId[backupId] = newId
                        catCount++
                    }
                }
            }
            // 2) customers.categoryId — 현재 미분류(null)인 고객만. 번역표를 거쳐야 엉뚱한 태그가 안 붙는다.
            val before = countTaggedCustomers(db)
            val custCols = tableColumns(db, "customers")
            val custArr = tables.optJSONArray("customers")
            if ("categoryId" in custCols && "id" in custCols && custArr != null) {
                for (r in 0 until custArr.length()) {
                    val row = custArr.optJSONObject(r) ?: continue
                    if (row.isNull("categoryId")) continue
                    val id = row.optLong("id", -1L)
                    if (id < 0L) continue
                    val localCat = backupIdToLocalId[row.optLong("categoryId")] ?: continue
                    db.execSQL(
                        "UPDATE `customers` SET `categoryId`=? WHERE `id`=? AND `categoryId` IS NULL",
                        arrayOf<Any?>(localCat, id)
                    )
                }
            }
            restored = (countTaggedCustomers(db) - before).coerceAtLeast(0)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return CategoryRestoreResult(catCount, restored)
    }

    private fun countTaggedCustomers(db: androidx.sqlite.db.SupportSQLiteDatabase): Int =
        runCatching {
            db.query("SELECT COUNT(*) FROM customers WHERE categoryId IS NOT NULL").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }.getOrDefault(0)

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
    /**
     * customers 에만 있고 jobs 에 없는 시공일을 jobs 로 옮겨 심는다. (2026-09-17)
     *
     * 왜 필요한가: v49 부터 일정 탭·달력·D-1 안내·잔금 알림의 근거는 **jobs** 다.
     *   옛 백업(= v49 이전)에는 jobs 표가 없어서, 복원하면 고객은 다 돌아오는데
     *   달력만 텅 빈 채로 남는다. 실제로 그 상태의 폰을 봤다.
     *
     * 안전: 넣기만 하고 아무것도 안 지운다. 같은 고객·같은 **날**의 건이 이미 있으면 건너뛴다
     *   (밀리초로 비교하면 시각이 든 값이 새 줄로 또 들어간다).
     */
    private fun backfillJobsFromCustomers(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        if (tableColumns(db, "jobs").isEmpty()) return
        db.execSQL(
            """
            INSERT INTO jobs (customerId, scheduledWorkDate, scheduledWorkMinutes, scheduledWorkDays,
                              address, totalAmount, depositAmount, depositPaidAt,
                              balanceAmount, balancePaidAt, workCompletedAt, createdAt, updatedAt)
            SELECT c.id, c.scheduledWorkDate, c.scheduledWorkMinutes, c.scheduledWorkDays,
                   c.address, c.totalAmount, c.depositAmount, c.depositPaidAt,
                   c.balanceAmount, c.balancePaidAt, c.workCompletedAt,
                   strftime('%s','now') * 1000, strftime('%s','now') * 1000
            FROM customers c
            WHERE c.scheduledWorkDate IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM jobs j
                WHERE j.customerId = c.id
                  AND j.scheduledWorkDate IS NOT NULL
                  AND date(j.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                      = date(c.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
              )
            """.trimIndent()
        )
    }

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

    // ─────────────────────────── 설정칸(SharedPreferences) 백업/복원 ───────────────────────────
    // 협업 연결(collab_assignments)·스팸목록·자동문자 문구·업체정보·설정 토글은 DB 가 아니라 이 설정칸에 산다.
    //   재설치 시 함께 지워지는데 예전 백업은 DB 만 담아 복원해도 안 살아났음 → 여기에 포함. (2026-08-24 사장님)
    //   ⚠️ 기기/인증/권한 종속 키(token·fcm·폴더 URI)는 제외 — 새 기기서 재발급·재선택 대상.
    private const val APP_PREFS = "call_follow_crm"

    private fun skipPrefKey(k: String): Boolean {
        val lk = k.lowercase(Locale.ROOT)
        // 🔴 '이 폰에서만 뜻이 있는' 설정은 되돌리면 안 된다. (2026-09-14 사장님)
        //   구글 캘린더 연결은 **폰+구글계정+앱서명**에 묶인 허가라 백업으로 못 옮긴다.
        //   그런데 "연결됨" 표시와 캘린더 id 는 그냥 설정칸 값이라 복원돼 버렸고,
        //   → 앱은 "연결됨"이라고 하는데 실제 인증은 없어서 [동기화]가 계속 실패했다.
        //   (사장님 재설치 직후 겪음. 표시가 거짓말을 하면 원인을 못 찾는다)
        if (lk.startsWith("google_calendar") || lk.contains("calendar_connected")) return true
        return lk.contains("token") || lk.contains("fcm") || lk.contains("gcm") || lk.contains("folder")
    }

    /** 설정칸을 타입 보존해 JSON 으로. (복원 때 같은 타입으로 되돌림) */
    // ─────────────────── 첨부 사진 백업/복원 (2026-09-14 사장님) ───────────────────
    //  🔴 그동안 백업엔 사진의 **주소만** 들어갔다. 앱을 지우면
    //     · content://…fileprovider/…  → 앱 내부 폴더가 통째로 삭제되고
    //     · content://com.android.providers/… → "이 앱이 그 사진 봐도 된다"는 허가가 사라져서
    //     복원해도 사진이 안 열렸다("문구 사진 다시 설정해야 하네").
    //  → 이제 **파일 자체**를 백업에 담고, 복원 때 앱 내부에 풀어서 주소를 새로 연결한다.

    // 🔴 2026-09-15 사장님 신고 "서버에 백업하기 누르면 막 꺼져" → OutOfMemoryError 150MB.
    //   사진을 base64 글자로 바꿔 JSON 에 넣으면 원본의 1.4배, 거기에 JSON 전체를 한 문자열로
    //   만드는 순간 또 그만큼이 더 필요하다. 60MB 상한이면 폰이 못 버틴다.
    //   → 상한을 확 낮추고, 그래도 모자라면 사진 없이 다시 만든다(백업 자체는 반드시 성공하게).
    /** 첨부 하나당 상한. */
    private const val FILE_MAX_BYTES = 2 * 1024 * 1024
    /** 파일로 내보낼 때(zip) 첨부 총량. */
    private const val FILES_TOTAL_EXPORT = 20 * 1024 * 1024
    /** 서버 백업은 통째로 한 문자열이 되므로 더 빡빡하게. */
    private const val FILES_TOTAL_SERVER = 8 * 1024 * 1024
    private const val TPL_DIR = "template_photos"
    private const val SITE_DIR = "site_photos"

    /** DB 가 가리키는 첨부 파일들을 읽어 base64 로. key = DB 에 저장된 원래 주소/경로. */
    private fun dumpFiles(
        context: Context, db: androidx.sqlite.db.SupportSQLiteDatabase, totalMax: Int
    ): JSONObject {
        val out = JSONObject()
        var total = 0
        fun add(key: String, bytes: ByteArray, mime: String, name: String) {
            if (bytes.isEmpty() || bytes.size > FILE_MAX_BYTES) return
            if (total + bytes.size > totalMax) return
            out.put(key, JSONObject()
                .put("b64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .put("mime", mime).put("name", name))
            total += bytes.size
        }
        // 문구 첨부 — 최신 것부터(총량 상한에 걸리면 옛 것이 잘리게)
        runCatching {
            db.query("SELECT `fileUri`, `displayName`, `mimeType` FROM `template_attachments` ORDER BY `id` DESC").use { c ->
                while (c.moveToNext()) {
                    val uri = c.getString(0) ?: continue
                    if (out.has(uri)) continue
                    val bytes = readUriBytes(context, uri) ?: continue
                    add(uri, bytes, c.getString(2) ?: "image/jpeg", c.getString(1) ?: "photo.jpg")
                }
            }
        }
        // 현장 사진 — 앱 내부 절대경로
        runCatching {
            db.query("SELECT `filePath` FROM `site_photos` ORDER BY `id` DESC").use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0) ?: continue
                    if (out.has(p)) continue
                    val f = File(p)
                    if (!f.exists() || f.length() > FILE_MAX_BYTES) continue
                    add(p, runCatching { f.readBytes() }.getOrNull() ?: continue, "image/jpeg", f.name)
                }
            }
        }
        return out
    }

    /** content:// 든 절대경로든 읽어서 바이트로. 못 읽으면 null(이미 죽은 주소). */
    private fun readUriBytes(context: Context, uriStr: String): ByteArray? = runCatching {
        if (!uriStr.startsWith("content://")) {
            val f = File(uriStr)
            return@runCatching if (f.exists() && f.length() <= FILE_MAX_BYTES) f.readBytes() else null
        }
        // 우리 앱 내부 파일이면 ContentResolver 안 거치고 바로 읽는다(FileProvider 권한 무관).
        TemplatePhotoStore.fileFor(context, uriStr)?.let { return@runCatching it.readBytes() }
        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
    }.getOrNull()

    /**
     * 백업의 files 를 앱 내부에 풀고, DB 의 주소를 **새 위치로 갱신**한다.
     *   주소만 되돌리면 그 주소가 이미 죽었으므로 반드시 갱신까지 해야 사진이 보인다.
     * @return 되살린 사진 장수
     */
    private fun restoreFiles(
        context: Context, db: androidx.sqlite.db.SupportSQLiteDatabase, files: JSONObject?
    ): Int {
        if (files == null || files.length() == 0) return 0
        var n = 0
        val tplDir = File(context.filesDir, TPL_DIR).apply { mkdirs() }
        val siteDir = File(context.filesDir, SITE_DIR).apply { mkdirs() }
        val keys = files.keys()
        while (keys.hasNext()) {
            val oldKey = keys.next()
            val o = files.optJSONObject(oldKey) ?: continue
            val bytes = runCatching { Base64.decode(o.optString("b64"), Base64.NO_WRAP) }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            val isSite = !oldKey.startsWith("content://") && oldKey.contains("/$SITE_DIR/")
            val dir = if (isSite) siteDir else tplDir
            val name = (o.optString("name").takeIf { it.isNotBlank() } ?: oldKey.substringAfterLast('/'))
                .substringBefore('?').replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "photo_${System.currentTimeMillis()}.jpg" }
            val f = File(dir, name)
            val wrote = runCatching { f.writeBytes(bytes); true }.getOrDefault(false)
            if (!wrote) continue
            val newRef = if (isSite) f.absolutePath else runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f).toString()
            }.getOrNull() ?: continue
            runCatching {
                if (isSite) db.execSQL("UPDATE `site_photos` SET `filePath`=? WHERE `filePath`=?",
                    arrayOf<Any?>(newRef, oldKey))
                else db.execSQL("UPDATE `template_attachments` SET `fileUri`=? WHERE `fileUri`=?",
                    arrayOf<Any?>(newRef, oldKey))
            }
            n++
        }
        return n
    }

    private fun dumpPrefs(context: Context): JSONObject {
        val out = JSONObject()
        try {
            val sp = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            for ((k, v) in sp.all) {
                if (v == null || skipPrefKey(k)) continue
                val e = JSONObject()
                when (v) {
                    is Boolean -> { e.put("t", "b"); e.put("v", v) }
                    is Int -> { e.put("t", "i"); e.put("v", v) }
                    is Long -> { e.put("t", "l"); e.put("v", v) }
                    is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                    is String -> { e.put("t", "s"); e.put("v", v) }
                    is Set<*> -> {
                        val a = JSONArray(); for (x in v) if (x is String) a.put(x)
                        e.put("t", "ss"); e.put("v", a)
                    }
                    else -> continue
                }
                out.put(k, e)
            }
        } catch (_: Exception) {}
        return out
    }

    /** JSON 설정칸을 SharedPreferences 로 되돌림(덮어쓰기). 없으면(옛 백업) 조용히 스킵. */
    private fun restorePrefs(context: Context, prefs: JSONObject?) {
        if (prefs == null || prefs.length() == 0) return
        try {
            val ed = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit()
            val keys = prefs.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (skipPrefKey(k)) continue
                val e = prefs.optJSONObject(k) ?: continue
                when (e.optString("t")) {
                    "b" -> ed.putBoolean(k, e.optBoolean("v"))
                    "i" -> ed.putInt(k, e.optInt("v"))
                    "l" -> ed.putLong(k, e.optLong("v"))
                    "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
                    "s" -> ed.putString(k, e.optString("v"))
                    "ss" -> {
                        val a = e.optJSONArray("v") ?: JSONArray()
                        val set = HashSet<String>()
                        for (i in 0 until a.length()) set.add(a.optString(i))
                        ed.putStringSet(k, set)
                    }
                }
            }
            ed.apply()
        } catch (_: Exception) {}
    }
}
