# MenetrendGO UI simplification — 2026-10-23

Mode: Operate. Keep the approved pale-blue palette, rounded controls, Hungarian copy and four destinations. This is a structural simplification within the existing identity, not a replacement visual world; no new comp is required.

User direction: keep the pale-blue rounded style; fix mismatched white rectangles around the appearance radio rows, on Pixel 10 Pro / latest Android. Keep the navbar floating with content behind it; add a feathered bottom blur that obscures that content without blurring the navigation controls.

Implementation: solid uniform themed panels replace layered gradient surfaces; full-width station rows and a divider/swap control; smaller shared page titles; consistent full-width result/detail surfaces; font-relative timetable time columns; wrapping vehicle filters. Appearance rows are single native radio selections with one touch target. The rounded navigation is compact, with full-item selection and inset-aware trailing scroll padding. A bottom-only display-list blur with feathered alpha and tonal fade sits behind the floating bar (Android 12+; tonal fade fallback on Android 9–11). Expanded layouts retain the rail and no bottom veil.

Navigation: progress-driven predictive back previews the previous screen, scales/translates the current pane from either edge, commits on completion and restores on cancellation. System animation disablement removes pane transformations. Native modal sheets keep their own Android back behavior.

Quality bar: no sharp color patches inside panels; no fixed background strip under floating navigation; readable Hungarian labels and reachable final controls at larger text; retain real provider-backed data and all local pass behavior. No new product claims, assets or backend changes.
