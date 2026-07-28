# Groceries — Claude Code Instructions

KMP + Compose Multiplatform app (Android, iOS, Desktop/JVM, Web/WASM). Package `com.emilflach.groceries`.

**Status:** local-first shopping list (roadmap Step 1) — SQLDelight `ShoppingListRepository` + a read-only bridge into the sibling **Lokcal** app's food DB. No Mealie / pantry-depletion / recipe suggestions yet (Steps 2–3); don't assume they exist.

## Build & test
**Kotlin Toolchain** via `./kotlin` — **not Gradle**. Modules in `module.yaml` + `project.yaml`; deps in root `libs.versions.toml` as `$libs.*` (built-in `$compose.*` needs no entry). Compile JVM/desktop first (fastest); build per-module with `-m` (avoid bare `./kotlin build`); Android needs `ANDROID_HOME`.

| Changed | Build | Test |
|---|---|---|
| `shared/src/` (common) or `src@jvm/` | `./kotlin build -m desktopApp` | `./kotlin test -m shared -p jvm` — **default, ~95%** |
| `src@android/` or `androidApp/` | `ANDROID_HOME=… ./kotlin build -m androidApp` | `./kotlin test -m shared -p android` |
| `src@ios/` or `iosApp/` | `./kotlin build -m shared -p iosSimulatorArm64` (full app: `-m iosApp`) | `… -p iosSimulatorArm64` (needs Xcode) |
| `src@wasmJs/` or `webApp/` | `./kotlin build -m webApp` | `./kotlin test -m shared -p wasmJs` |
| pre-release | `./kotlin build` (all) | `./kotlin check` |

Run: `./kotlin run -m desktopApp` (discover tasks with `./kotlin show`). Hot reload: add `--compose-hot-reload-mode` — a persistent session; stop with **Ctrl-C**, not by closing the window (else the DevTools sidecar orphans: `pkill -f 'apple.awt.application.name=Compose'`). For UI screenshots/validation, Compose Hot Reload has an MCP server (`take_screenshot`, `get_semantic_tree`, `click`/`type_text`/…), but it's exposed only as a Gradle `hotMcpServer` task this Toolchain project doesn't have yet — until then, screenshot the running desktop app.

## Layout
Modules: `shared` (kmp/lib), `androidApp`, `desktopApp`, `webApp`, `iosApp`.

Toolchain layout (no `commonMain/kotlin`): common in `shared/src/`, actuals in `src@<platform>/`, tests in `test/` (+ `test@jvm/`). `src@native/` = all iOS targets collectively (shared native code, e.g. the SQLDelight driver); `src@ios/` = iOS-only (e.g. `Platform.ios.kt`). SQLDelight `.sq` files live in `sqldelight/com/emilflach/groceries/` — **directory nesting must mirror `packageName`**.

`shared/src/` holds: `App.kt` (root `App(sqlDriverFactory, lokcalCatalogReader, lokcalImportRepository)` — loads `Database`, wires repos/ViewModels, switches `ShoppingListScreen`/`LokcalSetupScreen`; no `@Preview`, needs real platform deps), `Platform.kt`, `data/`, `lokcal/` (bridge — see below), `viewmodel/` (plain `StateFlow` classes with their own `CoroutineScope(Dispatchers.Main)`, no androidx `ViewModel`), `ui/{screens,components,theme}/`. `expect`/`actual` is used for all platform code (`platformName`, `SqlDriverFactory`, the `Lokcal*` classes).

Entry points build the platform actuals and pass them to `App(...)`, each calling `FileKit.init(...)` first (Android `FileKit.init(this)`; desktop `FileKit.init(appId = "Groceries")`). iOS: `ViewController.kt` → `ComposeUIViewController { App(...) }`, wrapped by `iosApp.swift` (`import KotlinModules`).

## Dependencies & gotchas
CMP `foundation`/`material3`/`preview`/`hotReload.runtimeApi` (+ `uiTooling` on Android); Coil3 (`coil-compose` + `coil-network-ktor3`) + Ktor load the remote food photos (loader set up in `ui/util/CoilSetup.kt`); desktop needs `logback-classic` + **`kotlinx-coroutines-swing`** (else any `Dispatchers.Main` use throws "Module with the Main dispatcher is missing" at runtime).

- **Verify every KMP dependency on [klibs.io](https://klibs.io)** — the KMP source of truth (MCP `https://api.klibs.io/mcp`; pages `klibs.io/package/<group>/<artifact>`). Confirm coordinates, the newest **stable** version, and target support before adding or bumping; don't guess from READMEs.
- **Kotlin toolchain (2.3.21) rejects newer-ABI KLIBs** — a lib whose native klib was built with Kotlin 2.4.0+ fails the iOS/native build with "incompatible ABI version" (why **`filekit 0.14.2` currently breaks iOS**). Check the release's Kotlin version on klibs.io before bumping.
- **Amper doesn't re-expose transitive deps** — a module must declare libs it uses directly (e.g. `desktopApp` declares `$libs.filekit.core`), and anything you `import` must be a direct dependency even if it rides in transitively (e.g. `org.xerial:sqlite-jdbc` via the SQLDelight sqlite driver).
- **Compose Material3 is versioned independently** of CMP — `$compose.material3` won't resolve once Compose is pinned, so it's declared explicitly (`composeMaterial3`), as is `material-icons-extended` (`1.7.3`; CMP stopped publishing later versions).
- **SQLDelight** (`plugins/sqldelight`, vendored from Lokcal; `generateAsync: true` → suspend `awaitAs*` queries) — iOS needs `settings@ios.kotlin.linkerOptions: [-lsqlite3]` in `shared/module.yaml` **and** `OTHER_LDFLAGS = "-lsqlite3";` on both Debug + Release configs in `iosApp/module.xcodeproj/project.pbxproj` (the yaml setting doesn't reach the app-level link; Amper won't regenerate the project).

## Lokcal integration
Lokcal (`~/AndroidStudioProjects/Lokcal`) — a separate KMP app with its own local SQLite DB (`Food`, `Intake`, `Meal`/`MealItem`), no API. On Android it nightly-copies its DB to a user-chosen SAF folder (`lokcal-backup-<epoch-millis>.db`, no pruning). Groceries reads it via its **own** SAF grant (persistable URI permissions are per-app → Groceries needs its own folder picker even for the same folder), using **hand-written raw SQL on a read-only `SQLiteDatabase`** (`LokcalCatalogReader.android.kt`), not a 2nd SQLDelight schema (avoids an extra Amper module + build coupling to Lokcal's schema). **Android-only today**; `src@jvm/`/`src@native/`/`src@wasmJs/` are stubs (`hasSnapshot() = false`).

## iOS bridge
Swift in `iosApp/src/` does `import KotlinModules`. The Toolchain generates `iosApp/module.xcodeproj` on first build; only `project.pbxproj` is tracked (rest gitignored). One `ViewController()` factory bridged by `ComposeView` in `iosApp.swift`; for per-screen native nav, add a `*ViewController()` factory + SwiftUI wrapper per screen.
