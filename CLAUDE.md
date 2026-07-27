# Groceries — Claude Code Instructions

## Project
Kotlin Multiplatform (KMP) + Compose Multiplatform app.
Targets: Android, iOS, Desktop (JVM), Web (WASM).
Package: `com.emilflach.groceries`.

> **Status:** local-first shopping list (Step 1 of the smart-groceries roadmap) is in place —
> SQLDelight-backed `ShoppingListRepository`, plus a read-only bridge into a sibling
> **Lokcal** app's food database (see "Lokcal integration" below). No Mealie integration,
> pantry-depletion suggestions, or recipe suggestions yet (Steps 2–3) — don't assume those
> exist until you see them in the tree.

## Build & Test Strategy

This project is built with the **Kotlin Toolchain** via the `./kotlin`
wrapper — **not Gradle**. There are no Gradle build files; modules are declared in
`module.yaml` + `project.yaml`. The dependency catalog is `libs.versions.toml` at the
**project root**, consumed natively as `$libs.*` (built-in catalogs like `$compose.*` need
no entry there).

**Rules:** Compile the JVM/desktop target first (fastest) to catch errors. Build per-module
(`-m`); avoid bare `./kotlin build` (builds every target). Always build → test → fix.
Android builds need `ANDROID_HOME` set.

**Compose Hot Reload (desktop):** `./kotlin run -m desktopApp --compose-hot-reload-mode`.
It's a persistent session — **stop it with Ctrl-C in the launching terminal**, not by
closing the app window, or the DevTools sidecar window orphans and piles up across runs
(clear stragglers: `pkill -f 'apple.awt.application.name=Compose'`).

### Platform Commands

| Changed | Build | Test | Notes |
|---------|-------|------|-------|
| `shared/src/` (common) or `shared/src@jvm/` | `./kotlin build -m desktopApp` | `./kotlin test -m shared -p jvm` | **Default — use for 95% of changes** |
| `shared/src@android/` or `androidApp/` | `ANDROID_HOME=… ./kotlin build -m androidApp` | `./kotlin test -m shared -p android` | Embedded Gradle for AGP |
| `shared/src@ios/` or `iosApp/` | `./kotlin build -m shared -p iosSimulatorArm64`<br>(full app: `./kotlin build -m iosApp`) | `./kotlin test -m shared -p iosSimulatorArm64` | Needs Xcode |
| `shared/src@wasmJs/` (web) or `webApp/` | `./kotlin build -m webApp` | `./kotlin test -m shared -p wasmJs` | |
| Pre-release verification | `./kotlin build` (all targets) | `./kotlin check` (tests + checks) | |

Run the desktop app: `./kotlin run -m desktopApp`. Discover tasks/modules/settings with
`./kotlin show` and custom commands with `./kotlin do`.

## Code Structure

**Modules** (`project.yaml` lists them): `shared` (`kmp/lib`), `androidApp`
(`android/app`), `desktopApp` (`jvm/app`), `webApp` (`wasm-js/app`), `iosApp` (`ios/app`).

**`shared` uses the Kotlin Toolchain layout** (no `commonMain/kotlin`): common code in `shared/src/`,
platform actuals in `shared/src@android/`, `src@jvm/`, `src@ios/`, `src@wasmJs/`; tests go
in `shared/test/` (+ `test@jvm/`, etc.). `src@native/` also exists now — it covers all three
iOS targets collectively (Kotlin's `native` source set sits above `iosArm64`/`iosX64`/
`iosSimulatorArm64` in the hierarchy), used for code like the SQLDelight native driver that's
identical across them; `src@ios/` is still for genuinely iOS-only code (e.g. `Platform.ios.kt`).

**Current paths under `shared/`:**
- `src/App.kt` — root `App(sqlDriverFactory, lokcalCatalogReader, lokcalImportRepository)`
  composable; loads the `Database`, wires repositories/ViewModels, switches between
  `ShoppingListScreen`/`LokcalSetupScreen`. No `@Preview`/`@DevelopmentEntryPoint` — it
  requires real platform dependencies, so hot reload isn't wired up at this level (matches
  Lokcal's own `App(sqlDriverFactory)` precedent).
