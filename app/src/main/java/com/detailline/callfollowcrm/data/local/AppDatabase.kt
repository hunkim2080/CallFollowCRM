package com.detailline.callfollowcrm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.detailline.callfollowcrm.data.local.dao.CallRecordDao
import com.detailline.callfollowcrm.data.local.dao.CallSummaryDao
import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.ImportantMessageDao
import com.detailline.callfollowcrm.data.local.dao.MessageHistoryDao
import com.detailline.callfollowcrm.data.local.dao.MessageTemplateDao
import com.detailline.callfollowcrm.data.local.dao.RecordingAttachmentDao
import com.detailline.callfollowcrm.data.local.dao.TemplateAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.ImportantMessageEntity
import com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
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
        ImportantMessageEntity::class
    ],
    version = 8,
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

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "call_follow_crm.db"
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()   // migration 실패 시 안전망 (개발 단계)
                .build()
                .also { instance = it }
        }
    }
}
