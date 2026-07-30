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
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.emilflach.groceries.recommendations.RegularMealSource
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.ui.components.FoodImage
import com.emilflach.groceries.ui.util.PlatformBackHandler
import com.emilflach.groceries.viewmodel.SuggestionGroupUi
import com.emilflach.groceries.viewmodel.SuggestionUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    aisleNames: Map<String, String>,
    onDismiss: () -> Unit,
    onFoodSelected: (LokcalFood) -> Unit,
    onAddCustom: (String) -> Unit,
    onToggleSuggestion: (Suggestion) -> Unit,
    onAddAll: (SuggestionGroupUi) -> Unit,
    onToggleRegular: (Suggestion) -> Unit,
    onDismissMeal: (SuggestionGroupUi) -> Unit,
    onRestoreMeal: (SuggestionGroupUi) -> Unit,
    onDismissSuggestion: (Suggestion) -> Unit,
    onRestoreSuggestion: (Suggestion) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LokcalFood>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val trimmedQuery = query.trim()
    val searching = trimmedQuery.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Dismiss now, but offer a brief undo — dismissing is the only way to hide a suggestion, so a
    // mis-tap shouldn't be irreversible (there's no separate "dismissed" screen to restore from).
    fun dismissWithUndo(label: String, onUndo: () -> Unit) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Dismissed $label",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    val onMealDismissed: (SuggestionGroupUi) -> Unit = { group ->
        onDismissMeal(group)
        dismissWithUndo(group.title) { onRestoreMeal(group) }
    }
    val onSuggestionDismissed: (Suggestion) -> Unit = { suggestion ->
        onDismissSuggestion(suggestion)
        dismissWithUndo(suggestion.name) { onRestoreSuggestion(suggestion) }
    }

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
      Box(modifier = Modifier.fillMaxSize()) {
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
                    aisleNames = aisleNames,
                    onToggle = onToggleSuggestion,
                    onAddAll = onAddAll,
                    onToggleRegular = onToggleRegular,
                    onDismissMeal = onMealDismissed,
                    onDismissSuggestion = onSuggestionDismissed,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )
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
    onNotInterested: (() -> Unit)? = null,
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
        // Only offered where dismissing makes sense (auto suggestions, not the user's own regulars).
        if (onNotInterested != null) {
            DropdownMenuItem(
                text = { Text("Not interested") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onNotInterested,
            )
        }
    }
}

