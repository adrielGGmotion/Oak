# Oak Development Guide

Complete guide for developing, building, and contributing to Oak.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (Temurin) | Required for Kotlin 2.3.21 |
| Android SDK | compileSdk 37, targetSdk 36 | build-tools 36.0.0 |
| NDK | r29 (29.0.14206865) | Only for proot native builds |
| Gradle | 9.6.1 | Use the wrapper (`./gradlew`) |
| IDE | Android Studio or IntelliJ | With Kotlin Multiplatform plugin |

## Project Structure

```
Oak/
├── androidApp/                     # Thin Android shell (Application, Activity, Manifest)
├── composeApp/src/
│   ├── commonMain/kotlin/com/oak/app/   # All shared logic
│   │   ├── data/                 # DataRepository, settings, memory, tasks, heartbeat
│   │   ├── ui/chat/              # Chat screen + ViewModel
│   │   ├── ui/settings/          # Settings screen + ViewModel
│   │   ├── ui/components/        # Reusable composables
│   │   ├── ui/dynamicui/         # AI-generated interactive UI blocks (oak-ui)
│   │   ├── ui/markdown/          # Custom markdown parser + renderer
│   │   ├── ui/sandbox/           # Linux sandbox file browser + packages
│   │   ├── tools/                # Tool implementations (calendar, email, SMS, etc.)
│   │   ├── network/              # HTTP clients, DTOs (Anthropic/Gemini/OpenAI)
│   │   ├── inference/            # On-device inference engine (LiteRT)
│   │   ├── mcp/                  # Model Context Protocol server client
│   │   └── AppModule.kt         # Koin DI module definitions
│   ├── androidMain/              # Android actuals (sandbox, notifications, SMS, keystore)
│   ├── desktopMain/              # Desktop actuals (ProcessBuilder, tray, file system)
│   ├── jvmShared/                # Shared JVM code (LiteRT inference, included in androidMain + desktopMain)
│   ├── commonTest/               # Platform-agnostic unit tests
│   └── desktopTest/              # Desktop-specific tests
├── gradle/libs.versions.toml     # Single source of truth for dependency versions
├── scripts/                      # Utility scripts
└── .github/workflows/            # CI/CD pipelines
```

All business logic lives in `composeApp`. The `androidApp` module is a thin shell with just the entry point.

## Architecture

**Pattern**: MVVM with Repository layer, Koin DI, expect/actual for platform abstraction.

```
UI (Compose) → ViewModel → DataRepository → platform actuals (expect/actual)
                                    ↓
                            RemoteDataRepository
                                    ↓
                            Ktor HTTP → LLM APIs
```

**Key components**:
- **Koin DI** — Modules defined in `AppModule.kt`. Use `koinInject<T>()` in composables, `koinViewModel()` for ViewModels.
- **DataRepository** — Single interface for all data ops. `RemoteDataRepository` is the sole implementation.
- **Platform abstraction** — `expect`/`actual` declarations split across `androidMain`/`desktopMain`/`jvmShared`.
- **Navigation** — Jetpack Navigation Compose with `@Serializable` route objects (`Home`, `Settings`). Nav graph defined in `App.kt`.
- **LLM clients** — OpenAI-compatible, Anthropic, Gemini with auto-fallback between services.

## Build Commands

### Android

```bash
# Debug APK (staging flavor)
./gradlew :androidApp:assembleStagingDebug

# All staging variants
./gradlew :androidApp:assembleStagingDebug
./gradlew :androidApp:assembleStagingPerformance   # minified, no resource shrink, debug signing
./gradlew :androidApp:assembleStagingRelease

# All nightly variants
./gradlew :androidApp:assembleNightlyDebug
./gradlew :androidApp:assembleNightlyPerformance
./gradlew :androidApp:assembleNightlyRelease

# Production
./gradlew :androidApp:assembleProductionRelease
```

**Flavors** (`mode` dimension): `production`, `nightly` (suffix `.nightly`), `staging` (suffix `.testing`)
**Build types**: `debug` (suffix `.debug`), `release` (minified + resource shrink + release signing), `performance` (minified, no resource shrink, debug signing)

### Desktop

```bash
# Run
./gradlew :composeApp:run

# Package for current OS
./gradlew :composeApp:packageDistributionForCurrentOS

# Platform-specific
./gradlew :composeApp:packageReleaseDmg    # macOS
./gradlew :composeApp:packageReleaseMsi    # Windows
./gradlew :composeApp:packageReleaseDeb    # Linux DEB
./gradlew :composeApp:packageReleaseRpm    # Linux RPM
./gradlew :composeApp:packageReleaseAppImage  # Linux AppImage
```

