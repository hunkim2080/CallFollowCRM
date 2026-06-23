package com.detailline.callfollowcrm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.detailline.callfollowcrm.data.local.dao.AiSummaryDao
import com.detailline.callfollowcrm.data.local.dao.CachedMessageDao
import com.detailline.callfollowcrm.data.local.dao.CallRecordDao
import com.detailline.callfollowcrm.data.local.dao.CallSummaryDao
import com.detailline.callfollowcrm.data.local.dao.CategoryDao
import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.ImportantMessageDao
import com.detailline.callfollowcrm.data.local.dao.MessageHistoryDao
import com.detailline.callfollowcrm.data.local.dao.MessageTemplateDao
import com.detailline.callfollowcrm.data.local.dao.PricingItemDao
import com.detailline.callfollowcrm.data.local.dao.RecordingAttachmentDao
import com.detailline.callfollowcrm.data.local.dao.TemplateAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity
import com.detailline.callfollowcrm.data.local.entity.CachedMessageEntity
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.ImportantMessageEntity
import com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.dao.SmsContactCacheDao
import com.detailline.callfollowcrm.data.local.dao.SpamPhoneDao
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.data.local.entity.SmsContactCacheEntity
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity

@Database(
    entities = [
        CustomerEntity::class,
        CallRecordEntity::class,
        MessageTemplateEntity::class,
        MessageHistoryEntity::class,
        RecordingAttachmentEntity::class,
        CallSummaryEntity::class,
        TemplateAttachmentEntity::class,
        ImportantMessageEntity::class,
        CachedMessageEntity::class,
        AiSummaryEntity::class,
        PricingItemEntity::class,
        CategoryEntity::class,
        SpamPhoneEntity::class,
        SmsContactCacheEntity::class,
        com.detailline.callfollowcrm.data.local.entity.SuggestionEventEntity::class,
        com.detailline.callfollowcrm.data.local.entity.ManualCashEntity::class,
        com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity::class,
        com.detailline.callfollowcrm.data.local.entity.JobCrewEntity::class,
        com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity::class,
        com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity::class,
        com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity::class,
        com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity::class,
        com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity::class,
        com.detailline.callfollowcrm.data.local.entity.PrincipleEntity::class
    ],
    version = 34,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun messageTemplateDao(): MessageTemplateDao
    abstract fun messageHistoryDao(): MessageHistoryDao
    abstract fun recordingAttachmentDao(): RecordingAttachmentDao
    abstract fun callSummaryDao(): CallSummaryDao
    abstract fun templateAttachmentDao(): TemplateAttachmentDao
    abstract fun importantMessageDao(): ImportantMessageDao
    abstract fun cachedMessageDao(): CachedMessageDao
    abstract fun aiSummaryDao(): AiSummaryDao
    abstract fun pricingItemDao(): PricingItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun spamPhoneDao(): SpamPhoneDao
    abstract fun smsContactCacheDao(): SmsContactCacheDao
    abstract fun suggestionEventDao(): com.detailline.callfollowcrm.data.local.dao.SuggestionEventDao
    abstract fun manualCashDao(): com.detailline.callfollowcrm.data.local.dao.ManualCashDao
    abstract fun notebookContactDao(): com.detailline.callfollowcrm.data.local.dao.NotebookContactDao
    abstract fun jobCrewDao(): com.detailline.callfollowcrm.data.local.dao.JobCrewDao
    abstract fun recurringMessageDao(): com.detailline.callfollowcrm.data.local.dao.RecurringMessageDao
    abstract fun sitePhotoDao(): com.detailline.callfollowcrm.data.local.dao.SitePhotoDao
    abstract fun intakeEventDao(): com.detailline.callfollowcrm.data.local.dao.IntakeEventDao
    abstract fun teamAssignmentDao(): com.detailline.callfollowcrm.data.local.dao.TeamAssignmentDao
    abstract fun principleDao(): com.detailline.callfollowcrm.data.local.dao.PrincipleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v3 -> v4: customers 테이블에 시공 예약일 컬럼 추가.
         * 사용자 데이터(고객/통화/문자) 보존.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN scheduledWorkDate INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_scheduledWorkDate ON customers(scheduledWorkDate)")
            }
        }

        /**
         * v4 -> v5: recording_attachments 에 phoneNumber 컬럼 추가.
         * 의도: 녹음 import 시 Customer 를 자동 생성하지 않도록 정책 변경하면서,
         *      파일명에서 추출된 번호를 보관해 두면 나중에 같은 번호의 Customer 가
         *      만들어질 때 orphan 첨부를 자동 연결할 수 있다.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_attachments ADD COLUMN phoneNumber TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_attachments_phoneNumber ON recording_attachments(phoneNumber)")
            }
        }

        /**
         * v5 -> v6: customers 테이블에 leadHeat 컬럼 추가.
         * 통화 직후 오버레이 카드에서 사장님이 빠르게 분류하는 "리드 온도".
         * null = 미분류, "COLD" = 단순 문의, "WARM" = 감도 있음.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN leadHeat TEXT")
            }
        }

        /**
         * v6 -> v7: customers 테이블에 입금 컬럼 4개 추가 (계약금/잔금 × 금액/받은시각).
         * 모두 nullable — null = 아직 안 받음/금액 미정.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN depositAmount INTEGER")
                db.execSQL("ALTER TABLE customers ADD COLUMN depositPaidAt INTEGER")
                db.execSQL("ALTER TABLE customers ADD COLUMN balanceAmount INTEGER")
                db.execSQL("ALTER TABLE customers ADD COLUMN balancePaidAt INTEGER")
            }
        }

        /**
         * v7 -> v8: important_messages 테이블 신설.
         * 사장님이 채팅 메시지를 ⭐ 표시해 두는 용도. 시스템 SMS/MMS 와 별개로 우리 DB 에 저장.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS important_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        customerId INTEGER,
                        messageBody TEXT NOT NULL,
                        messageDateMs INTEGER NOT NULL,
                        sent INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_important_messages_phoneNumber ON important_messages(phoneNumber)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_important_messages_phoneNumber_messageDateMs_sent ON important_messages(phoneNumber, messageDateMs, sent)")
            }
        }

        /**
         * v8 -> v9: cached_messages 테이블 신설.
         * 시스템 SMS/MMS 의 로컬 캐시. ChatScreen 진입 시 즉시 표시 + 백그라운드 동기화 용도.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_messages (
                        localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        systemId INTEGER NOT NULL,
                        isMms INTEGER NOT NULL,
                        phoneSuffix TEXT NOT NULL,
                        address TEXT,
                        body TEXT NOT NULL,
                        dateMs INTEGER NOT NULL,
                        sent INTEGER NOT NULL,
                        imageUrisCsv TEXT NOT NULL,
                        cachedAtMs INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_phoneSuffix ON cached_messages(phoneSuffix)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cached_messages_systemId_isMms ON cached_messages(systemId, isMms)")
            }
        }

        /**
         * v9 -> v10: ai_summary_cache 테이블 신설.
         * P0+P1+P2 서버 endpoint 3개 (card-summary, conversation-summary, next-action-suggest) 의 결과 통합 캐시.
         * 키 = phoneSuffix (한국 번호 끝 8자리 unique 가정).
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_summary_cache (
                        phoneSuffix TEXT PRIMARY KEY NOT NULL,
                        cardSummary TEXT,
                        conversationSummaryJson TEXT,
                        conversationStage TEXT,
                        nextActionJson TEXT,
                        latestMessageTimestampMs INTEGER NOT NULL DEFAULT 0,
                        generatedAtMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * v10 -> v11: pricing_items 테이블 신설.
         * 견적서 작성기에서 사장님이 항목 체크 + 합산하는 구조화된 가격표 (pricing.md 와 별개).
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pricing_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        price INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        displayOrder INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * v11 -> v12: categories 테이블 + customers.categoryId 컬럼 신설.
         * CustomerStatus enum 폐기 → 갤메시지 식 사장님 정의 카테고리로 통일.
         * status 컬럼은 보존 (legacy, drop 은 추후). 모든 신규 분류는 categoryId 로.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        emoji TEXT,
                        displayOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)")
                db.execSQL("ALTER TABLE customers ADD COLUMN categoryId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_categoryId ON customers(categoryId)")
            }
        }

        /**
         * v12 -> v13: customers.status 컬럼 drop.
         * SQLite ALTER TABLE DROP COLUMN 은 3.35+ 만 지원 → 테이블 recreate 방식.
         * (1) customers_new 테이블 생성 (status 없음)
         * (2) status 빼고 데이터 복사
         * (3) 기존 인덱스 + 외래키 무관 — DROP / RENAME / 인덱스 재생성
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE customers_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        name TEXT,
                        categoryId INTEGER,
                        memo TEXT NOT NULL DEFAULT '',
                        scheduledWorkDate INTEGER,
                        leadHeat TEXT,
                        depositAmount INTEGER,
                        depositPaidAt INTEGER,
                        balanceAmount INTEGER,
                        balancePaidAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO customers_new (
                        id, phoneNumber, name, categoryId, memo, scheduledWorkDate,
                        leadHeat, depositAmount, depositPaidAt, balanceAmount, balancePaidAt,
                        createdAt, updatedAt
                    )
                    SELECT
                        id, phoneNumber, name, categoryId, memo, scheduledWorkDate,
                        leadHeat, depositAmount, depositPaidAt, balanceAmount, balancePaidAt,
                        createdAt, updatedAt
                    FROM customers
                """.trimIndent())
                db.execSQL("DROP TABLE customers")
                db.execSQL("ALTER TABLE customers_new RENAME TO customers")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_phoneNumber ON customers(phoneNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_scheduledWorkDate ON customers(scheduledWorkDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_categoryId ON customers(categoryId)")
            }
        }

        /**
         * v13 -> v14: spam_phones 테이블 추가. 사장님이 미확인 카드 swipe 로 광고/스팸 영구 마킹.
         *   key = phone suffix (끝 8자리). 사용자 데이터 보존.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS spam_phones (
                        phoneSuffix TEXT NOT NULL PRIMARY KEY,
                        markedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * v14 -> v15: customers 에 address 컬럼 추가. 사장님이 현장 주소를 직접 등록.
         *   AddressExtractor 자동 추출보다 우선 — 사장님 신뢰 데이터.
         *   카드 펼침 [📍 길찾기] 가 이 값을 1순위로 활용.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT")
            }
        }

        /**
         * v15 -> v16: sms_contacts_cache 테이블 추가 (2026-05-28).
         *   HomeScreen 풀스캔 (17000건) 통점 fix 의 토대.
         *   첫 실행 시 풀스캔 → 캐시. 그 후 SmsReceiver 가 phone 별 incremental upsert.
         *   HomeViewModel 은 이 테이블 observe → instant 갱신 (재시작 후에도 빠름).
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_contacts_cache (
                        normalizedSuffix TEXT NOT NULL PRIMARY KEY,
                        address TEXT NOT NULL,
                        lastBody TEXT NOT NULL,
                        lastDateMs INTEGER NOT NULL,
                        lastSent INTEGER NOT NULL,
                        hasOwnerReply INTEGER NOT NULL,
                        firstDateMsInScan INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_sms_contacts_cache_lastDateMs " +
                        "ON sms_contacts_cache(lastDateMs)"
                )
            }
        }

        /**
         * v16 -> v17: suggestion_events 테이블 (킬러콘텐츠 3단계 — 채택/수정 데이터 수집).
         *   사장님 chip 행동 시그널 (SENT_AS_IS / EDITED / REFINED_THEN_SENT / IGNORED / DISMISSED)
         *   을 phone × suggestion 단위로 기록. 4/5/6단계 (Tone RAG / 페르소나 / 학습 루프) 기반.
         *   reportedToServer 플래그로 batch 보고 (cowork 의 POST /api/suggestion-events 예정).
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS suggestion_events (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        phoneSuffix TEXT NOT NULL,
                        scenario TEXT,
                        scenarioConfidence REAL,
                        intentKey TEXT,
                        intentLabel TEXT,
                        suggestionText TEXT,
                        action TEXT NOT NULL,
                        finalSentText TEXT,
                        editDistance INTEGER,
                        createdAtMs INTEGER NOT NULL,
                        reportedToServer INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_suggestion_events_createdAtMs " +
                        "ON suggestion_events(createdAtMs)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_suggestion_events_reportedToServer " +
                        "ON suggestion_events(reportedToServer)"
                )
            }
        }

        /**
         * v17 → v18: 2026-05-30 사장님 #9 통점 — CallStateReceiver + TelephonyCallback 동시 호출로
         *   동일 (phoneNumber, startedAt) 의 call_records 중복 row 가 박힌 것을 정리.
         *   각 그룹의 MIN(id) 만 유지, 나머지 삭제.
         *   startedAt IS NULL row 는 dedup 불가라 그대로 둠 (rare path).
         *   호출 후 정상 카운트 표시. 새 통화는 create() 가 이미 dedup 박혀 중복 방지.
         */
        /**
         * v18 → v19: 2026-05-30 사장님 #4 통점 — customers.totalAmount 컬럼 추가.
         *   입금 카드의 잔금 자동 계산 기준 (balance = total - deposit).
         *   기존 row 는 null 으로 추가 — 사장님이 채워야 자동 계산 동작.
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN totalAmount INTEGER")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM call_records
                    WHERE startedAt IS NOT NULL
                      AND id NOT IN (
                          SELECT MIN(id) FROM call_records
                          WHERE startedAt IS NOT NULL
                          GROUP BY phoneNumber, startedAt
                      )
                    """.trimIndent()
                )
            }
        }

        /**
         * v19 → v20: manual_cash 테이블 신설 (정산 Phase 2 — 직접 현금 기록).
         *   현금흐름 달력에서 settle 파생 수입과 합쳐 보여줄 수동 입/출금.
         *   순수 추가(additive) — 기존 데이터 영향 없음. index 이름은 Entity 의 @Index name 과 일치해야 함.
         */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS manual_cash (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dayStartMs INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        isIncome INTEGER NOT NULL,
                        isDone INTEGER NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_manual_cash_dayStartMs ON manual_cash(dayStartMs)"
                )
            }
        }

        /**
         * v20 → v21: notebook_contacts 테이블 신설 (수첩 — 일당/거래처 통합).
         *   순수 추가(additive). index 이름은 Entity 의 @Index name 과 일치.
         */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notebook_contacts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        tag TEXT NOT NULL DEFAULT '',
                        memo TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_notebook_contacts_kind ON notebook_contacts(kind)"
                )
            }
        }

        /**
         * v21 → v22: pricing_items 에 unit 컬럼 추가 (정액 FLAT / 평당 PYEONG).
         *   기존 항목은 모두 'FLAT'(정액). 순수 추가 — additive.
         */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pricing_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'FLAT'")
            }
        }

        /**
         * v22 → v23: job_crew 테이블 신설 (일당 배정 — 함께한 현장 + 일당 자동차감).
         *   순수 추가(additive). index 이름은 Entity 의 @Index name 과 일치.
         */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS job_crew (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workerId INTEGER NOT NULL,
                        workerName TEXT NOT NULL,
                        customerId INTEGER NOT NULL,
                        dayStartMs INTEGER NOT NULL,
                        wage INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_job_crew_workerId ON job_crew(workerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_job_crew_customerId ON job_crew(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_job_crew_dayStartMs ON job_crew(dayStartMs)")
            }
        }

        /**
         * v23 → v24: customers 에 시공 시간(분) + 시공 기간(일) 컬럼 추가 (일정 영역 완성).
         *   scheduledWorkMinutes = 자정부터 분(null=미정). scheduledWorkDays = 며칠(기본 1).
         *   순수 추가(additive) — 기존 데이터 영향 없음.
         */
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN scheduledWorkMinutes INTEGER")
                db.execSQL("ALTER TABLE customers ADD COLUMN scheduledWorkDays INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * v24 → v25: 정기문자 규칙 + 처리 로그 테이블 신설.
         *   순수 추가(additive). index 이름은 Entity 의 @Index 와 일치.
         */
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetCategoryId INTEGER,
                        intervalDays INTEGER NOT NULL,
                        sendMinutes INTEGER NOT NULL DEFAULT 600,
                        bodyTemplate TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_message_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ruleId INTEGER NOT NULL,
                        customerId INTEGER NOT NULL,
                        occurrenceDayStartMs INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_message_log_ruleId ON recurring_message_log(ruleId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_message_log_ruleId_customerId_occurrenceDayStartMs " +
                        "ON recurring_message_log(ruleId, customerId, occurrenceDayStartMs)"
                )
            }
        }

        /** v25 → v26: 수첩 일당 단가(wage, 원) + 단위(wageType DAILY/HOURLY) 추가. additive. */
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebook_contacts ADD COLUMN wage INTEGER")
                db.execSQL("ALTER TABLE notebook_contacts ADD COLUMN wageType TEXT NOT NULL DEFAULT 'DAILY'")
            }
        }

        // v27 — 현장 사진(로컬). SitePhotoEntity 와 정확히 일치해야 함(Room schema 검증).
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`customerId` INTEGER NOT NULL, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`label` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_site_photos_customerId` " +
                        "ON `site_photos` (`customerId`)"
                )
            }
        }

        // v28 — 시공접수서 제출 이벤트(채팅 타임라인 카드용). IntakeEventEntity 와 정확히 일치해야 함.
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `intake_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneSuffix` TEXT NOT NULL, " +
                        "`token` TEXT NOT NULL, " +
                        "`customerName` TEXT NOT NULL, " +
                        "`submittedAtMs` INTEGER NOT NULL, " +
                        "`address` TEXT, " +
                        "`dateLabel` TEXT, " +
                        "`totalManwon` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intake_events_phoneSuffix` " +
                        "ON `intake_events` (`phoneSuffix`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_intake_events_token` " +
                        "ON `intake_events` (`token`)"
                )
            }
        }

        // v29 — 팀원 현장 배정. TeamAssignmentEntity 와 정확히 일치해야 함.
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `team_assignments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`memberId` TEXT NOT NULL, " +
                        "`memberName` TEXT NOT NULL, " +
                        "`customerId` INTEGER NOT NULL, " +
                        "`dayStartMs` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_team_assignments_customerId` ON `team_assignments` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_team_assignments_memberId` ON `team_assignments` (`memberId`)")
            }
        }

        // v30 — 현장 배정에 '직원 전달 메모' 추가 (고객 메모와 분리, 팀원 화면 표시용).
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `team_assignments` ADD COLUMN `teamMemo` TEXT")
            }
        }

        // v31 — customers 에 시공 완료 시각(workCompletedAt). additive nullable. (2026-06-08 #2)
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN workCompletedAt INTEGER")
            }
        }

        // v32 — "막내가 알아낸 사장님 원칙" 저장 (발견 카드 ⭕ / 직접 추가). (2026-06-17)
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `principles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        // v33 — spam_phones 에 전체 번호(phoneNumber)+이름(displayName) 추가. '스팸 목록' 표시·복구용. additive. (2026-06-23)
        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spam_phones ADD COLUMN phoneNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE spam_phones ADD COLUMN displayName TEXT")
            }
        }

        // v34 — spam_phones 에 kind 추가("spam"/"personal"). 사생활(개인 연락처) 밀어내기 — RING-GO 제외, 목록 따로. (2026-06-23 사장님)
        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spam_phones ADD COLUMN kind TEXT NOT NULL DEFAULT 'spam'")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "call_follow_crm.db"
            )
                .addMigrations(
                    MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                    MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
                    MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
                    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30,
                    MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34
                )
                .fallbackToDestructiveMigration()   // migration 실패 시 안전망 (개발 단계)
                .build()
                .also { instance = it }
        }
    }
}
