# Raw GraphQL viewer verification

- assembleDebug, nine existing JVM tests and lintDebug passed.
- Installed in place on the Pixel 10 Pro emulator, Android 17.
- Captured default-off and enabled developer rows, journey overflow, menu, raw JSON, copied feedback and scrolled content.
- Real provider response displayed the full data.plan.itineraries envelope and the original Origin placeholder, while journey UI shows normalized station names. Original body is held as a String before JSONObject mutation and is shared by results from its own response batch.
- Persisted enabled flag was read back after force-stop/restart. Copy produced Másolva feedback. Scrolling worked and system Back returned to journey details. Clipboard byte equality was not separately inspected; source copies the retained original String.
- Disabled the setting and reopened journey details: overflow was absent. Captured journey-menu-disabled.png. Left the developer flag off after testing.
- Station geocoding returned duplicate IDs during verification, causing an existing picker duplicate-key crash. The station result list now deduplicates by ID.
- Independent review returned ship for the captured light phone configuration, with no blocker. Dark theme and large font of this new sheet were not runtime-tested. Selection toolbar in scrolled captures is native UI, not an app overlay.
- Evidence: .impeccable/review/raw-graphql/.
