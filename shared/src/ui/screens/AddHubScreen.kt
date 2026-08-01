package com.emilflach.groceries.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.Aisle
import com.emilflach.groceries.data.normalizeKey
import com.emilflach.groceries.lokcal.LokcalCatalogReader
import com.emilflach.groceries.lokcal.LokcalFood
import com.emilflach.groceries.lokcal.LokcalMeal
import com.emilflach.groceries.lokcal.LokcalMealItem
import com.emilflach.groceries.mealie.MealieClient
import com.emilflach.groceries.mealie.MealieIngredientLine
import com.emilflach.groceries.mealie.MealieRecipeSummary
import com.emilflach.groceries.recommendations.RegularMealSource
import com.emilflach.groceries.recommendations.Suggestion
import com.emilflach.groceries.recommendations.formatGrams
import com.emilflach.groceries.ui.components.AislePickerSheet
import com.emilflach.groceries.ui.components.FoodImage
import com.emilflach.groceries.ui.util.PlatformBackHandler
import com.emilflach.groceries.viewmodel.SuggestionGroupUi
import com.emilflach.groceries.viewmodel.SuggestionUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The full-screen "Add" hub opened from the shopping-list FAB. With a blank search box it shows
 * grouped, one-tap suggestions (regulars today; more sources later); as soon as you type it
 * becomes the food search — so everything to add lives in one place and search is always a keystroke
 * away for anything the suggestions don't cover. Long-pressing any card opens a small context menu
 * to mark/unmark it as a regular.
 */
