# Oak — AGENTS.md

*Last updated: 2026-05-19*

## What is Oak

Open-source AI assistant with persistent memory. Android-first, BYOK (bring your own API key). Nature + productivity theme, dark pastel green aesthetic. Built with Kotlin Multiplatform + Compose Multiplatform (Android + Desktop JVM). Package `com.oak.app`, GPLv3.

Repo: [github.com/adrielGGmotion/Oak](https://github.com/adrielGGmotion/Oak) — standalone project, not a fork.

**Stack**: Kotlin 2.3.21, Compose 1.10.3, Min SDK 26, Target SDK 36.

## Repo at a glance

```
Oak/
├── androidApp/                     # Android entry point (thin shell)
│   └── src/main/kotlin/com/oak/app/
├── composeApp/src/
│   ├── commonMain/kotlin/com/oak/app/   # All logic: UI, data, tools, inference
│   ├── androidMain/                      # Android specifics (sandbox, notifications, SMS)
│   ├── desktopMain/                      # Desktop specifics (ProcessBuilder, tray)
│   └── jvmShared/                        # Shared JVM code (LiteRT inference engine)
├── gradle/libs.versions.toml            # All deps — never hardcode versions
├── scripts/
└── .github/workflows/
```

## Architecture

`androidApp` is the thin Android shell. All code lives in `composeApp`. Koin DI in `AppModule.kt`. Data flows: UI (Compose) → ViewModel → DataRepository → platform actuals (expect/actual). Network via Ktor + kotlinx.serialization. LLM inference via OpenAI-compatible, Anthropic, or Gemini clients with auto-fallback.

## Theme

**Seed color**: `#5B8C5B` (dark pastel green). Uses `greenColorScheme(darkTheme)` / `dynamicColorScheme` from materialKolor. Dark/light automatic via `isSystemInDarkTheme`.

## Auto-setup (OpenHands)

The `.openhands/setup.sh` script runs automatically when an OpenHands session
starts. It installs:
- **JDK 21** (Temurin) — required to build
- **Android SDK** (platform 36 + build-tools) — only when missing
- **Gradle cache pre-warm** — downloads desktop dependencies so builds are fast

Manual equivalent:
```bash
sudo bash .openhands/setup.sh
```

## Build commands

```bash
# Android debug APK
./gradlew :androidApp:assembleDebug

# Desktop run
./gradlew :composeApp:run

# Desktop distribution
./gradlew :composeApp:packageDistributionForCurrentOS

# All tests
./gradlew :composeApp:allTests

# Check (lint + tests)
./gradlew check
```

## Platform differences

| Android | Desktop |
|---|---|
| Proot sandbox for command execution | Shell commands via ProcessBuilder |
| Notification listener, SMS access | File system access, Swing Tray |
| LiteRT on-device inference | LiteRT inference engine shared via jvmShared |
| Keystore-encrypted prefs | Plain file storage |

Neither platform has the other's capabilities.

## Rules

- **Kotlin best practices** — consistent style, no wildcard/duplicate imports, follow idioms
- **No hardcoded strings** — use `composeResources/values/strings.xml` (Weblate-ready)
- **No hardcoded versions** — always use `libs.versions.toml`
- **Standard M3 APIs** — use `ModalDrawerSheet`/`NavigationDrawerItem` directly, no custom wrappers
- **Material icons** — prefer `material-icons-extended` over custom drawables
- **No gradients** — they're being removed, don't add new ones
- **Commit discipline** — one logical change per commit, loose prefixes (`feat:`/`fix:`/`chore:`/`test:`)
- **PR required** for non-trivial changes
- **Review quality** — focus on functional correctness, logic, and architecture. Don't leave style nitpicks or subjective formatting comments.
- **Only commit/push when told** — don't autonomously push changes
- **Feature requests** — use the issue template, plan before coding

## Dynamic UI

AI-generated interactive UI blocks use the `oak-ui` fenced block. Rendered by `OakUiNode` → `OakUiRenderer`.
