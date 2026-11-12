# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MenetrendGO: a Hungarian-language native Android app (Kotlin, Jetpack Compose, Material 3; minSdk 28, compileSdk 37, targetSdk 36, JDK 21) for journey planning, live vehicle tracking, stop departure boards and pass-expiry tracking. UI strings, README and docs are in Hungarian; keep user-facing text Hungarian. There is no backend, no account system and no analytics. Everything is a single `:app` Gradle module in package `hu.matepazy.menetrendgo`.

## Commands

Windows uses `.\gradlew.bat`; on Linux/macOS use `sh ./gradlew`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug   # the standard check
./gradlew :app:testDebugUnitTest --tests "hu.matepazy.menetrendgo.PassRulesTest"            # one test class
./gradlew :app:testDebugUnitTest --tests "hu.matepazy.menetrendgo.PassRulesTest.someTest"   # one test method
./gradlew :app:installDebug              # install on device/emulator
./gradlew :app:connectedDebugAndroidTest # instrumented Compose UI tests (app/src/androidTest), needs a device/emulator
./gradlew :app:exportReleaseApk          # signed release APK -> artifacts/releases/menetrendgo-v<version>.apk
```

- Needs `sdk.dir` in `local.properties` or `ANDROID_HOME`. `start_emu.bat` starts an emulator.
- Release signing reads `signing/signing.properties` (see `signing.properties.example`); without it the release APK is unsigned. Release builds use R8, so reflection-read models (update manager) and WebView JS-bridge methods must stay in `app/proguard-rules.pro`.
- Releases: bump `versionCode`/`versionName` in `app/build.gradle.kts` and add release notes (`ReleaseNotes.kt`, `CHANGELOG.md`).
- Node scripts (no install step): `node scripts/refresh-fleet-photo-urls.mjs` re-verifies Wikimedia Commons photo URLs and licenses for the vehicle catalog; `tools/verify-vehicle-catalog.mjs` and `tools/research-local-buses.mjs` support the catalog at `app/src/main/assets/vehicle_models/catalog.json`. `python scripts/generate_branding.py` regenerates logos/icons from one source (stdlib only). `python scripts/generate_pictograms.py` (needs `fonttools` and `skia-pathops`) regenerates the app's own transit pictograms: `res/font/transit_pictograms.ttf` (route marks and service icons at the provider's MNR2007 code points), the `ic_mode_*` drawables and `assets/vehicle-icons.js`. Never bundle provider or operator artwork; edit the generator and regenerate.

## Architecture

- **Single `AppState` ViewModel** ([AppState.kt](app/src/main/java/hu/matepazy/menetrendgo/AppState.kt)) owns nearly all UI state as Compose `mutableStateOf`, persisted in SharedPreferences `menetrendgo` (recents, passes, settings, filters). It holds the `MavClient`, `StationSearch`, `UpdateController` and pass geography. [MainActivity.kt](app/src/main/java/hu/matepazy/menetrendgo/MainActivity.kt) hosts the screens; screens live in `PlanningScreens.kt`, `NearbyScreen.kt` (map), `UtilityScreens.kt`, `SettingsScreen.kt`, with drawers/bottom sheets (`*Drawer.kt`, `AppBottomSheet.kt`) for secondary flows.
- **Data source**: [MavClient.kt](app/src/main/java/hu/matepazy/menetrendgo/MavClient.kt) talks directly over HTTPS (OkHttp + org.json) to the MÁVPlusz OTP GraphQL and station-search endpoints. `MavQueries` builds the GraphQL strings (tested in `MavQueriesTest`); parsing into `Models.kt` types and normalization (`MavServices`, `ServiceNames`, `ServiceColors`, `RouteVehicles`) is separate from the transport. The query/parse shape follows the sibling project hovamegy.hu. Provider station identity must be preserved (platforms group into one station; street stops keep their provider IDs). Scheduled vs real-time data must stay visibly distinguishable.
- **Map**: Leaflet in a WebView. `app/src/main/assets/map.html`, `leaflet.js` and `vehicle-icons.js` are driven from Compose in `NearbyScreen.kt` by calling `window.*` JS functions through `evaluateJavascript` (`updateVehicles`, `updateRoute`, `updateStops`, `setMapType`, …); JS calls back through an `addJavascriptInterface` bridge. Changing either side of that contract requires updating both and the proguard keep rules. Pass county borders come from `hungary-counties.txt`.
- **Journey features**: `JourneyTransfers` (connection checks and alternative departures, including walking and boarding buffer), `JourneyAlerts` (provider service alerts), and `JourneyTracking` + `JourneyTrackingService` (opt-in arrival alert, a foreground location service gated behind a developer setting). Pure logic sits in separately testable files; UI counterparts are the `*Ui.kt` files.
- **Stop boards and widget**: `StopDepartures`, `StopBoardDrawer`, and the home-screen widget (`StopBoardWidget`, `WidgetRefresh`, `WidgetRowLayout`) share departure-row rendering and the colour semantics in `DepartureTheme`. The widget countdown works from stored times without network calls.
- **Passes**: `PassRules`, `PassDates`, `PassGeography`, `PassNotifications`; validity checks are informational, not entitlement proof.
- **Fleet info**: `VehicleModels`/`VehicleModelDrawer` read the bundled catalog; photos load lazily from Wikimedia Commons through `FleetPhotoStore` (LRU cache of at most 12 images / 12 MiB, 2 MiB per download).
- **Self-update**: `VersionUpdater` and `UpdateController` poll the public GitHub Releases of `matepazy/menetrend-go`; the user triggers the download and install.

## Conventions and tests

- Code is dense, compact Kotlin; match the surrounding style. Logic is kept in plain-Kotlin files so JVM tests in `app/src/test` can cover it (JUnit4 and `org.json`; sample data in `app/src/test/resources`). Compose UI behaviour is covered by instrumented tests in `app/src/androidTest`.
- Visual rules (colour tokens, type, components) are in [DESIGN.md](DESIGN.md); product scope and constraints are in [PRODUCT.md](PRODUCT.md). Preserve the existing design when adding features, and handle loading, empty and failed-request states.
- `.android-home`, `.gradle*`, `signing/`, `artifacts/` and the `*.log` files in the root are local or build artifacts, not source.
