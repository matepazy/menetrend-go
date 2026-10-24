# Layout consistency pass — 2026-10-24

Preserved the icy-blue Material theme, Hungarian copy, floating navigation, transit colors and connected itinerary. Shared AppLayout roles now keep screen gutters and panel padding at 20dp, repeated list gaps at 16dp, label gaps at 4dp, related content at 8–12dp, and major sections/sheet insets at 24dp. Page headers share a 48dp minimum row with no extra vertical inset. The recent-search heading and empty description form one group. Timetable cards use the same panel inset and medium shape as other native panels. Pass filters have explicit wrapping gaps and results filters have 48dp minimum targets.

The first batched rendered assessment found the 760dp content column anchored left on tablets and cramped timetable time/name proximity. The single correction batch centers the column within the content area beside the rail and adds a 12dp gap between time and stop columns in both timetables and vehicle drawers.

## Evidence

- Final `:app:assembleDebug :app:lintDebug --offline` passed. Lint: 0 errors, 16 existing warnings. `git diff --check` passed. No behavior changes or new unit tests.
- Updated debug APK installed in place on emulator-5554, preserving stored passes and history.
- Initial inspection: phone at 1280x2856 / density 480; light search, passes, editor, settings and appearance; dark 1.3x text settings/search/results/itinerary/timetable; expanded 1600x2560 / density 240 search/passes/settings/map and a real HÉV vehicle drawer. Real Székesfehérvár–Budapest results and 865 BALATON InterCity journey/timetable were used.
- Confirmation: final-tablet-search.png and final-tablet-settings.png show the corrected centered column. final-compact-search.png checks a narrower 360dp phone with 1.3x text; long navigation labels wrap to two lines and recent searches remain scrollable below the fold.
- Captures and accessibility dumps: `.impeccable/review/layout/`. Files without `final-` are from the initial inspection. The final compact results/timetable capture was interrupted by an ADB connection failure and is not claimed as confirmed. The timetable gap is source-verified in the final build; the initial 426dp/1.3x timetable was rendered before that correction.
- Impeccable layout detector returned no findings in degraded regex mode. It does not prove native measured layout or contrast. Native screenshots and Android lint are the evidence here.

Original emulator size/density and font scale were restored after review.

## Limits

This is a bounded emulator layout review, not physical-device performance proof or exhaustive accessibility certification. The final vehicle-drawer column gap was not separately recaptured after the correction. No provider, pass-validity, reminder or routing behavior was changed.

## Navbar feather boundary follow-up

At the user's request, removed the 48dp extension above navigation and bounded the effect to the bar surface's actual top (measured navigation inset minus the 6dp outer top margin). The outer offscreen layer clips the child blur so blur sampling cannot paint outside that bound. assembleDebug passed after this follow-up.

Installed the follow-up APK and inspected final-feather-results.png with a real result card crossing behind the navbar. Content remains sharp above the bar surface; the feather no longer extends above its top edge.
