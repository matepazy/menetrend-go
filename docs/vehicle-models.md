# Vehicle model drawer

The dotted model label opens a separate Material bottom sheet from both journey details and the map vehicle sheet. Each label owns only the model-sheet state. Closing, swiping away, or pressing Back in the model sheet must leave the map vehicle and its stop-list scroll position intact.

The Hungarian articles and fleet photos are local assets in `app/src/main/assets/vehicle_models/catalog.json`. Model matching uses explicit aliases, with case, accents, whitespace and punctuation normalized. It never guesses a variant from a substring. Registration numbers remain separate from the clickable model name. New, unrecognized provider names get an honest unavailable state.

## Native drawer pattern

This is a local extension of the native design system recorded in `DESIGN.md`, which remains the visual authority. Reuse its icy-blue Material color roles, rounded shapes, system typography and native sheet feedback. The drawer introduces no separate palette, type scale or visual identity.

The model label uses primary-colored body text, a dotted underline on each wrapped line and a minimum 48dp button target with the Hungarian action label “Járműtípus bemutatása”. The adjacent registration uses supporting text color. The sheet opens fully expanded with the existing extra-large shape and a height of 92% of the available space. Its fixed header pairs a bold headline-small model name with a labeled close icon. An existing familiar name or alternative name after the title's middle dot appears in a tonal, non-interactive pill; the wrapping header accommodates long names and larger fonts. The article scrolls independently below it, using 16dp horizontal insets to give the photograph more room.

The article has two explicit, accessible headings: “A járműről” introduces the vehicle and its general characteristics, followed by “Érdekesség” or “Érdekességek” according to the number of curiosities. The latter contains sourced naming stories, engineering details and model history rather than repeating the introduction. Both headings use bold title-large text, with 24dp above and 12dp below. Body text uses body-large. A broad fleet photograph and quiet illustration caption lead the article. Photos fit proportionally inside a rounded 3:2 surface without cropping; multiple photos use a native horizontal pager with a visible page count. Body-small carries captions and attribution.

A divided “Fotó és források” disclosure finishes the article. Its collapsed row shows the current photo's photographer, Wikimedia Commons source and license badge. Expanding it reveals the full photo caption, direct file and license links, and article sources. The selection follows the photo pager. Native expansion motion honors Android's animation scale, and the disclosure exposes its expanded state to accessibility services. Source and license actions retain native text-button feedback and minimum 48dp targets. Loading, retry, unknown-model, missing-photo, image-error and link-error states use explicit Hungarian text or native progress indicators.

Keep the model sheet's state local to its label. Both entry points (`PlanningScreens.kt` and `VehicleDrawer.kt`) use `VehicleModelLabel`; its dismissal clears only its own open state. Preserve the underlying journey or map context rather than introducing a separate navigation destination.

## Coverage and release status

The catalog contains 76 profiles covering the model names observed in the MÁVPlusz live feed on 2026-10-24, including spelling aliases. `app/src/test/resources/vehicle-models-2026-10-24.txt` records these names. This is a dated observation, not a promise that the provider cannot add types later.

There are 168 catalogued, freely licensed photos for 68 profiles; 45 profiles have three distinct photos. The user stopped further photo research on 2026-10-24 and required Hungarian MÁV/Volán fleet examples, including predecessor Volán companies. The other profiles have one or two verified photos, except these eight with none:

- Credo EN 18
- Mercedes-Benz Tourismo L
- Setra S 516 HD
- Setra S 419
- Setra S 416 GT-HD
- Setra S 415 HD
- Irisbus Evadys HD
- Scania OmniExpress

Do not substitute a foreign operator's example, a nearby model, an AI-generated image, or an all-rights-reserved image. Candidate bobalt.hu/Flickr fleet photos were excluded because reuse permission was not established. These articles show a clear missing-photo state in development.

Run `node tools/verify-vehicle-catalog.mjs` for integrity and `node tools/verify-vehicle-catalog.mjs --release` for the complete-photo gate. The latter intentionally fails until all eight gaps are resolved. No release APK was exported or published for this feature.

## Verification and finish review

The debug build, all 61 unit tests and Android lint passed. The catalog integrity check passed for all 168 photos, including complete attribution and distinct source pages within each gallery. The final debug APK was installed successfully with `adb install -r` on `emulator-5554` and MainActivity was launched. These checks do not establish rendered UI behavior.

Native emulator interaction and screenshot verification remain pending: Computer Use returned “Computer Use was not approved to use qemu-system-x86_64”. Installation and launch succeeded through ADB, but no rendered UI verification is claimed. Opening and dismissing the nested sheet, preserving the underlying stop-list scroll position, photo paging, wrapping, font scaling and light/dark appearance still need a native UI check.

The previous feature review recorded the eight known photo gaps. This update stops photo acquisition at the user's request; it does not claim those gaps are resolved or that a new independent review occurred. The existing `--release` gate still requires a photo for every profile; native UI verification remains outstanding.

## Photos and sources

Each photo records its original Wikimedia file page, author, license name and license URL. The article's source disclosure visibly labels the photographer, source and license; its expanded content provides direct photo, license and article-source links. The source/author/license also travel inside each JPEG's COM metadata. Images display proportionally without cropping. Wikimedia thumbnail scaling does not imply ownership or endorsement by the photographer.

The 99 newly added photos were checked visually and against their file descriptions. Municipal MVK examples, adjacent coach variants and unlicensed fleet-database photos were excluded. Intouro M and Tourliner L registrations were cross-checked with fleet records linked in the catalog. An earlier ÜL363 illustration was removed from the explicit ÜL313/353 profile and replaced with a verified ÜL313. New JPEGs preserve the full frame, are resized to at most 1000px and carry their source, author, license and transformation note in COM metadata. The in-app credit also labels resizing.

The selected images depict Hungarian fleet examples of types observed in the live feed. A photo is a model illustration, not identification of the particular arriving vehicle, and is labelled accordingly. Historical photo dates do not establish that a particular photographed unit is still active. Do not make that claim.

To add a model, add its exact provider aliases, Hungarian article and factual sources to the catalog, then add a fleet-specific freely licensed photo and its complete credit. Review both the photo's description and the image itself: text searches can return paper models, other operators and adjacent variants. Keep the dated fixture separate from later provider observations.
