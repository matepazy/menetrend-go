# MenetrendGO

<!-- impeccable:product-schema 1 -->

## Platform

android

## Implementation

Native Kotlin and Jetpack Compose (Material 3), minSdk 28. The default map uses the Google Maps SDK; railway and custom tile layers use bundled Leaflet in a WebView. No backend, no account system, no analytics.

## Users

Anyone travelling by public transport in Hungary on MÁV-network services (rail, HÉV, Volánbusz coaches, local buses, replacement buses): daily commuters checking departures and delays at a stop as well as occasional riders planning a long-distance trip.

## Product Purpose

A Hungarian-language transit app for journey planning, live vehicle tracking, stop departure boards and pass-expiry tracking. Success means a rider gets a trustworthy answer (when, where, is it late, will I make the transfer) faster than with the official tools.

## Positioning

An independent, unofficial client on the same MÁVPlusz data, set apart by:

- a faster, cleaner, native Material 3 interface without accounts or ads;
- capabilities the official app lacks: a live vehicle map with stop boards, home-screen departure widgets, get-off alerts, transfer help and fleet/vehicle-model information.

## Operating Context

Used on the move: at stops and platforms, on board, often one-handed and in variable light, with mobile data that may be slow or missing. Data is requested directly from the provider's public endpoints on the device; availability and freshness depend on the provider. Distributed as APKs through GitHub Releases with an in-app updater.

## Capabilities and Constraints

- Journey planning with saved/recent searches, journey details, provider service alerts, transfer checks with alternative departures, and an opt-in get-off alert (developer-gated).
- Live map of vehicles and stops, stop departure drawers and resizable home-screen widgets.
- Country, county and local pass tracking with expiry reminders; validity checks are informational, never entitlement proof.
- Keep provider station identity (platforms group into one station; street stops keep their provider IDs).
- Scheduled and real-time information must stay visibly distinguishable.
- Every data surface handles loading, empty and failed-request states; verify changes on an Android emulator.
- User-facing copy is Hungarian.
- No offline timetable database; an internet connection is required for data.

## Brand Commitments

- MenetrendGO is independent and unofficial, never presented as a MÁV product.
- Never ship MÁV or other operator logos, pictograms or fonts. Route marks, service icons and mode icons are the app's own look-alike artwork in the familiar provider colours (`scripts/generate_pictograms.py`); operator names appear as plain text.

## Evidence on Hand

- Product description and feature list: `README.md` (Hungarian).
- Visual system record: `DESIGN.md`.
- Vehicle catalog with Wikimedia Commons photos: `app/src/main/assets/vehicle_models/catalog.json`.
- No user research, testimonials, usage metrics or store listing exist; do not fabricate them.

## Product Principles

1. Trustworthy over clever: never blur scheduled and live data, and state data gaps as unknown rather than guessing.
2. Fast answers on the move: the most time-critical fact (next departure, delay, transfer risk) leads.
3. Native and familiar: Material 3 and Android conventions over custom controls.
4. Private by default: data stays on the device, nothing requires an account.
5. Independent: familiar to MÁV riders through colour and meaning, never through borrowed artwork.

## Accessibility & Inclusion

Touch targets of at least 48dp, native text scaling, screen-reader descriptions for pictograms and live/scheduled status, and colour never as the only signal.
