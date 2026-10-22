# MenetrendGO

Native Kotlin / Jetpack Compose Android journey planner and live vehicle tracker. Package: `hu.matepazy.menetrendgo`. Android 9+.

The Hungarian interface has **Tervezés**, **Térkép**, **Bérletek** and **Beállítások**. The search-first home stores recent stop pairs locally. Search opens a separate scrollable departures page; “További indulások” asks for later results (or earlier results for arrival searches). Open a result for individual legs, full service stop lists and its route on the map.

## Run the backend

The Python/Flask proxy follows [hovamegy.hu](https://github.com/matepazy/hovamegy.hu)'s architecture and uses the MÁVPlusz OTP GraphQL and station-search endpoints. It is an independent backend implementation, with a shared 75-second vehicle snapshot and on-demand service-detail caching. Failed refreshes retain the last good snapshot and timestamp.

```powershell
cd backend
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/python server.py
```

Or run `docker build -t menetrendgo backend` and `docker run --rm -p 8000:8000 menetrendgo`. Use one server process; its polling thread must not be duplicated across multiple workers. Public deployments need HTTPS and reverse-proxy request limits. No backend has been deployed by this project.

Endpoints: `GET /health`, `GET /api/stations?q=...`, `POST /api/plan`, `GET /live_data.json`, `GET /api/trip?id=...&date=YYYY-MM-DD`. Server timestamps are epoch seconds; journey times are epoch milliseconds. Service stop times are seconds from the service-day midnight in Europe/Budapest, including values above 86400.

## Build Android

Open the repository in Android Studio with JDK 21 and SDK 37 installed; Gradle downloads the declared dependencies. `local.properties` contains the local SDK path and is ignored.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Debug defaults to `http://10.0.2.2:8000`, the Android emulator's host address. A physical device needs a reachable HTTPS server. Set its address in **Beállítások → Adatkapcsolat**, or build with `-PapiUrl=https://your-server.example`. Release builds have no default server and reject cleartext HTTP. Local HTTP is allowed only for emulator/loopback hosts in debug.

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development build, not a Play Store release.

## Passes and reminders

Országbérlet and vármegyebérlet records stay in app-private storage; there is no account, analytics SDK, pass barcode or ticket wallet. Add the start and expiry dates printed on the pass. Stored records are not travel documents. Edit/delete is supported; deleting cancels the alarms.

Notifications are scheduled for 09:00 Budapest time three and one days before the final valid date, and the morning after expiry. Android notification permission is requested when a pass is saved. Alarms are inexact and may be delayed by Android power management. They are rescheduled after reboot; force-stopped apps must be reopened.

Date validity is evaluated at **both ends of every transit leg**. Service entitlement is advisory: the OTP fields used here do not provide authoritative county boundaries, reservation segments, carriage class or local acceptance. The UI explicitly reports uncertainty rather than claiming that an unexpired pass covers the exact service. Complete automatic service eligibility remains a limitation; see the [official conditions](https://www.ujtarifa.hu/orszagberlet).

## Live data and map

Vehicle and open-service screens refresh every 75 seconds while foregrounded. Backgrounding stops the app polling loop. Timetable results are snapshots; start a new search for new journey predictions. The server refreshes its shared vehicle snapshot every 75 seconds. Source positions may be older or unavailable; the app marks old snapshots after three minutes. There is no simulated movement.

The map bundles [Leaflet 1.9.4](https://leafletjs.com/download.html) and loads OpenStreetMap tiles with visible attribution. Country-scale positions are grouped into tappable count clusters; zoom in for individual service labels. Only the map is a WebView; the rest of the app is native Compose. Location permission is optional and coarse; coordinates stay on the device and center the map. Tile requests reveal the viewed region and IP address to the tile provider. No offline tile download or location history is implemented. Read the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) before a public launch at scale.

## Verification

```powershell
python -m unittest discover -s backend/tests -v
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Backend tests cover coordinate validation, invalid-plan rejection, Budapest timezone conversion, bounded station search, escaped/cached trip IDs and stale snapshots. Kotlin tests cover pass date boundaries, midnight expiry and unknown rail entitlement. Emulator checks and their limits are recorded in `.impeccable/verification.md`.

This app is unofficial and has no affiliation with MÁV. Live service availability depends on upstream public endpoints. No signing keys, production hosting credentials or fabricated timetables are included.
