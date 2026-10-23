# UI overhaul verification — 2026-10-23

- Android `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed. Nine JVM tests: four query tests and five pass-rule tests. Existing nonfatal lint warnings remain.
- Installed updated debug APK in place on Pixel_10_Pro emulator (Android 17, 1280×2856 at density 480); no app data was cleared.
- Real saved Mór–Székesfehérvár search returned provider journeys; opening service 1845 showed its walking/transit timeline. No fabricated journey data.
- Captures in `.impeccable/review/overhaul/` cover light and dark appearance rows, 1.3 font scale, home/results/journey, passes/editor, map, and 1600×2560 density-240 expanded layouts with navigation rail. Expanded checks use an emulator viewport, not physical tablet hardware. Device size, density and font scale restored after checks.
- Removed mismatched rectangular panel fills by using one solid themed surface. Appearance options now have a single selectable radio-row target. Full-width station fields, compact shared page titles, wrapping filters and font-relative stop times improve alignment/readability.
- The navbar remains a rounded floating surface. Content scrolls behind it and trailing padding keeps final controls reachable. The bottom feather combines real display-list blur (Android 12+) with a smoothly masked tonal fade; Android 9–11 use the fade fallback. Navigation controls are outside the blur layer. No second UI tree is composed for the backdrop.
- Injected left-edge gesture progress, reversed the pointer and released: settings remained open (`back-progress.png`, `back-cancelled.png`). Completed left-edge swipe returned home (`back-completed.png`). Right-edge back from developer settings returned to the main settings screen. These are emulator gesture checks, not physical-device smoothness/performance evidence.
- System animation disablement bypasses predictive pane transformations. Native Material sheets retain native back handling; their every gesture state was not separately instrumented.
- Independent review returned **ship within the reviewed scope** after inspecting 15 required native screenshots. See `overhaul-finish-review.md` for evidence and limitations.

Debug APK SHA-256: `ce5917cec9093b4f7b4553e22ed4f74c37eae91e405a7f8346be792bc9a1767d`.
