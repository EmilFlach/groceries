package com.emilflach.groceries.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
    val toBuyItems = items.filter { it.checked_at == null }
    val checkedItems = items.filter { it.checked_at != null }

    // Prefer the catalog feed; fall back to the list items' own photos when there's no snapshot.
    val collageUrls = remember(collageImageUrls, items) {
        if (collageImageUrls.isNotEmpty()) collageImageUrls
        else items.mapNotNull { it.image_url }.distinct().shuffled()
    }

    val listState = rememberLazyListState()

    // Adding an item drops it at the top of the "to buy" list — jump back up so it's visible.
    // A growing total means a net add (checks/unchecks/removes never grow the count).
    var previousCount by remember { mutableIntStateOf(items.size) }
    LaunchedEffect(items.size) {
        if (items.size > previousCount) listState.animateScrollToItem(0)
        previousCount = items.size
    }

    // Checking the top-most visible item makes it jump to the "in the cart" section; LazyColumn
    // would follow it down (it anchors on the first visible key). Instead, hold the viewport in
    // place so the next item simply slides up into the freed slot.
    var restoreAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(items) {
        restoreAnchor?.let { (index, offset) ->
            listState.scrollToItem(index, offset)
            restoreAnchor = null
        }
    }
    val onToggleChecked: (ShoppingListItem, Boolean) -> Unit = { item, checked ->
        if (checked && listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key == item.id) {
            restoreAnchor = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        viewModel.setChecked(item.id, checked)
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
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header scrolls away with the list rather than collapsing in place.
                item(key = "header") {
                    if (collageUrls.isNotEmpty()) {
                        CollageHeader(
                            imageUrls = collageUrls,
                            toBuy = toBuyItems.size,
                            total = items.size,
                            onOpenSetup = onOpenSetup,
                        )
                    } else {
                        FlatHeader(toBuy = toBuyItems.size, total = items.size, onOpenSetup = onOpenSetup)
                    }
                }

                if (!hasSnapshot) {
                    item(key = "import-banner") { ImportBanner(onClick = onOpenSetup) }
                }

                if (items.isEmpty()) {
                    item(key = "empty-state") { EmptyState(hasSnapshot = hasSnapshot) }
                } else {
                    if (toBuyItems.isNotEmpty()) {
                        item(key = "to-buy-section-header") {
                            SectionHeader(
                                title = "To buy · ${toBuyItems.size}",
                                actionLabel = "Check all",
                                actionIcon = Icons.Outlined.DoneAll,
                                onAction = viewModel::checkAll,
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                            )
                        }
                        items(toBuyItems, key = { it.id }) { item: ShoppingListItem ->
                            ShoppingListItemRow(
                                item = item,
                                onCheckedChange = { checked -> onToggleChecked(item, checked) },
                                onRemove = { viewModel.remove(item.id) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                            )
                        }
                    }

                    if (checkedItems.isNotEmpty()) {
                        item(key = "checked-section-header") {
                            SectionHeader(
                                title = "In the cart · ${checkedItems.size}",
                                actionLabel = "Uncheck all",
                                actionIcon = Icons.AutoMirrored.Outlined.Undo,
                                onAction = viewModel::uncheckAll,
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                            )
                        }
                        items(checkedItems, key = { it.id }) { item: ShoppingListItem ->
                            ShoppingListItemRow(
                                item = item,
                                onCheckedChange = { checked -> onToggleChecked(item, checked) },
                                onRemove = { viewModel.remove(item.id) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp),
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

/** Divider row above a section (to-buy / in-the-cart) with a one-tap bulk action for that section. */
@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(
                actionIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
}

/** A bento mosaic of food photos — a few large rounded tiles filling the space, thin colorful seams
 *  showing the gradient behind, with the list count over a soft bottom scrim. */
@Composable
private fun CollageHeader(imageUrls: List<String>, toBuy: Int, total: Int, onOpenSetup: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .height(300.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
    ) {
        BentoCollage(imageUrls = imageUrls, modifier = Modifier.fillMaxSize())

        // Soft scrim so the white count text stays legible over the photos below it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                ),
        )

        Text(
            text = countLabel(toBuy, total),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )

        Surface(
            onClick = onOpenSetup,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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

/**
 * Asymmetric bento grid: a tall feature tile on the left, a stacked pair in the middle, and a full-
 * height tile on the right. Varied proportions read playful; the tight even gaps keep it elegant.
 */
@Composable
private fun BentoCollage(imageUrls: List<String>, modifier: Modifier = Modifier) {
    val gap = 6.dp
    val shape = RoundedCornerShape(18.dp)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(modifier = Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            BentoTile(imageUrls.getOrNull(0), Modifier.weight(1.7f).fillMaxWidth(), shape)
            BentoTile(imageUrls.getOrNull(1), Modifier.weight(1f).fillMaxWidth(), shape)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            BentoTile(imageUrls.getOrNull(2), Modifier.weight(1f).fillMaxWidth(), shape)
            BentoTile(imageUrls.getOrNull(3), Modifier.weight(1.4f).fillMaxWidth(), shape)
        }
        BentoTile(imageUrls.getOrNull(4), Modifier.weight(0.9f).fillMaxHeight(), shape)
    }
}

@Composable
private fun BentoTile(url: String?, modifier: Modifier, shape: Shape) {
    FoodImage(url = url, name = "", modifier = modifier, shape = shape)
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

@Composable
private fun ImportBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
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
