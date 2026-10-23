# MenetrendGO

Native Kotlin / Jetpack Compose Android journey planner and live vehicle tracker. Package: `hu.matepazy.menetrendgo`. Android 9+.

The Hungarian interface has **Tervezés**, **Térkép**, **Bérletek** and **Beállítások**. The search-first home stores recent stop pairs locally. Search opens a separate scrollable departures page; “További indulások” asks for later results (or earlier results for arrival searches). Open a result for individual legs, full service stop lists and its route on the map.

## Data connection

The Android app calls MÁVPlusz OTP GraphQL and station search directly over HTTPS. Query construction, normalization and 75-second vehicle/service caches run on the device, following the data architecture of [hovamegy.hu](https://github.com/matepazy/hovamegy.hu). No Python server, hosted proxy, endpoint configuration or computer connection is required. Internet is needed for fresh timetables, vehicles and OpenStreetMap tiles; this is not an offline timetable database.

The `backend/` Python prototype is retained as a reference, with its own tests. It is not used by the Android app.

## Build Android

Open the repository in Android Studio with JDK 21 and SDK 37 installed; Gradle downloads the declared dependencies. `local.properties` contains the local SDK path and is ignored.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Install the debug APK on an emulator or Android 9+ phone. It connects directly to MÁV over HTTPS. Previously saved proxy addresses are ignored. Beállítások → Fejlesztői beállítások contains read-only data-source information; normal settings contain appearance, local data and notifications.

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development build, not a Play Store release.

## Passes and reminders

Országbérlet and vármegyebérlet records stay in app-private storage; there is no account, analytics SDK, pass barcode or ticket wallet. Add the start and expiry dates printed on the pass. Stored records are not travel documents. Edit/delete is supported; deleting cancels the alarms.

Notifications are scheduled for 09:00 Budapest time three and one days before the final valid date, and the morning after expiry. Android notification permission is requested when a pass is saved. Alarms are inexact and may be delayed by Android power management. They are rescheduled after reboot; force-stopped apps must be reopened.

Date validity is evaluated at **both ends of every transit leg**. The app combines saved passes with operator acceptance and the complete route geometry against bundled public-domain county boundaries (including Budapest within Pest pass coverage). Missing geometry, border-adjacent routes, unsupported local services and international sections receive specific advice. Boundary data is generalized, so border-stop exceptions require official confirmation. Rail categories identify ordinary services usually usable in second class without a reservation; OTP does not expose exact carriage/segment reservation requirements. InterCity and other uncertain categories retain an explicit seat-reservation check. See the [official conditions](https://bkk.hu/jegyek-es-berletek/arak/orszagberlet-teljesaru/) and [boundary provenance](app/src/main/assets/hungary-counties-LICENSE.md).

## Live data and map

Vehicle and open-service screens refresh every 75 seconds while foregrounded. Backgrounding stops the app polling loop. Timetable results are snapshots; start a new search for new journey predictions. The device caches successful vehicle and service responses for 75 seconds. Failed refreshes keep the last good displayed snapshot and timestamp. Source positions may be older or unavailable; connection failures appear as transient messages and the vehicle drawer distinguishes live and scheduled arrival data. There is no simulated movement.

The map bundles [Leaflet 1.9.4](https://leafletjs.com/download.html) and loads OpenStreetMap tiles with visible attribution. The map fills the screen behind navigation, with zoom and a contextual recenter button. Country-scale positions are grouped into tappable count clusters; individual markers show the service color and vehicle icon. Tapping a vehicle opens a native bottom drawer with origin, destination and upcoming stops. The next stop is highlighted; a changed live arrival appears beneath its red struck-through scheduled time. The map requests optional location access on entry, centers once, and shows recenter only while the location is outside the unobscured viewport. Only the map is a WebView; the rest of the app is native Compose. Location permission is optional and coarse; coordinates stay on the device and center the map. Tile requests reveal the viewed region and IP address to the tile provider. No offline tile download or location history is implemented. Read the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) before a public launch at scale.

## Verification

```powershell
python -m unittest discover -s backend/tests -v
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Backend tests cover coordinate validation, invalid-plan rejection, Budapest timezone conversion, bounded station search, escaped/cached trip IDs and stale snapshots. Kotlin tests cover pass date boundaries, midnight expiry and unknown rail entitlement. Emulator checks and their limits are recorded in `.impeccable/verification.md`.

This app is unofficial and has no affiliation with MÁV. Live service availability depends on upstream public endpoints. No signing keys, production hosting credentials or fabricated timetables are included.
