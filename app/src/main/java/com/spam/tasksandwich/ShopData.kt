package com.spam.tasksandwich

data class ShopItem(
    val id: String = "",
    val name: String = "",
    val cost: Int = 0,
    val mysteryText: String = "",
    val autoRedeem: Boolean = false
)

/**
 * Represents a single purchase record from the 'purchaseLog' subcollection.
 * Created when a user buys a ShopItem.
 */
data class PurchaseLogItem(
    val id: String = "",
    val itemName: String = "",
    val itemCost: Int = 0,
    val purchasedByUserId: String = "",
    val purchasedByUserName: String = "",
    val status: String = "pending",
    val mysteryText: String = ""
)