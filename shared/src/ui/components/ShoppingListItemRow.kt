package com.emilflach.groceries.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.ShoppingListItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingListItemRow(
    item: ShoppingListItem,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onAssignLabel: (() -> Unit)? = null,
    onEditNote: (() -> Unit)? = null,
) {
    val checked = item.checked_at != null
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (checked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (checked) 0.dp else 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Tap toggles bought/not; the rarely-used aisle assignment sits behind a long-press so it
            // needn't take up a permanent button next to the frequent note and delete actions.
            modifier = Modifier
                .combinedClickable(
                    onClick = { onCheckedChange(!checked) },
                    onLongClick = onAssignLabel,
                    onLongClickLabel = "Assign shop aisle",
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                FoodImage(
                    url = item.image_url,
                    name = item.name,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    dimmed = checked,
                )
                if (checked) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Checked off",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                // The grams/amount or any free-text note, shown just under the name when present.
                item.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onEditNote != null) {
                val hasNote = item.note != null
                IconButton(onClick = onEditNote, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (hasNote) Icons.Filled.EditNote else Icons.Outlined.EditNote,
                        contentDescription = if (hasNote) "Edit note on ${item.name}" else "Add note to ${item.name}",
                        tint = if (hasNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
