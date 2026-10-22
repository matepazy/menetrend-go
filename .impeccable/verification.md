# MenetrendGO verification

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