@Composable
private fun ColumnScope.Suggestions(
    groups: List<SuggestionGroupUi>,
    regularKeys: Set<String>,
    aisleNames: Map<String, String>,
    onToggle: (Suggestion) -> Unit,
    onAddAll: (SuggestionGroupUi) -> Unit,
    onToggleRegular: (Suggestion) -> Unit,
    onDismissMeal: (SuggestionGroupUi) -> Unit,
    onDismissSuggestion: (Suggestion) -> Unit,
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
    // Each category shows just its first rows so a long grid (or several sources at once) can't push
    // everything else off-screen; the rest expand on demand, tracked per source id.
    val expandedSources = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.sourceId}") {
                SuggestionSectionHeader(
                    group = group,
                    onAddAll = { onAddAll(group) },
                    // Whole-meal dismiss lives in the header; other groups dismiss per-card instead.
                    onDismiss = if (group.sourceId.startsWith(RegularMealSource.ID)) {
                        { onDismissMeal(group) }
                    } else {
                        null
                    },
                )
            }
            // Rows of three so a plain LazyColumn can host the grid without nested vertical scroll.
            val rows = group.items.chunked(3)
            val expanded = expandedSources[group.sourceId] == true
            val visibleRows = if (expanded) rows else rows.take(COLLAPSED_SUGGESTION_ROWS)
            visibleRows.forEachIndexed { index, row ->
                item(key = "${group.sourceId}-row-$index") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        for (cell in row) {
                            val cellIsRegular = cell.suggestion.key in regularKeys
                            SuggestionCard(
                                item = cell,
                                isRegular = cellIsRegular,
                                aisleName = aisleNames[cell.suggestion.key],
                                onClick = { onToggle(cell.suggestion) },
                                onToggleRegular = { onToggleRegular(cell.suggestion) },
                                // Dismissing a manual regular makes no sense — unmark it instead.
                                onNotInterested = if (cellIsRegular) null else {
                                    { onDismissSuggestion(cell.suggestion) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (rows.size > COLLAPSED_SUGGESTION_ROWS) {
                val hidden = group.items.size - rows.take(COLLAPSED_SUGGESTION_ROWS).sumOf { it.size }
                item(key = "${group.sourceId}-expand") {
                    ExpandToggle(
                        expanded = expanded,
                        hiddenCount = hidden,
                        onClick = { expandedSources[group.sourceId] = !expanded },
                    )
                }
            }
        }
    }
}

/** How many rows of a suggestion category show before "Show more" is tapped. */
private const val COLLAPSED_SUGGESTION_ROWS = 2

/** Rounded highlight shape for a tappable card — a touch larger than the image's own 20.dp corners
 *  so, with the content inset, the press/hover ripple reads as a concentric rounded frame. */
private val CARD_SHAPE = RoundedCornerShape(24.dp)

/** Full-width toggle under a category: expands the hidden rows, or collapses back to the first ones. */
@Composable
private fun ExpandToggle(expanded: Boolean, hiddenCount: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(if (expanded) "Show less" else "Show $hiddenCount more")
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SuggestionSectionHeader(
    group: SuggestionGroupUi,
    onAddAll: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.VisibilityOff,
                    contentDescription = "Dismiss ${group.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
    aisleName: String?,
    onClick: () -> Unit,
    onToggleRegular: () -> Unit,
    onNotInterested: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val suggestion = item.suggestion
    val keyboard = LocalSoftwareKeyboardController.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Clip so the press/hover ripple follows rounded corners, and pad so it isn't flush
            // against the image and label.
            modifier = Modifier
                .clip(CARD_SHAPE)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        keyboard?.hide()
                        menuExpanded = true
                    },
                )
                .padding(6.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                FoodImage(
                    url = suggestion.imageUrl,
                    name = suggestion.name,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    dimmed = item.added,
                )
                // Corner badge, opposite the "added" check so both can show at once: a star for the
                // user's favorites, otherwise the item's aisle icon. A surface disc keeps it legible
                // over any photo.
                if (isRegular) {
                    CornerBadge(Icons.Filled.Star, "Weekly regular", MaterialTheme.colorScheme.primary)
                } else if (aisleName != null) {
                    CornerBadge(aisleIcon(aisleName), aisleName, MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        }
        RegularContextMenu(
            expanded = menuExpanded,
            isRegular = isRegular,
            onDismiss = { menuExpanded = false },
            onToggle = {
                menuExpanded = false
                onToggleRegular()
            },
            onNotInterested = onNotInterested?.let {
                {
                    menuExpanded = false
                    it()
                }
            },
        )
    }
}

/** A small round badge in a suggestion card's image corner — the favorite star or an aisle icon. */
@Composable
private fun BoxScope.CornerBadge(icon: ImageVector, description: String, tint: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(14.dp))
    }
}

/**
 * Icon for a supermarket aisle, matched on its default name; renamed or custom aisles fall back to a
 * generic category tag. Shown on a suggestion card in place of the favorite star.
 */
private fun aisleIcon(aisleName: String): ImageVector = when (aisleName) {
    "Fruit & Vegetables" -> Icons.Outlined.Eco
    "Bakery" -> Icons.Outlined.BakeryDining
    "Dairy & Eggs" -> Icons.Outlined.Egg
    "Meat & Fish" -> Icons.Outlined.SetMeal
    "Pasta & Rice" -> Icons.Outlined.RiceBowl
    "Cans & Jars" -> Icons.Outlined.Inventory2
    "Frozen" -> Icons.Outlined.AcUnit
    "Drinks" -> Icons.Outlined.LocalDrink
    "Snacks" -> Icons.Outlined.Cookie
    "Household" -> Icons.Outlined.CleaningServices
    else -> Icons.Outlined.Category
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
            modifier = Modifier
                .clip(CARD_SHAPE)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        keyboard?.hide()
                        menuExpanded = true
                    },
                )
                .padding(6.dp),
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
    )
