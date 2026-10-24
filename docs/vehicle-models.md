# Vehicle model drawer

The dotted model label opens a separate Material bottom sheet from both journey details and the map vehicle sheet. Each label owns only the model-sheet state. Closing, swiping away, or pressing Back in the model sheet must leave the map vehicle and its stop-list scroll position intact.

The Hungarian articles and fleet photos are local assets in `app/src/main/assets/vehicle_models/catalog.json`. Model matching uses explicit aliases, with case, accents, whitespace and punctuation normalized. It never guesses a variant from a substring. Registration numbers remain separate from the clickable model name. New, unrecognized provider names get an honest unavailable state.

## Native drawer pattern

This is a local extension of the native design system recorded in `DESIGN.md`, which remains the visual authority. Reuse its icy-blue Material color roles, rounded shapes, system typography and native sheet feedback. The drawer introduces no separate palette, type scale or visual identity.

The model label uses primary-colored body text, a dotted underline on each wrapped line and a minimum 48dp button target with the Hungarian action label “Járműtípus bemutatása”. The adjacent registration uses supporting text color. The sheet opens fully expanded with the existing extra-large shape and a height of 92% of the available space. Its fixed header pairs a bold headline-small model name with a labeled close icon. The article scrolls independently below it, using the shared 24dp sheet insets and 12dp content gaps.

The opening content is a fleet photo with its credit, followed by the introduction, “Jó tudni” facts and article sources. Photos fit proportionally inside a rounded 3:2 surface; multiple photos use a native horizontal pager with a visible page count and the current photo's credit. Body-large introduces the model, body-medium carries facts, and body-small carries captions and attribution. Source and license actions retain native text-button feedback and minimum 48dp targets. Loading, retry, unknown-model, missing-photo, image-error and link-error states use explicit Hungarian text or native progress indicators.

Keep the model sheet's state local to its label. Both entry points (`PlanningScreens.kt` and `VehicleDrawer.kt`) use `VehicleModelLabel`; its dismissal clears only its own open state. Preserve the underlying journey or map context rather than introducing a separate navigation destination.

## Coverage and release status

The catalog contains 76 profiles covering the model names observed in the MÁVPlusz live feed on 2026-10-24, including spelling aliases. `app/src/test/resources/vehicle-models-2026-10-24.txt` records these names. This is a dated observation, not a promise that the provider cannot add types later.

There are 70 freely licensed photos for 68 profiles. **This feature is not release-ready:** the following eight profiles still require a verified, freely licensed photo of a MÁV/Volán fleet example:

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

The debug build, all 61 unit tests and Android lint passed. The asset audit passed for all 70 bundled photos, including complete attribution. These checks establish build, static and catalog integrity; they do not establish rendered UI behavior.

Native emulator interaction and screenshot verification remain pending because Computer Use permission for the emulator was denied. The user explicitly requested finishing with that verification pending. Opening and dismissing the nested sheet, preserving the underlying stop-list scroll position, photo paging, wrapping, font scaling and light/dark appearance still need a native UI check.

The independent review's disposition is **fix**, limited to the eight known photo gaps above. It found no additional static implementation defect and confirmed that the KISS and Volvo 7700 corrections were resolved. Missing-photo development states are documented limitations, not a completed release treatment. The feature remains blocked for release until all eight verified fleet photos are supplied and the release catalog gate passes; native UI verification also remains outstanding.

## Photos and sources

Each photo records its original Wikimedia file page, author, license name and license URL. Credits and license links appear immediately below the photo; article sources appear at the end of the article. The source/author/license also travel inside each JPEG's COM metadata. Images display proportionally without cropping. Wikimedia thumbnail scaling does not imply ownership or endorsement by the photographer.

The selected images depict Hungarian fleet examples of types observed in the live feed. A photo is a model illustration, not identification of the particular arriving vehicle, and is labelled accordingly. Historical photo dates do not establish that a particular photographed unit is still active. Do not make that claim.

To add a model, add its exact provider aliases, Hungarian article and factual sources to the catalog, then add a fleet-specific freely licensed photo and its complete credit. Review both the photo's description and the image itself: text searches can return paper models, other operators and adjacent variants. Keep the dated fixture separate from later provider observations.
