package com.example.shoppinglist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    object AllItems : Screen("all_items", Icons.Filled.List, "All")
    object PendingItems : Screen("pending_items", Icons.Filled.ShoppingCart, "Pending")
    object CheckedItems : Screen("checked_items", Icons.Filled.CheckCircle, "Checked")
}
