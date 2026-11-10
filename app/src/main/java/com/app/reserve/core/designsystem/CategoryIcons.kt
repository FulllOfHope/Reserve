package com.app.reserve.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps the stored icon name to an outlined Material icon. */
fun categoryIcon(name: String): ImageVector = when (name) {
    "food" -> Icons.Rounded.Restaurant
    "transport" -> Icons.Rounded.DirectionsBus
    "shopping" -> Icons.Rounded.ShoppingBag
    "bills" -> Icons.Rounded.ReceiptLong
    "health" -> Icons.Rounded.MonitorHeart
    "fun" -> Icons.Rounded.SportsEsports
    "salary" -> Icons.Rounded.Payments
    "gift" -> Icons.Rounded.CardGiftcard
    "wallet" -> Icons.Rounded.AccountBalanceWallet
    "home" -> Icons.Rounded.Home
    "education" -> Icons.Rounded.School
    "travel" -> Icons.Rounded.Flight
    "subscriptions" -> Icons.Rounded.Subscriptions
    else -> Icons.Rounded.Category
}

val CategoryIconChoices = listOf(
    "food" to "Food",
    "transport" to "Transport",
    "shopping" to "Shopping",
    "bills" to "Bills",
    "health" to "Health",
    "fun" to "Fun",
    "salary" to "Salary",
    "gift" to "Gift",
    "wallet" to "Wallet",
    "education" to "Education",
    "travel" to "Travel",
    "subscriptions" to "Subscription",
    "other" to "Other"
)
