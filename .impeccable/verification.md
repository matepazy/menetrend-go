# MenetrendGO verification

## Current revision: standalone Android client

This section supersedes the earlier proxy-build evidence and hash below. The app now calls fixed MÁVPlusz HTTPS endpoints directly through MavClient.kt; query normalization and 75-second caches run on the device. Saved proxy URLs are ignored, API_URL configuration and debug cleartext exceptions are removed. The Python reference backend is not used and no process was listening on port 8000 during these runtime checks.

- Final assembleDebug and lintDebug succeeded. Nine Android JVM tests passed (five pass/date tests and four query validation/escaping/date tests).
- Updated emulator-5554 in place with Android Studio's existing debug key; no uninstall or saved-data deletion. The initial mismatched-key update was rejected; its screenshot was replaced after successful install.
- Direct MÁV vehicle positions and visible map clusters loaded. Direct station autocomplete returned Mór and Székesfehérvár. A real journey search returned next-day 04:23, 06:13 and 06:44 options; opening 8096 loaded its complete stop list.
- Raw captures: standalone-home.png, standalone-map.png, standalone-settings.png, standalone-developer.png, standalone-results.png, standalone-stops.png and standalone-large-font.png. Planning wordmark removed; navigation highlights the full icon/label group. Developer source information is nested under developer settings. Larger-font dark mode inspected at scale 1.3; scale restored to 1.0 and theme to system afterward.
- The prior independent ship verdict covers only the prior revision. This narrow revision was verified with a bounded native runtime inspection; no new independent ship verdict is claimed. DESIGN.md and its sidecar were refreshed by the documenter.
- Internet remains required for MÁV data and OpenStreetMap tiles. No offline timetable database, future-day notification delivery or hardware GPS/performance is claimed.

Final standalone debug APK SHA-256: `5ebdb92a48b060ae964f7cafeee0b215b67c162073c3a0064641ccd478b23b8d`.

## Earlier proxy-build history


## Completed evidence

- Debug APK assembled; Kotlin unit tests and Android lint passed with no lint errors. Remaining lint warnings concern dependency versions/platform recommendations and non-fatal code recommendations.
- Seven backend tests pass: bad plan rejection, finite coordinate validation, Budapest timezone conversion, bounded station query, escaped/cached trip IDs, provider HTML/placeholder normalization, and stale snapshot retention.
- Five Kotlin tests pass: local-date expiry, an overnight journey crossing expiry, unconfirmed rail entitlement, explicit next-day labeling, and an unverified bus territory remaining unknown.
- Real MÁV requests returned station autocomplete, journey plans, vehicle positions and full service stop lists. No timetable or motion was fabricated.
- Actual Android emulator captures are in `.impeccable/review/`. Phone: Android 37.1, 1080×2400, density 420. Expanded layout: same emulator at 1600×2560, density 240; this is an expanded emulator viewport, not a physical tablet. Text-scale check: 1.3, restored to 1.0 afterward.
- A real Mór–Székesfehérvár search opens the separate results page. Additional-result loading produced later departures (06:44, 07:13, 07:44 in that check). Journey cards include “Holnap” for the overnight result set.
- Real full stop lists, planned route geometry, live map markers, opening a live service from a marker, and following that vehicle were exercised. One checked map had a 379×588 CSS-pixel viewport, eight loaded tiles and eighteen rendered vehicle markers.
- Pass saving and recent-search persistence were exercised through the app UI. Android reported three future pass alarms; force-stop and reopen restored all three. This verifies registration/restoration, not actual future-day delivery timing.
- Light/dark, 1.3 text scale, expanded navigation rail, Hungarian date/time dialogs and autocomplete were captured.

## Review status

The full independent review returned `fix` for country-map label density, map targets/large-font navigation, default lavender dialog surfaces, and the missing hero checkpoint. One correction batch has landed. It groups map vehicles in 64-CSS-pixel screen cells with 48-pixel targets, insets attribution, themes Material container roles in both schemes and gives navigation labels room at large text sizes. The final APK was rebuilt successfully with lint and unit tests; the same screenshot paths were recaptured. The first verdict scored all four fixes resolved and found a new map-pill containment regression. A second batch made the pill nonshrinking and content-sized; the same local-map screenshot was recaptured. The final verdict in `finish-verdict.md` is `ship`, covering the scored fixes and that regression only, all resolved. It is not a new whole-surface audit.

