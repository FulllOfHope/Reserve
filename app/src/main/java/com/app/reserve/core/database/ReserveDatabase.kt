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
    version = 2,
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

        fun get(context: Context): ReserveDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReserveDatabase::class.java,
                    "reserve.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(DbSecurity.getPassphrase(context)))
                    .build()
                    .also { instance = it }
            }
    }
}
