# Disposition

**Ship for the reviewed light phone configuration.** No blocking issue found in the new setting, overflow menu, raw-response presentation, or attribution path. Recapture the scrolled state without the Android text-selection toolbar for cleaner final evidence. This is a bounded finish review, not full platform certification.

# Evidence

Independently inspected the six-file source diff and all seven PNG captures under `.impeccable/review/raw-graphql/`: `setting-off`, `setting-on`, `journey-menu-enabled`, `menu`, `raw-light`, `raw-copied`, and `raw-scrolled`. Captures were supplied from Pixel 10 Pro Android 17 emulator; this reviewer did not control the emulator.

- The developer toggle is legible in both states and uses the requested Hungarian label. Source reads a default-false SharedPreferences flag and writes changes with apply(). The entire label row is a toggleable switch target with a 56 dp minimum height.
- The enabled journey header has a right-aligned overflow. Its dropdown exposes exactly “Nyers adatok”. Source gates both overflow and viewer on the persisted flag.
- The sheet has a clear title, copy action, close action, and an explanation that the full response containing the selected journey is shown. `raw-light.png` visibly contains `data.plan.itineraries` and the provider's `Origin` name, whereas the journey uses the normalized station name. This supports the intended original-response distinction.
- Request code retains the received response String before extracting `data` and before mutating itinerary route/place JSON. Each Journey stores the String from its own plan() call. Pagination merges Journey objects rather than replacing raw payloads with a global latest response.
- Presentation pretty-prints on Dispatchers.Default and retains an exact String copy path to the clipboard. The scrolled capture reaches route/trip content and closing JSON braces. The copied capture shows “Másolva”. Neither screenshot independently proves clipboard byte-for-byte contents.

# Findings

No blocking correctness or layout finding in scope.

1. **Evidence polish:** `raw-copied.png` and `raw-scrolled.png` retain the native text-selection toolbar over explanatory copy. Deselect and recapture the scrolled state. This is an evidence obstruction, not a demonstrated product defect.
2. **Minor copy polish:** Split “A behúzás az olvashatóságot segíti; másoláskor az eredeti választ kapod.” into two sentences for more natural Hungarian copy.

# Quality floor

The new UI follows the established pale blue Material theme and type roles. It uses native Material Switch, IconButton, DropdownMenu and ModalBottomSheet components. New controls have meaningful Hungarian labels or content descriptions. JSON is an appropriate use of monospace, wraps long values without observed horizontal clipping, and remains scrollable below a fixed action header. A missing-payload recovery message and formatting loading state exist in source. No fabricated payload fields or UI-generated serialization of the normalized Journey were found.

The supplied screenshots show readable text and clear action hierarchy. Exact contrast ratios were not measured. IconButton and TextButton use Material minimum target behavior; the setting row explicitly exceeds 48 dp. Standard sheet dismissal is wired through onDismissRequest. Predictive Back and actual accessibility traversal were not exercised by this reviewer.

# Limitations

This review covers only the added raw GraphQL flow and its changed source paths. No broad app audit or native web-detector run was performed. Build, test and lint success were reported by the implementation agent and were not independently rerun. Dark mode, 1.3 font scale, tablet layout, physical hardware, clipboard content equality, restart persistence, disabled journey screenshot, pagination runtime attribution, and empty-payload rendering were not independently demonstrated in the supplied evidence. Those paths are supported by source inspection where stated, not claimed as runtime verified.
