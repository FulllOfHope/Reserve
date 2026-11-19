package com.app.reserve.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        SmsImportEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ReserveDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun smsImportDao(): SmsImportDao

    companion object {
        @Volatile
        private var instance: ReserveDatabase? = null

        /** v2 adds SMS import fingerprints; existing ledger data is preserved. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_imports` (" +
                        "`fingerprint` TEXT NOT NULL, " +
                        "`transactionId` TEXT NOT NULL, " +
                        "`importedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`fingerprint`))"
                )
            }
        }

        /** v3 stores balance/account context so SMS rows can be reconciled safely. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `openingBalanceMinor` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sms_imports` ADD COLUMN `messageDate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sms_imports` ADD COLUMN `balanceAfterMinor` INTEGER")
                db.execSQL("ALTER TABLE `sms_imports` ADD COLUMN `accountId` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): ReserveDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReserveDatabase::class.java,
                    "reserve.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(DbSecurity.getPassphrase(context)))
                    .build()
                    .also { instance = it }
            }
    }
}