## Linting & Formatting

Spotless with ktlint enforces formatting on `**/*.kt` and `**/*.gradle.kts` (excluding `**/build/**`).

```bash
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Auto-fix formatting
```

CI auto-applies formatting on push to `main` and commits with `[skip ci]`. Fork PRs must run `spotlessApply` locally.

## Testing

```bash
./gradlew :composeApp:allTests       # All tests
./gradlew :composeApp:desktopTest    # Desktop tests only (used in CI)
./gradlew check                       # Lint + tests
```

**Test locations**:
- `composeApp/src/commonTest/` — Platform-agnostic unit tests
- `composeApp/src/desktopTest/` — Desktop-specific tests (integration, tools, UI)

**Test dependencies**: `kotlin-test`, `kotlinx-coroutines-test`, `Turbine` (Flow testing), `multiplatform-settings-test`

**Monkey testing**: `./scripts/monkey-test.sh [event_count] [throttle_ms]` — Android UI stress test via `adb shell monkey`

## Code Style

- **No hardcoded strings** — Use `composeResources/values/strings.xml` (Weblate-ready, 54 locales)
- **No hardcoded versions** — Always use `libs.versions.toml`
- **No gradients** — They're being removed; don't add new ones
- **Standard M3 APIs** — Use `ModalDrawerSheet`/`NavigationDrawerItem` directly, no custom wrappers
- **Material icons** — Prefer `material-icons-extended` over custom drawables
- **@Immutable** — Use on data classes used in Compose
- **Consistent style** — No wildcard/duplicate imports, follow idiomatic Kotlin

## Theme

- **Seed color**: `#5B8C5B` (dark pastel green)
- Uses `greenColorScheme(darkTheme)` via materialKolor `dynamicColorScheme`
- Dynamic color support (Material You on Android)
- Dark/light automatic via `isSystemInDarkTheme()`
- OLED black mode for pure black backgrounds
- Custom font family support (user + AI font selection)

## Commit & PR Conventions

- One logical change per commit
- Prefixes: `feat:`, `fix:`, `chore:`, `test:`, `style:`, `deps:`
- PR required for non-trivial changes; direct push allowed for tiny fixes
- Branches: `main` is the single long-lived branch; use `oak/**` prefix for feature branches (triggers build workflow)
- Focus reviews on functional correctness, logic, and architecture — no style nitpicks

## CI/CD Workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | Push/PR to `main`, weekly, manual | spotlessCheck, desktopTest, CodeQL |
| `pr-build.yml` | PR to main (after approval) | Build performance APKs |
| `oak-build.yml` | Push to `oak/**`, manual | Full build matrix (all flavors × build types + desktop) |
| `release.yml` | Push of `v*` tag | Build all platforms, create GitHub Release |
| `pr-checks.yml` | PR events | Auto-assign, label by files/size |
| `stale.yml` | Weekly | Mark stale issues (60d) and PRs (30d) |

## Key Dependencies

| Library | Purpose |
|---|---|
| Kotlin 2.3.21 | Language |
| Compose Multiplatform 1.11.1 | UI framework |
| Koin 4.2.2 | Dependency injection |
| Ktor 3.5.1 | HTTP client |
| kotlinx-serialization 1.11.0 | JSON serialization |
| materialKolor 5.0.0 | Dynamic theming |
| Coil 3.5.0 | Image loading |
| LiteRT LM 0.11.0 | On-device inference |
| Turbine 1.2.1 | Flow testing |

Full list: `gradle/libs.versions.toml`

## Useful Scripts

| Script | Purpose |
|---|---|
| `scripts/monkey-test.sh` | Android Monkey stress test |
| `build-proot.sh` | Cross-compile proot+talloc for Android sandbox |
| `.openhands/setup.sh` | Auto-setup for OpenHands sessions |

## Debugging Tips

- **Android logs**: `adb logcat | grep "oak"` or filter by `com.oak.app`
- **Desktop logs**: Check console output from `./gradlew :composeApp:run`
- **Network debugging**: Ktor logging plugin can be enabled in `RemoteDataRepository`
- **DI issues**: Verify all modules are registered in `AppModule.kt` and loaded via `startKoin`
- **Platform-specific code**: Check `expect` declarations in `commonMain` and their `actual` implementations in `androidMain`/`desktopMain`