@Composable
fun AddHubScreen(
    catalogReader: LokcalCatalogReader,
    mealieClient: MealieClient,
    groups: List<SuggestionGroupUi>,
    regularKeys: Set<String>,
    addedKeys: Set<String>,
    aisleNames: Map<String, String>,
    aisles: List<Aisle>,
    aisleIdByKey: Map<String, Long>,
    onDismiss: () -> Unit,
    onFoodSelected: (LokcalFood) -> Unit,
    onAddCustom: (String) -> Unit,
    onAddIngredient: (IngredientUi) -> Unit,
    onToggleSuggestion: (Suggestion) -> Unit,
    onAddAll: (SuggestionGroupUi) -> Unit,
    onToggleRegular: (Suggestion) -> Unit,
    onAssignAisle: (Suggestion, Long) -> Unit,
    onClearAisle: (Suggestion) -> Unit,
    onDismissMeal: (SuggestionGroupUi) -> Unit,
    onRestoreMeal: (SuggestionGroupUi) -> Unit,
    onDismissSuggestion: (Suggestion) -> Unit,
    onRestoreSuggestion: (Suggestion) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Search now spans three sources; each keeps its own result list so a section can render as soon
    // as its lookup returns and a slow one never blocks the others.
    var foodResults by remember { mutableStateOf<List<LokcalFood>>(emptyList()) }
    var mealResults by remember { mutableStateOf<List<LokcalMeal>>(emptyList()) }
    var recipeResults by remember { mutableStateOf<List<MealieRecipeSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    // The tapped meal/recipe whose ingredient list is shown in a bottom sheet; null = sheet closed.
    var expandedRecipe by remember { mutableStateOf<RecipeHit?>(null) }
    var recipeIngredients by remember { mutableStateOf<List<IngredientUi>>(emptyList()) }
    var ingredientsLoading by remember { mutableStateOf(false) }

    val searchListState = rememberLazyListState()
    val suggestionsListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val trimmedQuery = query.trim()
    val searching = trimmedQuery.isNotEmpty()

    // Collapse the title once the active list is scrolled past its first item, giving the results
    // more room (especially with the keyboard up); the search field itself stays pinned. Keyed on
    // the item index only — NOT the scroll offset, which reads non-zero transiently during scrolling
    // and would flicker the title, jittering the layout.
    val searchScrolled by remember { derivedStateOf { searchListState.firstVisibleItemIndex > 0 } }
    val suggestionsScrolled by remember { derivedStateOf { suggestionsListState.firstVisibleItemIndex > 0 } }
    val titleVisible = !(if (searching) searchScrolled else suggestionsScrolled)

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

    // The suggestion whose aisle is being picked (via a card's long-press menu); null = sheet closed.
    var aisleTarget by remember { mutableStateOf<Suggestion?>(null) }

    PlatformBackHandler { onDismiss() }

    // Don't grab focus on open: the suggestions cover most adds, so we leave the keyboard down and
    // let the user tap the field when they actually want to search (the focusRequester still wires
    // the field up for that tap).

    // Only hit the sources once the user is actually searching — blank query shows suggestions. The
    // three lookups run in parallel so one slow source (a Mealie network round-trip) doesn't hold up
    // the others; foods/meals come from the local snapshot and return near-instantly.
    LaunchedEffect(query) {
        if (!searching) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE)
        coroutineScope {
            val foods = async { runCatching { catalogReader.searchFoods(trimmedQuery) }.getOrDefault(emptyList()) }
            val meals = async { runCatching { catalogReader.searchMeals(trimmedQuery) }.getOrDefault(emptyList()) }
            val recipes = async { mealieClient.searchRecipes(trimmedQuery) } // never throws
            foodResults = foods.await()
            mealResults = meals.await()
            recipeResults = recipes.await()
        }
        loaded = true
        searchListState.scrollToItem(0)
    }

    // Load the tapped meal's/recipe's ingredients on demand, into the bottom sheet.
    LaunchedEffect(expandedRecipe) {
        val hit = expandedRecipe ?: return@LaunchedEffect
        ingredientsLoading = true
        recipeIngredients = when (hit.source) {
            RecipeSource.LOKCAL_MEAL ->
                runCatching { catalogReader.mealItems(hit.lokcalMealId!!).map { it.toIngredientUi() } }
                    .getOrDefault(emptyList())
            RecipeSource.MEALIE_RECIPE ->
                mealieClient.recipeIngredients(hit.mealieSlug!!).map { it.toIngredientUi() }
        }
        ingredientsLoading = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            AnimatedVisibility(
                visible = titleVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search foods, meals & recipes") },
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

            // Breathing room so scrolling content doesn't butt right up against the search field.
            Spacer(Modifier.height(12.dp))

            if (searching) {
                SearchResults(
                    query = trimmedQuery,
                    foods = foodResults,
                    meals = mealResults,
                    recipes = recipeResults,
                    loaded = loaded,
                    listState = searchListState,
                    regularKeys = regularKeys,
                    addedKeys = addedKeys,
                    onAddCustom = {
                        onAddCustom(trimmedQuery)
                        onDismiss()
                    },
                    // Stay on the search after adding, so several items can be added in a row.
                    // Back/dismiss is how you return to the list.
                    onFoodSelected = { food -> onFoodSelected(food) },
                    onOpenRecipe = { expandedRecipe = it },
                    onToggleRegular = { onToggleRegular(it.toSuggestion()) },
                    onAssignAisle = { aisleTarget = it.toSuggestion() },
                )
            } else {
                Suggestions(
                    groups = groups,
                    regularKeys = regularKeys,
                    aisleNames = aisleNames,
                    listState = suggestionsListState,
                    onToggle = onToggleSuggestion,
                    onAddAll = onAddAll,
                    onToggleRegular = onToggleRegular,
                    onAssignAisle = { aisleTarget = it },
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

    aisleTarget?.let { target ->
        AislePickerSheet(
            itemName = target.name,
            aisles = aisles,
            selectedAisleId = aisleIdByKey[target.key],
            onSelect = { aisleId ->
                onAssignAisle(target, aisleId)
                aisleTarget = null
            },
            onClear = {
                onClearAisle(target)
                aisleTarget = null
            },
            onDismiss = { aisleTarget = null },
        )
    }

    // Tapping a meal/recipe opens its ingredients in a sheet — the search's answer to the
    // suggestions' meal section: add ingredients one at a time or all at once.
    expandedRecipe?.let { hit ->
        RecipeDetailSheet(
            hit = hit,
            ingredients = recipeIngredients,
            loading = ingredientsLoading,
            addedKeys = addedKeys,
            onAddIngredient = onAddIngredient,
            onAddAll = {
                recipeIngredients.filter { normalizeKey(it.name) !in addedKeys }.forEach(onAddIngredient)
            },
            onDismiss = { expandedRecipe = null },
        )
    }
}

/** Long-press context menu anchored to a card: a single toggle for regular status. Appearing
 *  right at the card (rather than a bottom sheet) avoids the jarring keyboard-then-sheet swap when a
 *  search result is long-pressed with the keyboard up. */
@Composable
private fun RegularContextMenu(
    expanded: Boolean,
    isRegular: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onAssignAisle: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isRegular) "Remove from regulars" else "Mark as regular") },
            leadingIcon = {
                Icon(
                    if (isRegular) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = onToggle,
        )
        // Categorising a food when adding it — the aisle assignment moved off the list row into here.
        if (onAssignAisle != null) {
            DropdownMenuItem(
                text = { Text("Assign aisle") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onAssignAisle,
            )
        }
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
    listState: LazyListState,
    onToggle: (Suggestion) -> Unit,
    onAddAll: (SuggestionGroupUi) -> Unit,
    onToggleRegular: (Suggestion) -> Unit,
    onAssignAisle: (Suggestion) -> Unit,
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
    var showAllMeals by remember { mutableStateOf(false) }

    // Cap how many meal recipes show up front (there can be many); the rest are one tap away. Meal
    // groups sort after the flat food groups, so appending the capped meals preserves order.
    val mealGroups = groups.filter { it.sourceId.startsWith(RegularMealSource.ID) }
    val otherGroups = groups.filterNot { it.sourceId.startsWith(RegularMealSource.ID) }
    val displayGroups = otherGroups + if (showAllMeals) mealGroups else mealGroups.take(INITIAL_MEAL_GROUPS)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        displayGroups.forEach { group ->
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
                                onAssignAisle = { onAssignAisle(cell.suggestion) },
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
        if (mealGroups.size > INITIAL_MEAL_GROUPS) {
            item(key = "more-meals") {
                TextButton(
                    onClick = { showAllMeals = !showAllMeals },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                ) {
                    Icon(
                        if (showAllMeals) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showAllMeals) "Show fewer meals"
                        else "Show ${mealGroups.size - INITIAL_MEAL_GROUPS} more meals",
                    )
                }
            }
        }
    }
}

/** How many meal recipes show before "Show more meals" is tapped. */
private const val INITIAL_MEAL_GROUPS = 3

/** How many rows of a suggestion category show before "Show more" is tapped. */
private const val COLLAPSED_SUGGESTION_ROWS = 2

/** Debounce between the last keystroke and hitting the catalog — short enough to feel live. */
private val SEARCH_DEBOUNCE = 120.milliseconds

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
    // Meals get a distinctive "recipe banner"; the flat food groups keep a plain text header.
    if (group.sourceId.startsWith(RegularMealSource.ID)) {
        MealSectionHeader(group = group, onAddAll = onAddAll, onDismiss = onDismiss)
        return
    }
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
        if (group.supportsBulkAdd && !group.allAdded) {
            TextButton(onClick = onAddAll) {
                Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add all")
            }
        }
    }
}

