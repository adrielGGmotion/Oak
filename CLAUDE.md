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

## Build secrets

| Secret | Purpose |
|---|---|
| `DEBUG_KEYSTORE_BASE64` | Base64 of `debug.keystore` — decoded by Gradle for consistent debug signatures |
| `KEYSTORE_B64` | Base64 of the release keystore |
| `KEYSTORE_PASSWORD` | Password for the release keystore and key |
| `KEY_ALIAS` | Alias of the release key inside the keystore |

Keystore files (`debug.keystore`, `release.keystore`) are never committed — they're decoded from secrets in CI or generated locally.

## Repo Configuration (added May 14)
- **Dependabot**: `.github/dependabot.yml` — weekly checks for Gradle + GH Actions deps
- **CodeQL**: `.github/workflows/codeql.yml` — static analysis on main + PRs + weekly cron
- **Stale bot**: `.github/workflows/stale.yml` — issues stale at 60d, PRs at 30d, close at 7d
- **SECURITY.md**: `.github/SECURITY.md` — report vulnerabilities via private advisory
- **FUNDING.yml**: Updated to adrielGGmotion
- **CONTRIBUTING.md**: Workflow guidelines
- **Feature request template**: `.github/ISSUE_TEMPLATE/feature_request.yml`

## PR Tooling (added May 14)
- **PR Template**: `.github/PULL_REQUEST_TEMPLATE/pull_request_template.md` — checklist for every PR
- **Auto-labeler**: `.github/workflows/labeler.yml` + `.github/labeler.yml` — tags PRs by area (android, ci, docs, deps, kotlin)
- **PR Title Check**: `.github/workflows/pr-title.yml` — enforces conventional commits format (`feat:`, `fix:`, `chore:`, etc.)
- **Release Please**: `.github/workflows/release-please.yml` — auto-creates releases from conventional commits on main
