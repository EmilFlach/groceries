package com.emilflach.groceries.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextDecoration
import com.emilflach.groceries.ShoppingListItem

@Composable
fun ShoppingListItemRow(
    item: ShoppingListItem,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val checked = item.checked_at != null
    ListItem(
        headlineContent = {
            Text(
                item.name,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            )
        },
        leadingContent = {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        },
    )
}
