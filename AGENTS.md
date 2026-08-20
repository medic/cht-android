# AGENTS.md

Guidance for AI coding agents (Claude Code, Codex, Cursor, Continue, etc.) working in this repo. Human contributors should also find this useful.

This file is the source of truth; `CLAUDE.md` defers to it.

## Project overview

`cht-android` is a thin Android WebView wrapper around the [CHT Core Framework](https://github.com/medic/cht-core/) web app. It ships as ~50 product flavors — each "brand" is a separate APK hardcoded to a specific CHT deployment, with partner branding. The native shell adds capabilities the web app can't reach from a plain browser: SMS, MRDT, file capture, geolocation, task notifications, and arbitrary external-app intents.

- Package: `org.medicmobile.webapp.mobile`
- Min SDK 21 (Android 5.0), target/compile SDK 35
- Java 17, Gradle 8.10.2 (wrapper), Android Gradle Plugin 8.8.2
- Source code is Java only (see `build.gradle` for the Kotlin BOM — used transitively by AndroidX, no Kotlin sources)

## MCP servers

AI agents working in this repo can query these MCP servers for additional context:

- **CHT Docs MCP** (Kapa.ai) — full CHT product docs Q&A and search. Indexes cht-android files, issues, and PRs alongside cht-core, cht-conf, and the docs site / forum. URL: `https://mcp-docs.dev.medicmobile.org/mcp`
- **OpenDeepWiki MCP** — code-level docs for individual Medic repos; each repo is a separate MCP endpoint, parameterised by query string:
  - `https://opendeepwiki.dev.medicmobile.org/api/mcp?owner=medic&name=cht-core`
  - `https://opendeepwiki.dev.medicmobile.org/api/mcp?owner=medic&name=cht-conf`
  - `https://opendeepwiki.dev.medicmobile.org/api/mcp?owner=medic&name=cht-watchdog`

Note: no OpenDeepWiki index exists for `cht-android` — for code questions about this repo, read the source directly or query the Kapa docs MCP (which does index cht-android files, issues, and PRs).

## Setup

1. Install the Android SDK (cmdline-tools, platform-tools, an emulator image at API 34 for instrumentation tests).
2. Set `sdk.dir` in `local.properties` (or `ANDROID_HOME`).
3. Use JDK 17 (CI uses Adopt 17). Higher JDKs are not tested.
4. After clone: `git submodule update --init --recursive` (bats test helpers under `src/test/bash/`).

## Common commands

The `Makefile` is the canonical entry point — prefer it over raw Gradle.

| Task | Command |
|------|---------|
| Install a flavor onto a connected device | `make deploy flavor=Unbranded` (capitalize first letter) |
| Build all release APKs | `make assemble-all` (needs signing env) |
| Build all debug APKs | `make assemble-all-debug` |
| Run unit tests (incl. lint) | `make test` |
| Run a single unit test | `./gradlew testUnbrandedDebugUnitTest --tests '*UrlHandlerTest.someMethod'` |
| Run lint only | `make lint flavor=Unbranded` (runs pmd + checkstyle + spotbugs + Android lint) |
| Run instrumentation tests | `make test-ui` (unbranded) or `make test-ui-gamma` (live login flow against gamma) |
| Bash keystore tests | `make test-bash-keystore` |
| Coverage report | `make test-coverage` |
| Logs from device | `make logs` |

Lint is strict: `warningsAsErrors true`, `abortOnError true` on CI. Fix the underlying issue rather than suppressing.

## Project layout

```
src/
  main/java/org/medicmobile/webapp/mobile/   # all shared Java code
  main/res/                                   # default resources (strings, layouts, instances.xml)
  main/AndroidManifest.xml                    # base manifest (activities, permissions)
  <brand>/                                    # per-flavor overrides: usually just res/values/strings.xml + launcher icons
  <brand>/AndroidManifest.xml                 # optional manifest overlay (e.g. tools:node="remove" a permission)
  test/java/...                               # Robolectric + JUnit unit tests
  test/bash/                                  # bats tests for keystore make targets
  androidTestUnbrandedDebug/                  # Espresso instrumentation, unbranded
  androidTestMedicmobilegammaDebug/           # Espresso-web E2E, points at the gamma server
build.gradle                                  # all flavors declared in productFlavors {}
config/                                       # checkstyle.xml, pmd.xml, lint.xml
fastlane/Fastfile                             # Play Store alpha-track upload
secrets/                                      # encrypted per-org keystores (.tar.gz.enc)
```

## Architecture cheatsheet

Activity flow:
- `StartupActivity` → free-space check → either `EmbeddedBrowserActivity` (if a server URL is configured) or `SettingsDialogActivity`. On Android 12+ it also triggers `DomainVerificationActivity` for app links.
- `EmbeddedBrowserActivity` hosts the WebView. Wires up `UrlHandler` (WebViewClient), `MedicAndroidJavascript` (JS bridge), `FilePickerHandler`, `MrdtSupport`, `SmsSender`, `ChtExternalAppHandler`, `AppNotificationManager`, and runs `XWalkMigration` once on first launch.
- `SettingsDialogActivity` shows the server picker (`res/xml/instances.xml`) and a custom URL form, validated by `AppUrlVerifier` against `/setup/poll`.
- The hidden settings gesture (5 taps + 2-finger right-swipe) is implemented in `OpenSettingsDialogFragment` + `GestureHandler`.

The JS↔Java contract:
- Java exposes `medicmobile_android` to the WebView (`MedicAndroidJavascript`). Methods: `getAppVersion`, `playAlert`, `getDataUsage`, `getLocationPermissions`, `datePicker`, `mrdt_available`/`mrdt_verify`, `updateTaskNotificationStore`, `sms_available`/`sms_send`, `launchExternalApp`, `getDeviceInfo`.
- Java calls into the webapp via `window.CHTCore.AndroidApi.v1.*` (and a legacy AngularJS path: `angular.element(document.body).injector().get('AndroidApi')`).
- Any change to either side must be coordinated with `cht-core`.

Settings storage:
- `BrandedSettingsStore` derives the URL from `R.string.scheme` + `R.string.app_host` resources — no UI to change it.
- `UnbrandedSettingsStore` reads `app-url` from `SharedPreferences` and exposes the settings form.
- Last-loaded URL is cached for 24 h (`TTL_LAST_URL` in `build.gradle`).

## Adding a new brand

The end-to-end recipe (folder layout under `src/<brand>/`, `strings.xml` overrides, `build.gradle` flavor registration, keystore generation) is documented at <https://docs.communityhealthtoolkit.org/building/branding/android/>. Follow that page.

Two repo-specific notes the docs don't cover:

- **Training apps** — inside the flavor's block in `build.gradle`, set `buildConfigField "boolean", "IS_TRAINING_APP", 'true'`. This enables the orange "training app" border in `EmbeddedBrowserActivity`. See existing flavors `medicmobilegamma_training`, `moh_mali_chw_training_2`, `moh_mali_chw_training_three`.
- **Manifest overlays** — add `src/<brand>/AndroidManifest.xml` for surgical per-brand manifest edits via `xmlns:tools` + `tools:node`. Example: `src/lumbini_ne/AndroidManifest.xml` uses `tools:node="remove"` to drop the `READ_EXTERNAL_STORAGE` permission.

## Tests

- Unit tests use Robolectric, JUnit 4, Mockito (`mockito-inline`), and `androidx.work:work-testing`. They live in `src/test/` and run on the JVM via `make test`.
- Instrumentation tests use Espresso + `espresso-web`. `LoginTests` actually logs into the live gamma server, so it's gated to `medicmobilegammaDebug`.
- Bash tests for `make keygen`/`keydec` use bats (vendored as submodules).
- CI: `.github/workflows/build.yml` runs `make test` + `make test-bash-keystore` + `make test-ui` + `make test-ui-gamma` on every push/PR.

When adding a Java class, add a Robolectric test alongside in the matching package — coverage is tracked via `coverage.gradle`.

## Commits, branches, PRs

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) with the GitHub issue number in the type's scope:

