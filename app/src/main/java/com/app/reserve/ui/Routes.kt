package com.app.reserve.ui

object Routes {
    const val Home = "home"
    const val Transactions = "transactions"
    const val Insights = "insights"
    const val Settings = "settings"

    const val AddPattern = "add/{type}?id={id}"
    const val Budgets = "budgets"
    const val Categories = "categories"
    const val Accounts = "accounts"
    const val Recurring = "recurring"
    const val Trash = "trash"

    fun add(type: String, id: String? = null): String =
        "add/$type" + if (id.isNullOrBlank()) "" else "?id=$id"

    fun addFromRoute(route: String): String = route
}
