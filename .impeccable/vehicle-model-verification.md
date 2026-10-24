# Vehicle model and registration (2026-10-24)

Journey details show the matched live vehicle's model and registration below the service type. Matching reuses the route map's exact-trip and service-date filtering. The shared map vehicle sheet uses the same `model (registration)` formatter. A missing field leaves the available value, and empty metadata adds no row. Rail UIC numbers retain their separate map-sheet row.

Validation: `assembleDebug`, `testDebugUnitTest` (59 tests), `lintDebug`, and `git diff --check` passed. Installed the debug APK in place on emulator-5554.

Native phone verification used the real Székesfehérvár–Budapest 1172/2 service: `Scania Touring (MXN521)` appeared in both journey details and the route map vehicle sheet. The sheet also displayed correctly at 1.3 font scale. A model-only rail vehicle displayed `STADLER-FLIRT` with its separate UIC row. Captures: `.impeccable/review/vehicle-model/journey-phone.png`, `map-phone.png`, and `map-large.png`.

The app was explicitly set to light appearance, so switching Android night mode did not verify the app's dark theme. Further dark-theme and tablet checks were stopped at the user's request to use the checks already completed. System night mode and font scale were restored. No physical-device verification is claimed.