```
feat(#1234): short description
fix(#1234): short description
chore(#1234): short description
```

Trailing PR numbers (`(#427)`) are appended automatically by the squash merge — don't write them by hand.

- **Default branch:** `master`. Open PRs against `master`.
- **Branch naming:** `<issue-number>-short-description` (hyphens or underscores both appear in history).
- **Merge strategy:** squash and merge. PR titles should match the Conventional Commits format above so the squashed commit is well-formed.
- CI (`.github/workflows/build.yml`) runs `make test` + `make test-bash-keystore` + `make test-ui` + `make test-ui-gamma` on every push and PR. All must pass before merge.

## Conventions

- Java only. No Kotlin sources. Keep it that way unless the team decides otherwise.
- Tabs (size 4) for Java sources and the Makefile; 2-space indentation everywhere else (see `.editorconfig`).
- Static imports for `MedicLog.{trace,log,warn,error}` and the helper utilities — match the existing style.
- Logging goes through `MedicLog`; `trace` is debug-only, `log/warn/error` are unconditional.
- Redact URLs that may contain credentials with `SimpleJsonClient2.redactUrl(...)` before logging.
- Don't disable lint rules globally to silence a warning — fix the code or add a narrow `@SuppressWarnings`/`tools:ignore`.
- The `unbranded` flavor uses `src/main` only and is the default for development. Most refactors should be verified against it first.
- Per-brand changes belong in `src/<brand>/`, never in `src/main/`. If you find yourself special-casing a brand inside main code, that's a smell.

## Releases & secrets

- Versioning is driven by `RELEASE_VERSION` env var (set in CI when a `v*` tag is pushed) — see `getVersionCode` in `build.gradle`.
- Release publishing flow lives in `.github/workflows/publish.yml` + `fastlane/Fastfile` (Play Store alpha track).
- Per-brand keystores are encrypted with AES-256-CBC and stored under `secrets/`. The Make targets (`keygen`, `keydec`, `keyrm`) handle creation and unpacking — never commit unencrypted keystores.
- The `pepk.jar` flow uploads the private key to Google Play in encrypted form.

## Further reading

External docs (don't duplicate these here — they evolve):

- CHT Architecture overview: <https://docs.communityhealthtoolkit.org/technical-overview/architecture/cht-android/>
- Building & branding: <https://docs.communityhealthtoolkit.org/building/branding/android/>
- Android integrations (deep links, intents): <https://docs.communityhealthtoolkit.org/building/integrations/android/>
- Releasing: <https://docs.communityhealthtoolkit.org/building/branding/publishing/>
- Forum: <https://forum.communityhealthtoolkit.org/>

When something here conflicts with the docs site, the docs site wins for product/user-facing concerns; this file wins for repo conventions.
