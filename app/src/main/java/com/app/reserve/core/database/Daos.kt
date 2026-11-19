package com.app.reserve.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, createdAt DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reparentCategory(oldCategoryId: String, newCategoryId: String?)

    /**
     * Second-line duplicate guard for the same SMS arriving with two slightly
     * different fingerprints (for example a multipart body reassembled twice).
     */
    @Query(
        """SELECT * FROM transactions
           WHERE accountId = :accountId AND amount = :amount AND type = :type
             AND note = :note AND timestamp BETWEEN :from AND :to
           ORDER BY createdAt ASC, id ASC LIMIT 1"""
    )
    suspend fun findDuplicateSmsTransaction(
        accountId: String,
        amount: Long,
        type: TransactionType,
        note: String,
        from: Long,
        to: Long
    ): TransactionEntity?

    /** SMS-sourced rows used by the one-time v3 ledger repair. */
    @Query("SELECT * FROM transactions WHERE note LIKE 'SMS ·%' ORDER BY createdAt ASC, id ASC")
    suspend fun smsTransactionsForRepair(): List<TransactionEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isArchived ASC, createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET balance = :balance WHERE id = :id")
    suspend fun setBalance(id: String, balance: Long)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface SmsImportDao {
    /**
     * IGNORE makes dedupe atomic: a duplicate fingerprint returns -1 and the
     * caller never touches balances or transactions.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SmsImportEntity): Long

    @Query("DELETE FROM sms_imports WHERE fingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)

    @Query("SELECT * FROM sms_imports WHERE fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): SmsImportEntity?

    /** Newest SMS date already posted to an account; used to reject replays. */
    @Query("SELECT MAX(messageDate) FROM sms_imports WHERE accountId = :accountId")
    suspend fun latestMessageDate(accountId: String): Long?

    /** Latest import that carried a bank balance, for repair reconciliation. */
    @Query(
        """SELECT * FROM sms_imports
           WHERE accountId = :accountId AND balanceAfterMinor IS NOT NULL
           ORDER BY messageDate DESC, importedAt DESC LIMIT 1"""
    )
    suspend fun latestWithBalance(accountId: String): SmsImportEntity?

    @Query("SELECT COUNT(*) FROM sms_imports")
    suspend fun count(): Int

    @Query("DELETE FROM sms_imports")
    suspend fun clear()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY monthYear DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY monthYear DESC")
    suspend fun getAll(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: String)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY nextDueAt ASC")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY nextDueAt ASC")
    suspend fun getAll(): List<RecurringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RecurringEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE recurring_rules SET nextDueAt = :nextDueAt WHERE id = :id")
    suspend fun updateNextDue(id: String, nextDueAt: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun clear()
}