- `src/Platform.kt` — `expect fun platformName()`, with actuals per `src@<platform>/`
- `src/data/` — `SqlDriverFactory` (expect/actual, opens Groceries' own `groceries.db`) and
  `ShoppingListRepository`
- `src/lokcal/` — the read-only Lokcal bridge (see below)
- `src/viewmodel/` — `ShoppingListViewModel`, `LokcalSetupViewModel`: plain
  `StateFlow`-holding classes with their own `CoroutineScope(Dispatchers.Main)`, no
  `androidx.lifecycle.ViewModel` dependency (same pattern as Lokcal's `viewmodel/` package)
- `src/ui/screens/`, `src/ui/components/` — `ShoppingListScreen`, `LokcalSetupScreen`,
  `AddItemSheet`, `ShoppingListItemRow`
- `sqldelight/com/emilflach/groceries/` — `ShoppingListItem.sq`, `Meta.sq` (unlike other
  `.sq`-adjacent code, these files' directory nesting must mirror `packageName` — SQLDelight
  requires it)
- `composeResources/` — Compose resources; `Res` accessors →
  `com.emilflach.groceries.resources` (`exposedAccessors: true`, so previews and other
  modules can use `Res.*`). Currently empty — the starter demo strings/drawable were removed
  once real UI landed.

**App entry points** construct the platform actuals and pass them into `App(...)`:
- `desktopApp/src/main.kt` — `application { FileKit.init(appId = "Groceries"); Window { App(...) } }`
- `webApp/src/main.kt` — `ComposeViewport(document.body!!) { App(...) }`
- `androidApp/src/MainActivity.kt` — `FileKit.init(this)` then `setContent { App(...) }`
- `iosApp/src/ViewController.kt` — `ComposeUIViewController { App(...) }`, wrapped by
  `iosApp/src/iosApp.swift` (`import KotlinModules`, `ViewControllerKt.ViewController()`)

## Stack
- Kotlin Multiplatform + Compose Multiplatform, built with the Kotlin Toolchain.
- Compose deps declared in `shared/module.yaml`: `foundation`, `material3`, `preview`,
  `hotReload.runtimeApi`, `material-icons-extended` (pinned to `1.7.3` — Compose
  Multiplatform stopped publishing this artifact after that version) (+ `uiTooling` on
  Android).
- **SQLDelight** (`plugins/sqldelight`, vendored verbatim from Lokcal's own local Amper
  plugin) — `generateAsync: true`, so all generated queries are suspend-based
  (`awaitAsList()`/`awaitAsOne()`/`awaitAsOneOrNull()` from
  `app.cash.sqldelight.async.coroutines`). iOS needs `settings@ios.kotlin.linkerOptions:
  [-lsqlite3]` in `shared/module.yaml` to link the native driver's KLib — but that setting
  does **not** reach the final app-level link; `iosApp/module.xcodeproj/project.pbxproj`
  also needs `OTHER_LDFLAGS = "-lsqlite3";` set directly on both the Debug and Release
  build configurations (Amper won't regenerate an existing project, so this persists).
- **FileKit** (`filekit-core`/`filekit-dialogs`/`filekit-dialogs-compose`) for file/folder
  pickers and the Android SAF bookmark flow. `FileKit.init(...)` must run once per platform
  entry point before any picker/bookmark call.
- Desktop uses `compose.desktop.currentOs` + `logback-classic` + **`kotlinx-coroutines-swing`**
  (required — without it, any `Dispatchers.Main` use throws `IllegalStateException: Module
  with the Main dispatcher is missing` at runtime on JVM); Android uses
  `androidx-activity-compose`.
- Any module using FileKit directly (not just transitively via `shared`) must declare
  `$libs.filekit.core` itself — Amper doesn't re-expose `shared`'s dependencies to consumers.

## Architecture
`expect`/`actual` for platform-specific code: `platformName()`, `SqlDriverFactory`,
`LokcalCatalogReader`, `LokcalImportRepository`, `LokcalBookmarkStore`. Repository / ViewModel
(StateFlow) layers live in `src/data/` and `src/viewmodel/`.

## Lokcal integration
Lokcal (sibling app, `~/AndroidStudioProjects/Lokcal`) is a separate KMP app with its own
local SQLite database (`Food`, `Intake`, `Meal`/`MealItem` tables) — no API, no shared
backend. On Android, Lokcal copies its DB nightly to a user-chosen SAF folder
(`lokcal-backup-<epoch-millis>.db`, no pruning). Groceries reads from that same folder as a
**separate SAF permission grant** — Android's persistable URI permissions are scoped per
requesting app, so Groceries always needs its own folder-picker flow even when pointed at
the exact folder Lokcal already uses (see `LokcalSetupScreen`/`LokcalImportRepository`).

Reading Lokcal's data is **hand-written raw SQL against a plain read-only
`SQLiteDatabase`** (`LokcalCatalogReader.android.kt`), not a second SQLDelight-compiled
schema — this avoids a second Amper module just for a handful of read queries, and avoids
Groceries' build breaking on unrelated Lokcal schema changes. Only Android has a real
implementation today; `src@jvm/`, `src@native/`, `src@wasmJs/` are honest stubs
(`hasSnapshot() = false`, `SyncResult.Failed("...only available on Android right now")`) —
don't assume Lokcal data is reachable on those platforms yet.

## iOS — What Needs Implementing Twice
Swift sources live in `iosApp/src/` and `import KotlinModules` (the Kotlin Toolchain's framework name).
The Kotlin Toolchain generates `iosApp/module.xcodeproj` on first build; the committed
`project.pbxproj` is tracked while the rest of `module.xcodeproj/` is gitignored. Today
there's a single `ViewController()` factory bridged by `ComposeView`
(`UIViewControllerRepresentable`) in `iosApp.swift`. Once per-screen native navigation is
introduced, wire each new screen through a `*ViewController()` factory + its SwiftUI
wrapper here.
