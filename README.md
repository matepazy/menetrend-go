# MenetrendGO

Native Kotlin / Jetpack Compose Android journey planner and live vehicle tracker. Package: `hu.matepazy.menetrendgo`. Android 9+.

The Hungarian interface has **Tervezés**, **Térkép**, **Bérletek** and **Beállítások**. The search-first home stores recent stop pairs locally. Search opens a separate scrollable departures page; “További indulások” asks for later results (or earlier results for arrival searches). Open a result for individual legs and full service stop lists. “Útvonal a térképen” opens a dedicated map of the selected journey, with only its route and matching live vehicles, when available. Back returns to the journey details; the Térkép tab remains the separate all-vehicle map.

## Data connection

The Android app calls MÁVPlusz OTP GraphQL and station search directly over HTTPS. Query construction, normalization and 75-second vehicle/service caches run on the device, following the data architecture of [hovamegy.hu](https://github.com/matepazy/hovamegy.hu). No Python server, hosted proxy, endpoint configuration or computer connection is required. Internet is needed for fresh timetables, vehicles and the selected provider's map tiles; this is not an offline timetable database.

The `backend/` Python prototype is retained as a reference, with its own tests. It is not used by the Android app.

## Build Android

Open the repository in Android Studio with JDK 21 and SDK 37 installed; Gradle downloads the declared dependencies. `local.properties` contains the local SDK path and is ignored.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Install the debug APK on an emulator or Android 9+ phone. It connects directly to MÁV over HTTPS. Previously saved proxy addresses are ignored. Beállítások → Az alkalmazásról → Fejlesztői beállítások contains developer options, including Saját térkép URL; normal settings contain appearance, local data and notifications.

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development build, not a Play Store release.

## Passes and reminders

Országbérlet and vármegyebérlet records stay in app-private storage; there is no account, analytics SDK, pass barcode or ticket wallet. Add the start and expiry dates printed on the pass. Stored records are not travel documents. Edit/delete is supported; deleting cancels the alarms.

Notifications are scheduled for 09:00 Budapest time three and one days before the final valid date, and the morning after expiry. Android notification permission is requested when a pass is saved. Alarms are inexact and may be delayed by Android power management. They are rescheduled after reboot; force-stopped apps must be reopened.

Date validity is evaluated at **both ends of every transit leg**. The app combines saved passes with operator acceptance and the complete route geometry against bundled public-domain county boundaries (including Budapest within Pest pass coverage). Missing geometry, border-adjacent routes, unsupported local services and international sections receive specific advice. Boundary data is generalized, so border-stop exceptions require official confirmation. Rail categories identify ordinary services usually usable in second class without a reservation; OTP does not expose exact carriage/segment reservation requirements. InterCity and other uncertain categories retain an explicit seat-reservation check. See the [official conditions](https://bkk.hu/jegyek-es-berletek/arak/orszagberlet-teljesaru/) and [boundary provenance](app/src/main/assets/hungary-counties-LICENSE.md).

## Live data and map

Vehicle and open-service screens refresh every 75 seconds while foregrounded. Backgrounding stops the app polling loop. Timetable results are snapshots; start a new search for new journey predictions. Departure cards and journey details show a changed scheduled time in red with a strikethrough, followed by the live estimate below. Departure and arrival delays are handled independently, including midnight crossings. Unchanged or scheduled-only times occupy one line. The device caches successful vehicle and service responses for 75 seconds. Failed refreshes keep the last good displayed snapshot and timestamp. Source positions may be older or unavailable; connection failures appear as transient messages and the vehicle drawer distinguishes live and scheduled arrival data. There is no simulated movement.

The map bundles [Leaflet 1.9.4](https://leafletjs.com/download.html) and offers locally remembered Alap (OpenStreetMap), Műhold (Esri imagery), Hibrid (Esri imagery, roads and labels) and Vasúti (OpenStreetMap with OpenRailwayMap) sources with visible attribution. The Layers button below search opens a native map-type drawer. Enabling Saját térkép URL in developer settings reveals an HTTPS XYZ raster tile editor with required {z}, {x}, {y} and optional {s}, {r} placeholders. Applying a valid URL saves and selects it locally. Disabling the option while a custom source is active returns to Alap and retains the saved URL. Map-type changes preserve the current center, zoom, routes and vehicle markers. The map fills the screen behind navigation, with zoom and a contextual recenter button. Country-scale positions are grouped into tappable count clusters; individual markers show the service color and vehicle icon, with a pointed edge indicating the provider heading when available. Vehicle markers have no long-press tooltip. Tapping a vehicle opens a native bottom drawer with origin, destination and upcoming stops, plus the provider's vehicle model, license plate and rail UIC number when supplied. The next stop is highlighted; a changed live arrival appears beneath its red struck-through scheduled time. The map requests optional location access on entry, centers once, and shows recenter only while the location is outside the unobscured viewport. Only the map is a WebView; the rest of the app is native Compose. Location permission is optional and coarse; coordinates stay on the device and center the map. Tile requests reveal the viewed region and IP address to the tile provider. No offline tile download or location history is implemented. Read the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) before a public launch at scale.

## App updates

Beállítások → Frissítések offers startup checks enabled by default, manual checking, stable/beta selection, release notes, APK download progress, and version management. Its row is directly below Adatok és előzmények as the last row in the main Settings page's top group; About remains in its separate group. Users can turn automatic checking off. Checks query the public `matepazy/menetrend-go` GitHub Releases API without credentials. GitHub receives normal connection metadata; no passes, searches or location are sent. Downloads and Android installation require user action. Dismissed updates are deferred for 24 hours, including across restarts. Automatic checking runs when the app starts, with an in-process reminder while it remains open; there is no background polling service.

Publish semver-tagged GitHub releases (`v1.0.1`, `v1.1.0-beta.1`), mark beta releases as prereleases, and attach a signed `menetrendgo-v1.0.1.apk`. The updater prefers this filename and retains Still's generic `.apk` fallback. Increase both `versionName` and `versionCode` before release, and retain the same application ID and signing key. Android can reject downgrades or incompatible signatures. A beta channel includes stable releases too; the highest semantic version wins.

```powershell
./gradlew.bat :app:exportReleaseApk
```

This builds the release APK and copies it into `artifacts/releases/menetrendgo-v<version>.apk`. Signing uses the existing ignored local configuration. Public releases must be available for update discovery; no GitHub token belongs in the APK.

For UI verification without replacing an existing signed installation, build `:app:assembleDebug -PupdatePreview` and install the debug APK. This uses the separate `hu.matepazy.menetrendgo.updatepreview` package. Its debug-only `UpdatePreviewActivity` displays an explicitly synthetic beta release with an intentionally failing download URL. Normal builds never use the fixture, and release APKs omit that activity.

## Verification

```powershell
python -m unittest discover -s backend/tests -v
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Backend tests cover coordinate validation, invalid-plan rejection, Budapest timezone conversion, bounded station search, escaped/cached trip IDs and stale snapshots. Kotlin tests cover pass date boundaries, midnight expiry and unknown rail entitlement. Emulator checks and their limits are recorded in `.impeccable/verification.md`.

This app is unofficial and has no affiliation with MÁV. Live service availability depends on upstream public endpoints. No signing keys, production hosting credentials or fabricated timetables are included.

The service timetable’s “Jármű a térképen” action opens a separate map containing only that exact trip’s live vehicle, without the main navigation or journey route. Back returns to the same timetable. Missing positions are shown explicitly; unrelated vehicles never act as a fallback.