/** A meal recipe's header: a rounded tonal banner with the meal photo, name, an ingredient count and
 *  a leading "Recipe" label — visually set apart from the flat food-group headers so each recipe is
 *  easy to spot and scan. */
@Composable
private fun MealSectionHeader(
    group: SuggestionGroupUi,
    onAddAll: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            FoodImage(
                url = group.imageUrl,
                name = group.title,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = "Dismiss ${group.title}",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionCard(
    item: SuggestionUi,
    isRegular: Boolean,
    aisleName: String?,
    onClick: () -> Unit,
    onToggleRegular: () -> Unit,
    onAssignAisle: () -> Unit,
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
                    CornerBadge(Icons.Filled.Star, "Regulars", MaterialTheme.colorScheme.primary)
                } else if (aisleName != null) {
                    CornerBadge(aisleIcon(aisleName), aisleName, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.added) AddedCheck()
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
            onAssignAisle = {
                menuExpanded = false
                onAssignAisle()
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

/** The "added" mark shown in a card's top-right corner once its food is on the list — a filled
 *  primary disc with a check, opposite the [CornerBadge] so both can show at once. Shared by the
 *  suggestion cards and the search-result cards so "added" looks identical everywhere. */
@Composable
private fun BoxScope.AddedCheck() {
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
 * Icon for a supermarket aisle. Matched on keywords (case-insensitive) rather than the exact default
 * name, so renamed, pluralised or otherwise custom aisles still get a fitting icon; only a genuinely
 * unrecognised aisle falls back to the generic category tag. Shown on a suggestion card in place of
 * the favorite star.
 */
private fun aisleIcon(aisleName: String): ImageVector {
    val n = aisleName.lowercase()
    fun has(vararg keys: String) = keys.any { it in n }
    return when {
        has("veg", "fruit", "produce", "greens", "salad") -> Icons.Outlined.Eco
        has("bak", "bread", "pastr") -> Icons.Outlined.BakeryDining
        has("dairy", "egg", "cheese", "milk", "yog", "yogh") -> Icons.Outlined.Egg
        has("meat", "fish", "poultry", "butcher", "seafood") -> Icons.Outlined.SetMeal
        has("pasta", "rice", "noodle", "grain", "cereal") -> Icons.Outlined.RiceBowl
        has("can", "jar", "tin", "preserv", "sauce", "condiment") -> Icons.Outlined.Inventory2
        has("frozen", "freezer", "ice") -> Icons.Outlined.AcUnit
        has("drink", "beverage", "juice", "water", "soda", "coffee", "tea") -> Icons.Outlined.LocalDrink
        has("snack", "candy", "sweet", "chocolate", "crisp", "chip", "biscuit", "cookie") -> Icons.Outlined.Cookie
        has("house", "home", "clean", "laundry", "paper", "toilet") -> Icons.Outlined.CleaningServices
        else -> Icons.Outlined.Category
    }
}

/**
 * Sectioned search results: catalog **Foods** (a 3-up grid, adds one item), Lokcal **Meals** and
 * Mealie **Recipes** (rows that open the ingredient sheet). Each section renders only when it has
 * hits, and the source of every meal/recipe is labelled so a Lokcal meal and a Mealie recipe never
 * look alike.
 */
@Composable
private fun ColumnScope.SearchResults(
    query: String,
    foods: List<LokcalFood>,
    meals: List<LokcalMeal>,
    recipes: List<MealieRecipeSummary>,
    loaded: Boolean,
    listState: LazyListState,
    regularKeys: Set<String>,
    addedKeys: Set<String>,
    onAddCustom: () -> Unit,
    onFoodSelected: (LokcalFood) -> Unit,
    onOpenRecipe: (RecipeHit) -> Unit,
    onToggleRegular: (LokcalFood) -> Unit,
    onAssignAisle: (LokcalFood) -> Unit,
) {
    val nothingFound = loaded && foods.isEmpty() && meals.isEmpty() && recipes.isEmpty()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        // Anything none of the sources has can still go on the list as a free-typed item.
        item(key = "add-custom") { AddCustomRow(name = query, onClick = onAddCustom) }

        if (nothingFound) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing matches \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (foods.isNotEmpty()) {
            item(key = "foods-header") { SearchSectionHeader("Foods") }
            // Rows of three so this LazyColumn can host the grid without a nested vertical scroll.
            foods.chunked(3).forEachIndexed { index, row ->
                item(key = "foods-row-$index") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        for (food in row) {
                            FoodPickCard(
                                food = food,
                                isRegular = normalizeKey(food.name) in regularKeys,
                                added = normalizeKey(food.name) in addedKeys,
                                onClick = { onFoodSelected(food) },
                                onToggleRegular = { onToggleRegular(food) },
                                onAssignAisle = { onAssignAisle(food) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (meals.isNotEmpty()) {
            item(key = "meals-header") { SearchSectionHeader("Meals") }
            meals.forEach { meal ->
                item(key = "meal-${meal.id}") {
                    RecipeHitRow(
                        title = meal.name,
                        imageUrl = meal.imageUrl,
                        source = RecipeSource.LOKCAL_MEAL,
                        onClick = { onOpenRecipe(meal.toHit()) },
                    )
                }
            }
        }

        if (recipes.isNotEmpty()) {
            item(key = "recipes-header") { SearchSectionHeader("Recipes") }
            recipes.forEach { recipe ->
                item(key = "recipe-${recipe.id}") {
                    RecipeHitRow(
                        title = recipe.name,
                        imageUrl = recipe.imageUrl,
                        source = RecipeSource.MEALIE_RECIPE,
                        onClick = { onOpenRecipe(recipe.toHit()) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodPickCard(
    food: LokcalFood,
    isRegular: Boolean,
    added: Boolean,
    onClick: () -> Unit,
    onToggleRegular: () -> Unit,
    onAssignAisle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
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
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                FoodImage(
                    url = food.imageUrl,
                    name = food.name,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    dimmed = added,
                )
                if (added) AddedCheck()
            }
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
            onAssignAisle = {
                menuExpanded = false
                onAssignAisle()
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

/**
 * An ingredient of a tapped meal/recipe, ready to add to the list. [lokcalFoodId] is set for a
 * catalog-backed Lokcal food (added via the shared toggle, so it dedups against the list) and null
 * for a free-text Mealie ingredient (added as a custom item).
 */
data class IngredientUi(
    val name: String,
    val imageUrl: String?,
    val note: String?,
    val lokcalFoodId: Long?,
)

/** Where a search hit's recipe came from — surfaced as a label so the two never look alike. */
private enum class RecipeSource(val label: String) {
    LOKCAL_MEAL("Lokcal meal"),
    MEALIE_RECIPE("Mealie recipe"),
}

/** A tapped meal/recipe search hit, carrying just enough to fetch its ingredients on demand. */
private data class RecipeHit(
    val title: String,
    val imageUrl: String?,
    val source: RecipeSource,
    val lokcalMealId: Long?,
    val mealieSlug: String?,
)

private fun LokcalMeal.toHit() = RecipeHit(name, imageUrl, RecipeSource.LOKCAL_MEAL, id, null)

private fun MealieRecipeSummary.toHit() = RecipeHit(name, imageUrl, RecipeSource.MEALIE_RECIPE, null, slug)

private fun LokcalMealItem.toIngredientUi() = IngredientUi(
    name = food.name,
    imageUrl = food.imageUrl,
    note = formatGrams(quantityG),
    lokcalFoodId = food.id,
)

private fun MealieIngredientLine.toIngredientUi() = IngredientUi(
    name = name,
    imageUrl = null,
    note = note,
    lokcalFoodId = null,
)

/** Plain text header separating the Foods / Meals / Recipes sections of the search results. */
@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
    )
}

/** A meal/recipe search hit: a tappable tile with the photo, name and a source label ("Lokcal meal"
 *  / "Mealie recipe"). Tapping opens the ingredient sheet. */
@Composable
private fun RecipeHitRow(
    title: String,
    imageUrl: String?,
    source: RecipeSource,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp),
        ) {
            FoodImage(
                url = imageUrl,
                name = title,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The ingredient sheet — the search's counterpart to the suggestions' meal section: a banner with
 * the recipe photo, name and source label, an "Add all", and a grid of ingredient cards. Ingredients
 * load lazily (spinner meanwhile). Each card adds its ingredient; already-listed ones show a check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailSheet(
    hit: RecipeHit,
    ingredients: List<IngredientUi>,
    loading: Boolean,
    addedKeys: Set<String>,
    onAddIngredient: (IngredientUi) -> Unit,
    onAddAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FoodImage(
                    url = hit.imageUrl,
                    name = hit.title,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.source.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (ingredients.isNotEmpty()) {
                val allAdded = ingredients.all { normalizeKey(it.name) in addedKeys }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${ingredients.size} ingredient${if (ingredients.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!allAdded) {
                        TextButton(onClick = onAddAll) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add all")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                ingredients.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No ingredients found for this ${if (hit.source == RecipeSource.MEALIE_RECIPE) "recipe" else "meal"}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> ingredients.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        for (ingredient in row) {
                            val added = normalizeKey(ingredient.name) in addedKeys
                            IngredientCard(
                                ingredient = ingredient,
                                added = added,
                                // Add-only: a second tap on an added item is a no-op, so a re-tap
                                // (or a custom Mealie ingredient) never stacks duplicates.
                                onClick = { if (!added) onAddIngredient(ingredient) },
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

/** A single ingredient in the recipe sheet — photo (or a name-derived placeholder), name, optional
 *  amount, and an "added" check once it's on the list. */
@Composable
private fun IngredientCard(
    ingredient: IngredientUi,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(CARD_SHAPE)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            FoodImage(
                url = ingredient.imageUrl,
                name = ingredient.name,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
                dimmed = added,
            )
            if (added) AddedCheck()
        }
        Text(
            text = ingredient.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (!ingredient.note.isNullOrBlank()) {
            Text(
                text = ingredient.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
