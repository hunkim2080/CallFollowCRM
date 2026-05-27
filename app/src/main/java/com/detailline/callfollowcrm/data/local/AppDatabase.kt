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
import com.detailline.callfollowcrm.data.local.dao.SpamPhoneDao
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
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
        SpamPhoneEntity::class
    ],
    version = 14,
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

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "call_follow_crm.db"
            )
                .addMigrations(
                    MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
                )
                .fallbackToDestructiveMigration()   // migration 실패 시 안전망 (개발 단계)
                .build()
                .also { instance = it }
        }
    }
}
