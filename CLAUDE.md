# Groceries — Claude Code Instructions

## Project
Kotlin Multiplatform (KMP) + Compose Multiplatform app.
Targets: Android, iOS, Desktop (JVM), Web (WASM).
Package: `com.emilflach.groceries`.

> **Status:** early scaffold. Right now `shared/src/` holds the starter `App()` (Greeting
> demo) plus an `expect`/`actual` `platformName()`. There's no data/viewmodel/UI-screen
> layer yet — add the sections below as those land, and don't assume repositories,
> SQLDelight, Ktor, or navigation exist until you see them in the tree.

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
in `shared/test/` (+ `test@jvm/`, etc.).

**Current paths under `shared/`:**
- `src/App.kt` — root `App()` composable (`@Preview` + `@DevelopmentEntryPoint` for hot
  reload; public, consumed by every app module)
- `src/Greeting.kt`, `src/GreetingCard.kt` — starter demo (replace as the real UI lands)
- `src/Platform.kt` — `expect fun platformName()`, with actuals per `src@<platform>/`
- `composeResources/` — Compose resources; `Res` accessors →
  `com.emilflach.groceries.resources` (`exposedAccessors: true`, so previews and other
  modules can use `Res.*`)

**App entry points** consume the shared `App()`:
- `desktopApp/src/main.kt` — `application { Window { App() } }`
- `webApp/src/main.kt` — `ComposeViewport(document.body!!) { App() }`
- `androidApp/src/MainActivity.kt` — `ComponentActivity` → `setContent { App() }`
- `iosApp/src/ViewController.kt` — `ComposeUIViewController { App() }`, wrapped by
  `iosApp/src/iosApp.swift` (`import KotlinModules`, `ViewControllerKt.ViewController()`)

## Stack
- Kotlin Multiplatform + Compose Multiplatform, built with the Kotlin Toolchain.
- Compose deps declared in `shared/module.yaml`: `foundation`, `material3`, `preview`,
  `hotReload.runtimeApi` (+ `uiTooling` on Android).
- Desktop uses `compose.desktop.currentOs` + `logback-classic`; Android uses
  `androidx-activity-compose`.

## Architecture
`expect`/`actual` for platform-specific code (currently just `platformName()`). Add the
repository / ViewModel (StateFlow) layers here as they're introduced.

## iOS — What Needs Implementing Twice
Swift sources live in `iosApp/src/` and `import KotlinModules` (the Kotlin Toolchain's framework name).
The Kotlin Toolchain generates `iosApp/module.xcodeproj` on first build; the committed
`project.pbxproj` is tracked while the rest of `module.xcodeproj/` is gitignored. Today
there's a single `ViewController()` factory bridged by `ComposeView`
(`UIViewControllerRepresentable`) in `iosApp.swift`. Once per-screen native navigation is
introduced, wire each new screen through a `*ViewController()` factory + its SwiftUI
wrapper here.
