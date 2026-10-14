package com.qingxi.ledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qingxi.ledger.data.category.CategoryDao
import com.qingxi.ledger.data.category.CategorySeeder
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.recurring.RecurringDismissalDao
import com.qingxi.ledger.data.recurring.RecurringDismissalEntity
import com.qingxi.ledger.data.recurring.RecurringRuleDao
import com.qingxi.ledger.data.recurring.RecurringRuleEntity
import com.qingxi.ledger.data.tx.TransactionDao
import com.qingxi.ledger.data.tx.TransactionEntity

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        RecurringDismissalEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun recurringDismissalDao(): RecurringDismissalDao

    companion object {
        private const val DB_NAME = "qingji.db"

        /**
         * v1 → v2：周期记账。只建表、加两个可空列和索引，不改动任何已有行——
         * 这是工程里第一条迁移，DDL 必须与 schemas/2.json 逐字对齐，否则 Room 开库时校验失败直接崩。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`tags` TEXT NOT NULL, " +
                        "`repeatType` INTEGER NOT NULL, " +
                        "`weekdays` TEXT NOT NULL, " +
                        "`dayOfMonth` INTEGER NOT NULL, " +
                        "`startMillis` INTEGER NOT NULL, " +
                        "`endType` INTEGER NOT NULL, " +
                        "`endMillis` INTEGER NOT NULL, " +
                        "`totalCount` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdMillis` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` " +
                        "ON `recurring_rules` (`categoryId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_dismissals` (" +
                        "`ruleId` INTEGER NOT NULL, " +
                        "`dueDayKey` TEXT NOT NULL, " +
                        "`dismissedMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ruleId`, `dueDayKey`))"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `ruleId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `dueDayKey` TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_ruleId_dueDayKey` " +
                        "ON `transactions` (`ruleId`, `dueDayKey`)"
                )
            }
        }

        lateinit var instance: AppDatabase
            private set

        fun init(context: Context): AppDatabase {
            if (!::instance.isInitialized) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addCallback(CategorySeeder.callback())
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
            }
            return instance
        }
    }
}
