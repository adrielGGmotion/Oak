# SDK Management

When adding or updating dependencies, always use the version catalog (`gradle/libs.versions.toml`).
Do NOT hardcode version strings in `build.gradle.kts` files.

# Naming Conventions

This project was renamed from **Kai → Beer**. All code, assets, and documentation should use "Beer" branding.

- **Dynamic UI language**: Use `beer-ui` (not `kai-ui`) as the code fence identifier
- **Compose components**: Use `Beer` prefix (`BeerChip`, `BeerSlider`, etc.) instead of `Kai`
- **Generated resources**: Live under `beer.composeapp.generated.resources` (tied to `rootProject.name` in `settings.gradle.kts`)
- **User-Agent strings**: Use `Beer/<version>` and `Beer/1.0`
- **Window/tray titles**: Use "Beer", not "Kai 9000" or "Kai"

Avoid introducing new "Kai" references. If you encounter leftover ones, rename them to "Beer".

# Removed Platforms

The following platforms/targets have been removed:
- **iOS** (`iosApp/`, `composeApp/src/iosMain/`, iOS targets in build.gradle.kts)
- **Web/WASM** (`composeApp/src/wasmJsMain/`, wasmJs target)
- **Website** (`site/`, `docs/`, `mkdocs.yml`)
- **Packaging**: flatpak, AUR, fastlane/Play Store, WinGet

Remaining targets: **Android** and **Desktop (JVM)** only.