`hero-repro.png` is a direct raw emulator capture at the approved comp dimensions (841×1870, density 312), with the approved real-data/native-control/separate-results adaptations. Phone dimensions were restored to 1080×2400, density 420, font scale 1.0. WebView inspection confirmed the actual zoom target height is 48 CSS pixels. The source records the earlier seed 1b242c78, but no separate original roll artifact survives; the user-approved concept and brief remain the authority.

## Honest limits

- No production backend was deployed. Debug defaults to the local development server; release configuration requires a reachable HTTPS URL.
- Complete service-specific pass entitlement is not available from the integrated OTP fields. Date coverage is checked at both ends of every transit leg; county coverage, local exceptions, class and reservation restrictions are not guessed. The app displays uncertainty explicitly.
- Actual scheduled-day notification delivery, reboot behavior on physical hardware, OEM power management, real GPS position acquisition and device performance were not proven by these checks.
- Timetable results are provider-generated journey options, extended with pagination; no claim of exhaustive enumeration of every theoretically possible route is made.
- The first inspection exposed blank WebView sizing, unthemed dark headings and stale notification-permission presentation. These were corrected and confirmed with actual subsequent captures. The QA helper now rejects failed accessibility dumps rather than reusing stale XML; the invalid initial country-map capture was replaced.

## Assets

The app ships native Compose UI, Material vector icons, a VectorDrawable launcher/notification icon, and bundled Leaflet JavaScript/CSS/license. Generated concept images remain design references only and have prompt/approval sidecars; the asset manifest records that no generated raster is shipped as UI.

## Final build

Final debug APK SHA-256: `47855a075d3322905357b096cbaebc2298230b912c07bf1871f69214a231489e`. Final assembleDebug and lintDebug passed after the label correction. Kotlin source is unchanged since the passing five-test run; the seven backend tests also passed.

The task-created headless QA emulator was stopped and its exact D:/Temp/MenetrendGO-QA-01a164d3 directory removed after the final captures. The separate user emulator and installed SDK were retained. The local test backend was stopped; use README instructions to start it.

The fresh Impeccable documenter recorded the final built system in DESIGN.md and .impeccable/design.json. Its sidecar previews are illustrative documentation, not shipped Android controls. The surface brief is bound to the native source files and map asset.

## Floating navigation backing fix

Removed the reserved bottom viewport strip on scrollable pages. Lists draw under the rounded navigation surface and retain its measured height as trailing content padding. The map keeps safe control bounds. assembleDebug passed; installed in place on emulator-5554 and inspected a real departure list in floating-nav-results.png. This is a bounded refinement check, not a new independent whole-app verdict.

## Journey card and itinerary refinement (2026-10-22)

- Cards now show top-left service numbers and mode icons on provider route colors; equally bold endpoint times with names underneath and an arrow; scheduled-time boilerplate removed. Query/model retain route.color, trainCategoryName and tripHeadsign. Direct MÁVPlusz OTP readback confirmed COACH 8096/8097/1846 colors F6AC00 and category helyközi busz. Official context: https://www.mavcsoport.hu/mav-csoport/minden-eddiginel-tobb-utastajekoztatasi-funkcioval-erkezik-mavplusz-jaratinfo .
- Journey details use a connected timeline, dotted walking, colored transit, directions, transfer waits, day transitions, inline intermediate stops and the existing full-service timetable action. Shared same-time stops are condensed. A final source correction keeps time-column width consistent under font scaling.
- assembleDebug, testDebugUnitTest and lintDebug passed. Existing nine JVM tests pass; no new UI automation suite is claimed. Layout detector reports no findings.
- Updated emulator-5554 in place. Real saved Mór–Székesfehérvár search showed the new cards; opened 8096 itinerary and expanded its 16 intermediate stops, reading back real stop names. Inspected light phone results, dark 1.3 font-scale phone itinerary, and expanded 1600x2560/density 240 results/itinerary. Native dimensions 1280x2856/density 480, font 1.0 and system theme restored afterward. Evidence in .impeccable/review/: cards-results.png, final-results-expanded.png, final-itinerary-expanded.png, final-itinerary-dark-large.png, final-expanded-stops.xml. Initial cards-dark-large.png/cards-expanded.png were captured before configuration settled and are not final evidence.
- Runtime journey evidence is a direct bus with a walking connection; multi-transfer/overnight logic is implemented but not separately exercised in this bounded check. No physical-device claim.
