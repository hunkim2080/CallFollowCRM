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
        com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity::class,
        com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity::class,
        com.detailline.callfollowcrm.data.local.entity.JobCrewEntity::class,
        com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity::class,
        com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity::class,
        com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity::class,
        com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity::class,
        com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity::class,
        com.detailline.callfollowcrm.data.local.entity.PrincipleEntity::class,
        com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity::class,
        com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity::class,
        com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity::class,
        com.detailline.callfollowcrm.data.local.entity.JobEntity::class
    ],
    version = 55,
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
    abstract fun simpleEventDao(): com.detailline.callfollowcrm.data.local.dao.SimpleEventDao
    abstract fun notebookContactDao(): com.detailline.callfollowcrm.data.local.dao.NotebookContactDao
    abstract fun jobCrewDao(): com.detailline.callfollowcrm.data.local.dao.JobCrewDao
    abstract fun recurringMessageDao(): com.detailline.callfollowcrm.data.local.dao.RecurringMessageDao
    abstract fun sitePhotoDao(): com.detailline.callfollowcrm.data.local.dao.SitePhotoDao
    abstract fun intakeEventDao(): com.detailline.callfollowcrm.data.local.dao.IntakeEventDao
    abstract fun teamAssignmentDao(): com.detailline.callfollowcrm.data.local.dao.TeamAssignmentDao
    abstract fun principleDao(): com.detailline.callfollowcrm.data.local.dao.PrincipleDao
    abstract fun timelineEventDao(): com.detailline.callfollowcrm.data.local.dao.TimelineEventDao
    abstract fun issuedDocDao(): com.detailline.callfollowcrm.data.local.dao.IssuedDocDao
    abstract fun threadBucketDao(): com.detailline.callfollowcrm.data.local.dao.ThreadBucketDao
    abstract fun jobDao(): com.detailline.callfollowcrm.data.local.dao.JobDao

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
                        memo TEXT NOT NULL,
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

        // v35 — intake_events 에 시공내용(itemsText)+확인발송시각(confirmedAt). 접수 확인 문자(사장님 확인버튼)용. additive nullable. (2026-06-28 사장님)
        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intake_events ADD COLUMN itemsText TEXT")
                db.execSQL("ALTER TABLE intake_events ADD COLUMN confirmedAt INTEGER")
            }
        }

        // v36 — timeline_events 신설(일정/금액/잔금 변경 이력 카드). TimelineEventEntity 와 정확히 일치. (2026-06-30 사장님)
        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timeline_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneSuffix` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`oldValue` TEXT, " +
                        "`newValue` TEXT, " +
                        "`reason` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`notifiedAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_timeline_events_phoneSuffix` " +
                        "ON `timeline_events` (`phoneSuffix`)"
                )
            }
        }

        // v37 — pricing_items 에 isEstimated 추가. 업종 스타터가 자동 채운 '추정' 항목 표시용. additive. (2026-07-02 사장님)
        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pricing_items ADD COLUMN isEstimated INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v38 — pricing_items 에 basisText 추가. 문자 기반 가격추출이 뽑은 '가격 기준' 메모(nullable). additive. (2026-07-02 사장님)
        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pricing_items ADD COLUMN basisText TEXT")
            }
        }

        // v39 — issued_docs 신설(발행한 견적서/시공접수서 스냅샷 → 발행 이력). IssuedDocEntity 와 정확히 일치. (2026-07-07 사장님)
        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `issued_docs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneSuffix` TEXT NOT NULL, " +
                        "`customerId` INTEGER, " +
                        "`kind` TEXT NOT NULL, " +
                        "`recipient` TEXT, " +
                        "`totalWon` INTEGER NOT NULL, " +
                        "`workDateMs` INTEGER, " +
                        "`itemsText` TEXT, " +
                        "`memo` TEXT, " +
                        "`docJson` TEXT NOT NULL, " +
                        "`url` TEXT, " +
                        "`token` TEXT, " +
                        "`issuedAtMs` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_docs_phoneSuffix` ON `issued_docs` (`phoneSuffix`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_docs_customerId` ON `issued_docs` (`customerId`)")
            }
        }

        // v40 — thread_buckets 신설(상담함/문자함 분류). ThreadBucketEntity 와 정확히 일치. index 없음(PK만). (2026-07-11 사장님)
        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `thread_buckets` (" +
                        "`suffix` TEXT PRIMARY KEY NOT NULL, " +
                        "`bucket` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`reason` TEXT, " +
                        "`decidedAt` INTEGER NOT NULL, " +
                        "`classifiedBodyHash` INTEGER)"
                )
            }
        }

        // v41 — cached_messages 에 동영상 첨부 URI 컬럼 추가(동영상 MMS 수신 지원). (2026-07-13 사장님)
        private val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cached_messages` ADD COLUMN `videoUrisCsv` TEXT NOT NULL DEFAULT ''")
            }
        }

        // v42 — 시공 "건(job)" 이력 테이블 신설(재방문/추가 시공 대비). 순수 추가(additive) — 기존 데이터 영향 없음.
        //   컬럼은 CustomerEntity 시공 필드와 1:1. index 이름은 JobEntity @Index name 과 정확히 일치해야 함
        //   (불일치 시 첫 query 에서 "Migration didn't properly handle" crash). (2026-07-20 사장님 — 재방문 케이스)
        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS jobs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerId INTEGER NOT NULL,
                        scheduledWorkDate INTEGER,
                        scheduledWorkMinutes INTEGER,
                        scheduledWorkDays INTEGER NOT NULL,
                        address TEXT,
                        totalAmount INTEGER,
                        depositAmount INTEGER,
                        depositPaidAt INTEGER,
                        balanceAmount INTEGER,
                        balancePaidAt INTEGER,
                        workCompletedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_customerId ON jobs(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_scheduledWorkDate ON jobs(scheduledWorkDate)")
            }
        }

        // v42 → v43: customers 에 A/S 예약(asScheduledDate) + A/S 기간(asScheduledDays) 컬럼 추가.
        //   시공 예약과 별개로 A/S 를 따로 잡음(무료). 순수 추가(additive) — 기존 데이터 영향 없음. (2026-08-01 사장님)
        private val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN asScheduledDate INTEGER")
                db.execSQL("ALTER TABLE customers ADD COLUMN asScheduledDays INTEGER NOT NULL DEFAULT 1")
            }
        }

        // 2026-08-13 시공막내 웹 뷰어 — 로컬 현장사진을 서버로 백필 업로드하며 완료 표시(중복 방지).
        private val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE site_photos ADD COLUMN serverUploadedAt INTEGER")
            }
        }
        // 통화 전문 '카톡 말풍선'용 화자분리 세그먼트(JSON) — 서버 transcript_segments 저장. (2026-08-14)
        private val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_summaries ADD COLUMN transcriptSegmentsJson TEXT")
            }
        }
        // 통화 키워드 태그(JSON) — 서버 tags[] 를 통화카드 해시태그로. (2026-08-17)
        private val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_summaries ADD COLUMN tagsJson TEXT")
            }
        }
        // v47 — customers 에 구글 캘린더 이벤트 id 2개(시공/AS). 본폰 미러링 대체 = 구글 캘린더 동기화.
        //   additive nullable — 기존 데이터 영향 없음. (2026-08-31 사장님)
        private val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN workCalendarEventId TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN asCalendarEventId TEXT")
            }
        }
        // v48 — intake_events 에 고객 접수 메모(현관 비번·요청사항). 접수 카드에 표시. additive nullable. (2026-09-02 사장님)
        private val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intake_events ADD COLUMN customerMemo TEXT")
            }
        }

        // v49 — 재방문 Phase2 Stage A: "한 고객 여러 일정"(인테리어 업체 케이스. 2026-09-11 사장님).
        //   각 고객의 '현재 예정 시공'을 jobs 로 복사해 jobs 를 일정 SoT 로 승격.
        //   ⚠️ COPY 만 — customers 의 시공 컬럼은 그대로 둠(= 대표 건 미러). 기존 화면 무변경·무손실.
        //   이미 같은 고객·같은 날 건(Phase1 에서 보관된 완료건 등)이 있으면 건너뜀(중복 방지).
        private val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                        WHERE j.customerId = c.id AND j.scheduledWorkDate = c.scheduledWorkDate
                      )
                    """.trimIndent()
                )
            }
        }

        // v50 — 재방문 Phase2 **Stage B 1단계**: 건마다 메모·사진이 따로 붙게 (2026-09-14 사장님).
        //   "인테리어 사장이 1차·2차·3차 계속 일을 준다. 그럼 잔금도 다르고 메모도 3개가 생겨야 한다."
        //   ⚠️ 여기선 **칸만 만든다.** 화면/계산은 그대로 → 이 마이그레이션만으로는 아무것도 안 바뀐다(위험 0).
        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ① 건별 메모
                db.execSQL("ALTER TABLE jobs ADD COLUMN memo TEXT NOT NULL DEFAULT ''")
                // 지금까지 쓰던 고객 메모는 '대표 건'(= customers 미러와 같은 날짜) 것으로 옮겨 심는다.
                //   customers.memo 는 **지우지 않는다** — 아직 그걸 읽는 화면들이 있다.
                db.execSQL(
                    """
                    UPDATE jobs SET memo = COALESCE((
                        SELECT c.memo FROM customers c
                        WHERE c.id = jobs.customerId
                          AND c.memo IS NOT NULL AND c.memo <> ''
                          AND c.scheduledWorkDate = jobs.scheduledWorkDate
                    ), '')
                    """.trimIndent()
                )

                // ② 현장 사진을 '건'에 붙임 (NULL = 아직 어느 건인지 모름)
                db.execSQL("ALTER TABLE site_photos ADD COLUMN jobId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_site_photos_jobId ON site_photos(jobId)")
                // 기존 사진은 '찍힌 시각 기준으로 그때 진행 중이던(또는 직전) 건'에 붙인다.
                //   그런 게 없으면(사진이 첫 시공보다 이른 경우) 그 고객의 가장 이른 건으로.
                db.execSQL(
                    """
                    UPDATE site_photos SET jobId = COALESCE(
                      (SELECT j.id FROM jobs j
                        WHERE j.customerId = site_photos.customerId
                          AND j.scheduledWorkDate IS NOT NULL
                          AND j.scheduledWorkDate <= site_photos.createdAt
                        ORDER BY j.scheduledWorkDate DESC LIMIT 1),
                      (SELECT j.id FROM jobs j
                        WHERE j.customerId = site_photos.customerId
                          AND j.scheduledWorkDate IS NOT NULL
                        ORDER BY j.scheduledWorkDate ASC LIMIT 1)
                    )
                    """.trimIndent()
                )
            }
        }

        // v51 — 간단 일정 (2026-09-16 사장님).
        //   "폰번호 없이도 일정에 메모처럼 간단하게 등록하고 싶을 수도 있잖아."
        //   고객 표를 건드리지 않는다 — **새 표 하나만 추가**. 기존 데이터는 손도 안 댄다(위험 0).
        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS simple_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        dayStartMs INTEGER NOT NULL,
                        minutes INTEGER,
                        memo TEXT NOT NULL DEFAULT '',
                        calendarEventId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // ⚠️ @Entity 와 **한 글자라도 다르면** 앱이 아예 안 켜진다(파괴적 마이그레이션을 꺼둬서 크래시).
                //    · 인덱스 이름·컬럼이 @Entity(indices=) 와 같아야 한다
                //    · SQL 에 DEFAULT 를 쓰지 않는다 — 엔티티(@ColumnInfo(defaultValue=))에 없는 기본값을
                //      DB 에만 넣으면 스키마가 어긋날 수 있다. memo 는 코드에서 늘 채워 넣는다.
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_simple_events_dayStartMs ON simple_events(dayStartMs)")
            }
        }

        // v52 — 달력에서 사라진 시공 되살리기 (2026-09-17 실기 발견).
        //   증상: 상담함엔 "다음 시공 모레(9/19)" 가 뜨는데 일정 탭·달력엔 그 날이 비어 있었다.
        //   원인: v49 부터 일정 SoT 가 jobs 인데, **접수서(고객이 직접 작성)로 잡힌 시공일**은
        //         customers 에만 쓰고 jobs 에 안 넣었다. 그 구멍은 2026-09-15(0aeef7f6)에 막았지만,
        //         그 전에 들어온 건들은 이미 jobs 없이 남아 있다 — 마이그레이션은 과거를 안 고쳐준다.
        //   왜 급한가: 달력만 비는 게 아니다. **D-1 안내와 잔금 알림도 jobs 를 돌기 때문에**
        //         (ReminderWorker, Stage B) 모레 시공인데 하루 전 문자가 안 나간다.
        //   무엇을 하나: v49 와 **같은 SQL 을 한 번 더** 돌린다. 이미 있는 건은 NOT EXISTS 로 건너뛰므로
        //         중복이 안 생기고, 지우는 것도 없다(위험 0).
        //   ⚠️ 2026-09-17 사고: memo 를 안 채워 앱이 아예 안 켜졌다(NOT NULL constraint failed: jobs.memo).
        //      새로 까는 폰은 jobs.memo 에 DEFAULT 가 없다(ALTER 로 붙인 폰에만 있었다).
        //      → NOT NULL 칸은 **전부 직접 채운다.** 그리고 되살리기는 '있으면 좋은' 일이니
        //      앱이 켜지는 것보다 중요하지 않다 — 실패해도 앱은 켜지게 감싼다.
        private val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL(
                    """
                    INSERT INTO jobs (customerId, scheduledWorkDate, scheduledWorkMinutes, scheduledWorkDays,
                                      address, totalAmount, depositAmount, depositPaidAt,
                                      balanceAmount, balancePaidAt, workCompletedAt, memo, createdAt, updatedAt)
                    SELECT c.id, c.scheduledWorkDate, c.scheduledWorkMinutes, c.scheduledWorkDays,
                           c.address, c.totalAmount, c.depositAmount, c.depositPaidAt,
                           c.balanceAmount, c.balancePaidAt, c.workCompletedAt,
                           COALESCE(c.memo, ''),
                           strftime('%s','now') * 1000, strftime('%s','now') * 1000
                    FROM customers c
                    WHERE c.scheduledWorkDate IS NOT NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM jobs j
                        WHERE j.customerId = c.id
                          AND j.scheduledWorkDate IS NOT NULL
                          -- '같은 밀리초'로 비교하면 접수서처럼 시각까지 든 값이 새 줄로 또 들어간다.
                          -- 사람이 보는 단위는 '그 날' 이므로 날짜로 맞춘다.
                          AND date(j.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                              = date(c.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                      )
                    """.trimIndent()
                ) }
            }
        }

        // v53 — 이미 어긋나 있는 '건'의 돈을 고객 카드와 맞춘다. (2026-09-17 사장님:
        //   "잔금 다 받았다고 눌렀는데 돈 못 받았다고 알람이 오네")
        //   원인: Stage B 에서 알람만 jobs 로 옮기고 **돈은 customers 에만 남겨뒀다.**
        //   앞으로는 CustomerRepository.mutate 가 바뀔 때마다 건에 찍는다(같은 날 수정).
        //   여기선 **이미 벌어진 어긋남**만 한 번 맞춘다.
        //   대상: 고객 카드가 가리키는 그 건(= 같은 날짜) 하나. 지난(아카이브) 건은 안 건드린다.
        private val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching {
                db.execSQL(
                    """
                    UPDATE jobs SET
                      totalAmount    = (SELECT c.totalAmount    FROM customers c WHERE c.id = jobs.customerId),
                      depositAmount  = (SELECT c.depositAmount  FROM customers c WHERE c.id = jobs.customerId),
                      depositPaidAt  = (SELECT c.depositPaidAt  FROM customers c WHERE c.id = jobs.customerId),
                      balanceAmount  = (SELECT c.balanceAmount  FROM customers c WHERE c.id = jobs.customerId),
                      balancePaidAt  = (SELECT c.balancePaidAt  FROM customers c WHERE c.id = jobs.customerId),
                      updatedAt      = strftime('%s','now') * 1000
                    WHERE jobs.scheduledWorkDate IS NOT NULL
                      AND EXISTS (
                        SELECT 1 FROM customers c
                        WHERE c.id = jobs.customerId
                          AND c.scheduledWorkDate IS NOT NULL
                          AND date(c.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                              = date(jobs.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                      )
                    """.trimIndent()
                )
                }
            }
        }

        // v54 — 완료 표시를 건 전표에도. (2026-09-18 연결부 점검)
        //   완료 처리는 지금까지 **고객 카드에만** 찍혔다(홈 히어로 [완료] → customers.workCompletedAt).
        //   그래서 건 탭의 '완료' 표시가 틀릴 수 있었다. 앞으로는 둘 다 찍고(HomeViewModel),
        //   여기선 **이미 완료해둔 옛 건**을 한 번 맞춘다.
        //   넣기만 하고 지우지 않는다. 이미 찍힌 건은 건드리지 않는다(사장님이 건별로 고쳤을 수 있다).
        private val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching {
                db.execSQL(
                    """
                    UPDATE jobs SET
                      workCompletedAt = (SELECT c.workCompletedAt FROM customers c WHERE c.id = jobs.customerId),
                      updatedAt = strftime('%s','now') * 1000
                    WHERE jobs.workCompletedAt IS NULL
                      AND jobs.scheduledWorkDate IS NOT NULL
                      AND EXISTS (
                        SELECT 1 FROM customers c
                        WHERE c.id = jobs.customerId
                          AND c.workCompletedAt IS NOT NULL
                          AND c.scheduledWorkDate IS NOT NULL
                          AND date(c.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                              = date(jobs.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                      )
                    """.trimIndent()
                )
                }
            }
        }

        // v55 — 구글 캘린더 일정 번호를 **건마다**. (2026-09-18 연결부 점검)
        //   전엔 고객 표에 칸이 하나라, 2차를 잡으면 1차 일정이 2차 날짜로 **옮겨졌다**.
        //   ⚠️ 칸을 더할 땐 @Entity 와 정확히 같아야 한다(TEXT nullable, DEFAULT 안 씀).
        //     NOT NULL 칸을 실수로 만들면 앱이 아예 안 켜진다(2026-09-17 실제 사고).
        private val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN calendarEventId TEXT")
                // 이미 올려둔 일정을 **대표 건에 물려준다** — 안 그러면 다음 동기화에서
                //   같은 일정이 하나 더 생긴다(중복). 같은 날짜 건에만.
                runCatching {
                    db.execSQL(
                        """
                        UPDATE jobs SET
                          calendarEventId = (SELECT c.workCalendarEventId FROM customers c WHERE c.id = jobs.customerId),
                          updatedAt = strftime('%s','now') * 1000
                        WHERE jobs.calendarEventId IS NULL
                          AND jobs.scheduledWorkDate IS NOT NULL
                          AND EXISTS (
                            SELECT 1 FROM customers c
                            WHERE c.id = jobs.customerId
                              AND c.workCalendarEventId IS NOT NULL
                              AND c.scheduledWorkDate IS NOT NULL
                              AND date(c.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                                  = date(jobs.scheduledWorkDate / 1000, 'unixepoch', 'localtime')
                          )
                        """.trimIndent()
                    )
                }
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
                    MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34,
                    MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38,
                    MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42,
                    MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46,
                    MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50,
                    MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53, MIGRATION_53_54,
                    MIGRATION_54_55
                )
                // 2026-07-19 데이터 전멸 지뢰 제거 (프로덕션 감사 by Fable 5).
                //   기존 .fallbackToDestructiveMigration() 은 "어떤 migration 이든 실패하면 DB 전체를 조용히 삭제"였다.
                //   → 앞으로 41→42 등에서 migration SQL 하나만 틀려도 고객·정산 데이터가 소리 없이 증발.
                //   실전 앱에선 '삭제'보다 '크래시'가 백배 낫다(크래시는 보고받고 고쳐 재배포 가능, 삭제는 복구 불가).
                //   단, MIGRATION_1_2/2_3 이 애초에 없어(v1·v2 경로 부재) 통째로 떼면 그 옛 사용자가 크래시 →
                //   v1·v2 에서 올라올 때만 예외로 삭제 허용, 그 외(v3~ 및 미래 실수)는 IllegalStateException 으로 멈춤.
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
                .also { instance = it }
        }
    }
}
