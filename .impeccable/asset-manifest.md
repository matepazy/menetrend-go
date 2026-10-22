# MenetrendGO asset manifest

## Shipping raster media

No bundled raster artwork is required for the approved Android interface. The app is a native, Hungarian transit utility built from text, semantic controls, vector icons, and live map content. No generated photography, decorative illustration, texture, or rasterized card background is needed.

Map tiles are runtime geographic content supplied by the map provider, not exported design assets. Retain the provider's required attribution and respect its tile usage terms in the map implementation.

## Native implementation inventory

| Surface | Implementation |
| --- | --- |
| Search-first planning page | Compose text fields, origin/destination icons, clear and swap controls, departure date/time selector, primary search button, and recent-search rows |
| Separate results page | Native navigation/back control, search summary, date/time controls, and a long scrollable departure list built with `LazyColumn` |
| Departure rows and service detail | Native text, route badges, transport icons, timeline lines/dots, scheduled/live/stale status labels, and explicit pass-eligibility state |
| Bottom destinations | Native navigation controls with search, map, pass/card, and settings vector icons |
| Map | Runtime OpenStreetMap tiles in bundled Leaflet, HTML count clusters/vehicle pills and Leaflet zoom controls; native Compose filter, refresh and location actions |
| Passes and settings | Native cards, selectors, date controls, switches, dialogs, and permission explanations |
| Feedback and empty states | Native text and vector progress, error, refresh, and location symbols; no image-based UI |

All fields, buttons, panels, chips, and navigation surfaces use fully rounded native shapes. Slight blue-white glass is implemented through Compose colors, tonal highlights, and restrained elevation; labels, hit targets, focus, disabled states, and accessibility semantics remain native. Do not flatten controls or glass panels into bitmap assets.

Icons should use consistent Material vector equivalents or small Android vector drawables. Functional icon buttons require meaningful Hungarian accessibility labels; decorative icons alongside equivalent text should not duplicate announcements.

## Existing vector asset

`app/src/main/res/drawable/ic_launcher.xml` is the existing blue-and-white vector transit launcher mark, referenced by `AndroidManifest.xml`. Keep it as vector media. This inventory does not change or certify launcher adaptive-icon behavior.

## Design reference boundary

`.impeccable/mocks/concept-a.png` is an approved visual reference only and must never ship as a screen, background, card, or rasterized app UI. It guides the pale blue-white palette, fully rounded geometry, and restrained glass treatment.

The user's approved flow supersedes the reference's sample result placement: the planning page contains search and recents; results open on a separate page with a long departure list. Reference times, locations, service numbers, pass status, notification badges, and system chrome are illustrative and must not be presented as verified live data.
