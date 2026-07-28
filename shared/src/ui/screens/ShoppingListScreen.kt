package com.emilflach.groceries.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.ShoppingListItem
import com.emilflach.groceries.ui.components.FoodImage
import com.emilflach.groceries.ui.components.ShoppingListItemRow
import com.emilflach.groceries.viewmodel.ShoppingListViewModel

@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel,
    hasSnapshot: Boolean,
    collageImageUrls: List<String>,
    onOpenSetup: () -> Unit,
    onAddItem: () -> Unit,
) {
    val items by viewModel.items.collectAsState()
    val toBuy = items.count { it.checked_at == null }

    // Photos for the top collage. Prefer the catalog feed (meals first, then foods — already
    // shuffled and prioritized upstream); when there's no snapshot to draw from, fall back to
    // the list's own item photos so the collage still has something to show.
    val collageUrls = remember(collageImageUrls, items) {
        if (collageImageUrls.isNotEmpty()) collageImageUrls
        else items.mapNotNull { it.image_url }.distinct().shuffled()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItem,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add food", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ),
                    ),
                )
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (collageUrls.isNotEmpty()) {
                    CollageHeader(
                        imageUrls = collageUrls,
                        toBuy = toBuy,
                        total = items.size,
                        onOpenSetup = onOpenSetup,
                    )
                } else {
                    FlatHeader(toBuy = toBuy, total = items.size, onOpenSetup = onOpenSetup)
                }

                if (!hasSnapshot) {
                    ImportBanner(onClick = onOpenSetup)
                }

                if (items.isEmpty()) {
                    EmptyState(hasSnapshot = hasSnapshot)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { item: ShoppingListItem ->
                            ShoppingListItemRow(
                                item = item,
                                onCheckedChange = { checked -> viewModel.setChecked(item.id, checked) },
                                onRemove = { viewModel.remove(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun countLabel(toBuy: Int, total: Int): String = when {
    total == 0 -> "Nothing on the list yet"
    toBuy == 0 -> "All $total done — nice"
    else -> "$toBuy to buy" + (if (total - toBuy > 0) " · ${total - toBuy} in the cart" else "")
}

/**
 * The top of the screen: a random mosaic of food photos with the list count read out over a
 * bottom scrim, and the Lokcal-setup action tucked into the corner. Replaces the old title bar
 * so the app reads flat — no second header stacked under the window chrome.
 */
@Composable
private fun CollageHeader(imageUrls: List<String>, toBuy: Int, total: Int, onOpenSetup: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
            .height(200.dp)
            .clip(MaterialTheme.shapes.large),
    ) {
        FoodCollage(imageUrls = imageUrls, modifier = Modifier.fillMaxSize())

        // Scrim so the count stays legible over whatever photos land at the bottom edge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                ),
        )

        Text(
            text = countLabel(toBuy, total),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )

        Surface(
            onClick = onOpenSetup,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 1.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "Lokcal setup",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fallback header when there are no photos yet — just the count and the setup action. */
@Composable
private fun FlatHeader(toBuy: Int, total: Int, onOpenSetup: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = countLabel(toBuy, total),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onOpenSetup,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = "Lokcal setup",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A mosaic of up to six food photos laid out across columns of varying tile counts, so the
 * band reads as a collage rather than a plain grid. Photos are pre-shuffled by the caller.
 */
@Composable
private fun FoodCollage(imageUrls: List<String>, modifier: Modifier = Modifier) {
    val columns = remember(imageUrls) {
        val tilesPerColumn = listOf(2, 1, 2, 1)
        val cols = mutableListOf<List<String>>()
        var i = 0
        for (count in tilesPerColumn) {
            if (i >= imageUrls.size) break
            val end = minOf(i + count, imageUrls.size)
            cols += imageUrls.subList(i, end)
            i = end
        }
        cols
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        columns.forEach { colUrls ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                colUrls.forEach { url ->
                    FoodImage(
                        url = url,
                        name = "",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RectangleShape,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.ShoppingBag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Import your Lokcal data to pick items from your food catalog — tap to set it up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyState(hasSnapshot: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.size(20.dp))
            Text(
                text = "Your basket is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (hasSnapshot) "Tap “Add food” to start your list."
                else "Import your Lokcal foods, then tap “Add food”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
