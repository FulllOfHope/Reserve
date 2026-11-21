package com.app.reserve.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
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
    "cash" -> Icons.Rounded.AttachMoney
    "savings" -> Icons.Rounded.Savings
    "investment" -> Icons.Rounded.TrendingUp
    "donation" -> Icons.Rounded.VolunteerActivism
    "coffee" -> Icons.Rounded.Coffee
    "groceries" -> Icons.Rounded.ShoppingCart
    "car" -> Icons.Rounded.DirectionsCar
    "bike" -> Icons.Rounded.TwoWheeler
    "train" -> Icons.Rounded.Train
    "fuel" -> Icons.Rounded.LocalGasStation
    "rent" -> Icons.Rounded.Apartment
    "hotel" -> Icons.Rounded.Bed
    "movies" -> Icons.Rounded.Movie
    "music" -> Icons.Rounded.MusicNote
    "sports" -> Icons.Rounded.SportsSoccer
    "pets" -> Icons.Rounded.Pets
    "kids" -> Icons.Rounded.ChildCare
    "work" -> Icons.Rounded.Work
    "tech" -> Icons.Rounded.Computer
    "phone" -> Icons.Rounded.PhoneAndroid
    "internet" -> Icons.Rounded.Wifi
    "electricity" -> Icons.Rounded.Bolt
    "water" -> Icons.Rounded.WaterDrop
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
    "cash" to "Cash",
    "savings" to "Savings",
    "investment" to "Investment",
    "donation" to "Donation",
    "coffee" to "Coffee",
    "groceries" to "Groceries",
    "car" to "Car",
    "bike" to "Bike",
    "train" to "Train",
    "fuel" to "Fuel",
    "rent" to "Rent",
    "hotel" to "Hotel",
    "movies" to "Movies",
    "music" to "Music",
    "sports" to "Sports",
    "pets" to "Pets",
    "kids" to "Kids",
    "work" to "Work",
    "tech" to "Tech",
    "phone" to "Phone",
    "internet" to "Internet",
    "electricity" to "Electricity",
    "water" to "Water",
    "other" to "Other"
)
