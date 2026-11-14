# Fleet catalog review · 2026-11-13

The Android catalog publishes 121 profiles. Every published profile has exactly one selected Wikimedia Commons photograph, a Hungarian description, a labeled basic fact sheet, curiosities and source links. The existing app style and native sheet, retry, zoom, Back and source disclosure behavior are preserved.

This is a curated model guide, not a live inventory of serviceable vehicles. Current passenger use is the inclusion scope. A model's maximum speed, production period or layout describes that type or the explicitly named variant. It does not describe the equipment or operating speed of an individual arriving service. Existing local-operator profiles remain supported alongside MÁV, HÉV and Volán profiles.

## Additions

- LEW MX and MXA HÉV units. The [operator's vehicle list](https://www.mavcsoport.hu/sites/default/files/upload/page/3m_igenybevett_halozat_es_eszkozok.pdf) distinguishes their three-car formations. [Type history](https://hu.wikipedia.org/wiki/LEW_MX_%C3%A9s_MXA) supplies manufacturer and production dates. MIX/A is excluded because [MÁV records its withdrawal from public service](https://www.mavcsoport.hu/fenyjarmuvek).
- IC+ Bbdpmz multi-purpose passenger coach. [MÁV's type introduction](https://www.mavcsoport.hu/mav-csoport/elkeszult-elso-husz-hazai-gyartasu-ic-kocsi) identifies the Szolnok-built version and its 200 km/h design capability. Generic IC+ aliases are deliberately avoided because the family includes other coach configurations.
- Bybdtee Halberstadt control car. [MÁV's account of control-car operation](https://www.mavcsoport.hu/sites/default/files/upload/page/vm_apr_final_webre_full.pdf) explains its relationship with the Cirmos locomotives. The [2024 service-quality report](https://www.mavcsoport.hu/sites/default/files/upload/page/szolgaltatasminosegi_jelentes_2024_start_teljes_mellekletekkel.pdf) lists the passenger-service type.
- Ikarus C56 and C80. The [operator's 2026 event page](https://www.mavcsoport.hu/mav-szemelyszallitas/szolgaltatasok/esemenyek-es-nosztalgiajaratok/retro-es-tematikus-esemenyek) explicitly identifies TGN-061 and HZE-127 as buses used on scheduled regional services. Their production dates and dimensions are cross-checked against the linked type histories.

## Photo review

All 121 selected URLs were downloaded successfully and checked as images within the app's 2 MiB per-image limit. Commons imageinfo supplied original dimensions, author, file page and free-license metadata. Contact-sheet inspection checked type correspondence and framing, with a second selection pass replacing several dark, distant or lower-resolution images. Selected images are requested at up to 1280 pixels. Original dimensions, exact file links and attribution are recorded in `catalog-review.json` and the app catalog. A few scarce types have smaller originals, with a minimum source width of 1024 pixels. Images are never upscaled to manufacture resolution.

Foreign-operated examples are used when an exact variant is identifiable but a suitable Hungarian example was not found. The app explicitly labels photos as type illustrations and warns that livery and equipment can differ. Family names never silently inherit a known different length, drive or generation. The generic Setra S419 name remains unresolved rather than borrowing the S419 UL photograph.

Use `node scripts/refresh-fleet-photo-urls.mjs` to refresh metadata for the already selected files. It does not search for replacement images or admit new models. It rejects profiles that do not have exactly one photo and rejects unsupported licenses. Review changes before shipping.

## Research backlog and scope exclusions

`backlog.json` preserves the original profiles outside Android assets, following the user's explicit choice to keep incomplete entries out of the app catalog.

| Profile | Reason |
| --- | --- |
| Credo EN 18 | No verified exact-type Commons photo found. BN 18 and Econell 18 Next are different types. |
| Credobus Optinell 10 | No verified exact-type Commons photo found. Current Volán passenger use also needs stronger evidence. |
| Credobus Optinell 12 | No verified exact-type Commons photo found. Current Volán passenger use also needs stronger evidence. |
| Setra S419 | Provider name omits its variant suffix. An S419 UL photo cannot establish that it represents this separate entry. |
| Ikarus 260 | The operator states that JSZ-369 leaves daily operation after the October 2026 Aeropark event. Remaining cited examples are heritage services. |
| Ikarus 280 | Existing evidence documents the preserved heritage fleet. |
| Ikarus 256 | Existing evidence documents the preserved heritage fleet. |

## Source availability and limits

`catalog-review.json` records source availability separately from content verification. The broken new HÉV reference was corrected and returned HTTP 200. Several existing BKV pages timed out and two HBweb references could not be fetched during this pass. Their existing citations remain intact because a timeout is not evidence that a type or a fact is wrong. Existing factual copy is retained except for the explicitly researched changes. This pass does not certify every pre-existing historical curiosity, nor claim a complete November 2026 national vehicle roster.

Facts with configuration-dependent dimensions or equipment keep their qualifier. Unknown dimensions are omitted. Seating has an explicit unknown state as described below. Source and license information is bundled so it remains readable if image download fails. Photo caching remains limited to 12 files and 12 MiB.

## Bus seating review · 2026-11-14

All 95 bus profiles now have an **Utasülőhelyek** fact-sheet row. `bus-seating.json` records the displayed Hungarian text, passenger-seat counts, exact source, configuration scope and review status for each profile. There are 87 sourced counts and eight explicitly unverified entries. Published sources are original manufacturer brochures, manufacturer releases, operator vehicle sheets and operator/authority fleet reports. Some original brochures and operator documents are hosted by archives or dealers.

The count describes the named layout, delivery or manufacturer maximum. Foreign examples and older configurations are labeled. It is not a promise about the number of seats on an arriving bus. Driver and separately identified crew seats are excluded. Fixed and folding passenger seats stay separate when the source identifies them. A wheelchair space is not a seat. Manufacturer maxima are not substituted for a known operator configuration.

For IC 12 LLL-882, the tender explicitly labels 52 as a registration-certificate count. Registration field S.1 includes the driver, so the displayed 51 passenger seats are derived by subtracting one. Both the operator document and the field definition are linked. Other operator tables are retained as published seated-passenger examples, not converted into family-wide bounds.

The following seating counts remain unverified: **Credo LH 12, generic Mercedes-Benz Conecto, Volvo 7900A, Volvo–Säffle 8500, Ikarus E134, ARC 127 / Ikarus E127, Ikarus EAG 395/E95 and Ikarus V127**. Their fact sheets say “Kivitelfüggő, ellenőrzött adat még nincs”. The research records explain the ambiguity. Their already reviewed photos and other facts remain available. SOR LH 12, V134, electric/hybrid 7900 variants and Modulo M108D are not used as substitutes.

Seating citations are bundled under **Fotó és források**. The data tests enforce one seating field and research record per bus, visible configuration qualifiers, source inclusion and a number-free unknown state. Regression examples distinguish the driver/folding seat in EN 9,5, first-generation and C2 Citaro K, and the Budapest B12 fixed seats.

## Validation

- Catalog tests enforce a single photo, free-license credit, explicit unique aliases, a nonempty fact sheet and coverage of known provider names through a published profile or the research backlog.
- Android tests exercise real catalog parsing, one-photo rendering, fact labels, curiosities, attribution, source disclosure, full-screen viewing and Back in light and enlarged-text dark modes.
- Content files are explicit Gradle unit-test inputs so changing the catalog or backlog reruns validation.
- Seating follow-up: 225 unit tests passed, debug app/test builds and lint passed. Two seating UI tests passed at each of 427 dp and 1024 dp, covering light C56 and dark EN 9,5 at 1.3 font scale, photo viewing/Back and expanded sources. Final captures were inspected for wrapping and clipping. An initial phone test launch raced a density change and crashed before running tests, then passed after the configuration settled. The emulator's original density override was restored.
