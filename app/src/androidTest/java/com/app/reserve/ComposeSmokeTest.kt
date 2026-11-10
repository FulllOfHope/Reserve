package com.app.reserve

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.app.reserve.core.database.AccountEntity
import com.app.reserve.core.database.CategoryEntity
import com.app.reserve.core.database.TransactionEntity
import com.app.reserve.core.database.TransactionType
import com.app.reserve.core.designsystem.ReserveTheme
import com.app.reserve.core.designsystem.TransactionRow
import com.app.reserve.core.domain.Dates
import com.app.reserve.feature.ledger.LedgerState
import com.app.reserve.feature.transactions.TransactionsScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * Lightweight Compose smoke checks. These render real screens with fixed data
 * and confirm the important text appears, without asserting pixel layout.
 */
class ComposeSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val category = CategoryEntity("food", "Food", "#5C947F", "food", TransactionType.EXPENSE)

    private val transaction = TransactionEntity(
        id = "t1",
        amount = 45_050,
        type = TransactionType.EXPENSE,
        categoryId = "food",
        accountId = "cash",
        timestamp = Dates.toTimestamp(LocalDate.of(2026, 11, 3)),
        note = "Lunch"
    )

    @Test
    fun transactionRowShowsNoteAndAmount() {
        composeRule.setContent {
            ReserveTheme(darkTheme = false) {
                TransactionRow(
                    transaction = transaction,
                    category = category,
                    accountName = "Cash",
                    onClick = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        composeRule.onNodeWithText("Lunch").assertIsDisplayed()
        composeRule.onNodeWithText("-₹450.50").assertIsDisplayed()
    }

    @Test
    fun transactionsScreenShowsTitleAndSearch() {
        val state = LedgerState(
            loading = false,
            monthYear = "2026-11",
            transactions = listOf(transaction),
            categories = listOf(category),
            accounts = listOf(AccountEntity("cash", "Cash", 0, createdAt = 1L))
        )
        composeRule.setContent {
            ReserveTheme(darkTheme = false) {
                TransactionsScreen(
                    state = state,
                    onEditTransaction = {},
                    onDeleteTransaction = {},
                    onPreviousMonth = {},
                    onNextMonth = {}
                )
            }
        }
        composeRule.onNodeWithText("Transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Search notes, categories, accounts").assertIsDisplayed()
    }
}
