package com.emilflach.groceries.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalFood
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.ui.components.FoodImage
import com.emilflach.groceries.ui.util.PlatformBackHandler
import com.emilflach.groceries.viewmodel.SuggestionGroupUi
import com.emilflach.groceries.viewmodel.SuggestionUi
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The full-screen "Add" hub opened from the shopping-list FAB. With a blank search box it shows
 * grouped, one-tap suggestions (weekly regulars today; more sources later); as soon as you type it
 * becomes the food search — so everything to add lives in one place and search is always a keystroke
 * away for anything the suggestions don't cover. Long-pressing any card opens a small context menu
 * to mark/unmark it as a weekly regular.
 */
@Composable
fun AddHubScreen(
    catalogReader: LokcalCatalogReader,
    groups: List<SuggestionGroupUi>,
    regularKeys: Set<String>,
    onDismiss: () -> Unit,
    onFoodSelected: (LokcalFood) -> Unit,
    onAddCustom: (String) -> Unit,
    onToggleSuggestion: (Suggestion) -> Unit,
    onAddAll: (SuggestionGroupUi) -> Unit,
    onToggleRegular: (Suggestion) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LokcalFood>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val trimmedQuery = query.trim()
    val searching = trimmedQuery.isNotEmpty()

    PlatformBackHandler { onDismiss() }

    // Open focused so the keyboard is up and you can type straight away; suggestions sit above it.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Only hit the catalog once the user is actually searching — blank query shows suggestions.
    LaunchedEffect(query) {
        if (!searching) return@LaunchedEffect
        delay(250.milliseconds)
        results = catalogReader.searchFoods(trimmedQuery)
        loaded = true
    }

    LaunchedEffect(results) {
        if (results.isNotEmpty()) gridState.scrollToItem(0)
    }

    LaunchedEffect(loaded) {
        if (loaded && !showItems) {
            delay(10.milliseconds)
            showItems = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Add to list",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your Lokcal foods") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )

            if (searching) {
                SearchResults(
                    query = trimmedQuery,
                    results = results,
                    loaded = loaded,
                    showItems = showItems,
                    gridState = gridState,
                    regularKeys = regularKeys,
                    onAddCustom = {
                        onAddCustom(trimmedQuery)
                        onDismiss()
                    },
                    onFoodSelected = { food ->
                        onFoodSelected(food)
                        onDismiss()
                    },
                    onToggleRegular = { onToggleRegular(it.toSuggestion()) },
                )
            } else {
                Suggestions(
                    groups = groups,
                    regularKeys = regularKeys,
                    onToggle = onToggleSuggestion,
                    onAddAll = onAddAll,
                    onToggleRegular = onToggleRegular,
                )
            }
        }
    }
}

/** Long-press context menu anchored to a card: a single toggle for weekly-regular status. Appearing
 *  right at the card (rather than a bottom sheet) avoids the jarring keyboard-then-sheet swap when a
 *  search result is long-pressed with the keyboard up. */
@Composable
private fun RegularContextMenu(
    expanded: Boolean,
    isRegular: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isRegular) "Remove from weekly regulars" else "Mark as weekly regular") },
            leadingIcon = {
                Icon(
                    if (isRegular) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = onToggle,
        )
    }
}

@Composable
private fun ColumnScope.Suggestions(
    groups: List<SuggestionGroupUi>,
    regularKeys: Set<String>,
    onToggle: (Suggestion) -> Unit,
    onAddAll: (SuggestionGroupUi) -> Unit,
    onToggleRegular: (Suggestion) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No suggestions yet — search above to add food, or long-press an item to mark it as a regular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.sourceId}") {
                SuggestionSectionHeader(group = group, onAddAll = { onAddAll(group) })
            }
            // Rows of three so a plain LazyColumn can host the grid without nested vertical scroll.
            group.items.chunked(3).forEachIndexed { index, row ->
                item(key = "${group.sourceId}-row-$index") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        for (cell in row) {
                            SuggestionCard(
                                item = cell,
                                isRegular = cell.suggestion.key in regularKeys,
                                onClick = { onToggle(cell.suggestion) },
                                onToggleRegular = { onToggleRegular(cell.suggestion) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionSectionHeader(group: SuggestionGroupUi, onAddAll: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (group.supportsBulkAdd && !group.allAdded) {
            TextButton(onClick = onAddAll) {
                Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add all")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionCard(
    item: SuggestionUi,
    isRegular: Boolean,
    onClick: () -> Unit,
    onToggleRegular: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestion = item.suggestion
    val keyboard = LocalSoftwareKeyboardController.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    keyboard?.hide()
                    menuExpanded = true
                },
            ),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                FoodImage(
                    url = suggestion.imageUrl,
                    name = suggestion.name,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    dimmed = item.added,
                )
                if (item.added) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Added",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            if (suggestion.reason != null) {
                Text(
                    text = suggestion.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        RegularContextMenu(
            expanded = menuExpanded,
            isRegular = isRegular,
            onDismiss = { menuExpanded = false },
            onToggle = {
                menuExpanded = false
                onToggleRegular()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.SearchResults(
    query: String,
    results: List<LokcalFood>,
    loaded: Boolean,
    showItems: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    regularKeys: Set<String>,
    onAddCustom: () -> Unit,
    onFoodSelected: (LokcalFood) -> Unit,
    onToggleRegular: (LokcalFood) -> Unit,
) {
    // Anything the catalog doesn't have can still go on the list as a free-typed item.
    AddCustomRow(name = query, onClick = onAddCustom)

    if (!loaded) {
        // First load in flight — don't flash a "no foods" message.
    } else if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No foods match \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(results, key = { _, food -> food.id }) { index, food ->
                AnimatedVisibility(visible = showItems, enter = intakeItemEnterTransition(index)) {
                    FoodPickCard(
                        food = food,
                        isRegular = normalizeKey(food.name) in regularKeys,
                        onClick = { onFoodSelected(food) },
                        onToggleRegular = { onToggleRegular(food) },
                    )
                }
            }
        }
    }
}

/** Tap target for adding the typed text as a custom item when it isn't in the Lokcal catalog. */
@Composable
private fun AddCustomRow(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Add \"$name\" as a custom item",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Staggered fade + slide-up per result, delayed by index so the grid cascades in. */
private fun intakeItemEnterTransition(index: Int): EnterTransition =
    fadeIn(animationSpec = tween(150, delayMillis = index * 15)) +
        slideInVertically(animationSpec = tween(150, delayMillis = index * 15)) { it / 2 }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodPickCard(
    food: LokcalFood,
    isRegular: Boolean,
    onClick: () -> Unit,
    onToggleRegular: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    keyboard?.hide()
                    menuExpanded = true
                },
            ),
        ) {
            FoodImage(
                url = food.imageUrl,
                name = food.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(20.dp),
            )
            Text(
                text = food.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        RegularContextMenu(
            expanded = menuExpanded,
            isRegular = isRegular,
            onDismiss = { menuExpanded = false },
            onToggle = {
                menuExpanded = false
                onToggleRegular()
            },
        )
    }
}

private fun LokcalFood.toSuggestion(): Suggestion =
    Suggestion(
        key = normalizeKey(name),
        name = name,
        imageUrl = imageUrl,
        lokcalFoodId = id,
        reason = null,
    )
