---
name: MenetrendGO
description: A calm Hungarian transit utility in icy blue, ink and rounded milky surfaces.
colors:
  primary: "#2864D9"
  on-primary: "#FFFFFF"
  background: "#EDF4FA"
  ink: "#112D43"
  surface: "#F8FBFF"
  surface-variant: "#E3EDFA"
  muted-ink: "#4E6581"
  primary-container: "#DCEAFF"
  on-primary-container: "#174FAF"
  outline: "#71849C"
  dark-primary: "#9FC2FF"
  dark-on-primary: "#082F65"
  dark-background: "#111317"
  dark-ink: "#E7E9EF"
  dark-surface: "#1B1E24"
  dark-surface-variant: "#303640"
  dark-muted-ink: "#BEC6D2"
  dark-primary-container: "#203F68"
  dark-on-primary-container: "#D8E7FF"
  dark-outline: "#8792A2"
  map-cluster: "#244C86"
  map-coach: "#0D687A"
typography:
  headline-medium:
    fontFamily: "Roboto"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: "36sp"
  headline-small:
    fontFamily: "Roboto"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: "32sp"
  title-large:
    fontFamily: "Roboto"
    fontSize: "22sp"
    lineHeight: "28sp"
  title-medium:
    fontFamily: "Roboto"
    fontSize: "16sp"
    lineHeight: "24sp"
  body-large:
    fontFamily: "Roboto"
    fontSize: "16sp"
    lineHeight: "24sp"
  body-medium:
    fontFamily: "Roboto"
    fontSize: "14sp"
    lineHeight: "20sp"
  body-small:
    fontFamily: "Roboto"
    fontSize: "12sp"
    lineHeight: "16sp"
  label-large:
    fontFamily: "Roboto"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
  label-medium:
    fontFamily: "Roboto"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
rounded:
  extra-small: "16dp"
  date-control: "20dp"
  small: "24dp"
  medium: "28dp"
  large: "32dp"
  extra-large: "36dp"
  pill: "50%"
spacing:
  compact: "8dp"
  small: "12dp"
  regular: "16dp"
  page: "20dp"
  sheet: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.pill}"
  button-outlined:
    textColor: "{colors.primary}"
    rounded: "{rounded.pill}"
  station-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.medium}"
    padding: "12dp 4dp"
  glass-panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.medium}"
    padding: "{spacing.page}"
  result-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.large}"
    padding: "20dp"
  route-badge:
    backgroundColor: "{colors.primary-container}"
    rounded: "{rounded.pill}"
    padding: "7dp 12dp"
  bottom-navigation:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.large}"
---

# Design System: MenetrendGO

Journey legs and full trip timetables request MÁV `infoServices`. Journey services occupy a full-width panel after the itinerary, headed by the route badge and selected endpoints. MenetrendGO's own pictograms (drawn at the provider's MNR2007 code points) align in a fixed leading column beside wrapping Hungarian descriptions and original validity sections, grouped into onboard facilities and ticket/pass rules; secondary operational notices expand separately. Indulások cards end with a divider and acceptance/reservation pictograms, regardless of saved passes. Tapping an icon reveals all matching rules, routes and sections. Different sections remain separate, including reservation exemptions alongside mandatory-reservation notices. Missing facilities remain unknown. The bundled pictogram font's glyphs are hidden from accessibility text and announced using service descriptions.

## Overview

Vehicle article photos open a full-screen, theme-aware viewer from either the photo or its expand control. The viewer starts on the selected photo and supports horizontal paging through that model’s gallery, with the current photo’s bundled caption as the wrapping top-left title and a page count for multiple photos. One-finger swipes page at normal size, while zoomed drags pan the image. Each page has independent zoom state, and newly visited photos retain loading and retry states. Closing preserves the article’s original carousel position. The decoded image is reused without another request. Pinch zoom is bounded to 1–5× with panning clamped to the fitted image, double-tap toggles 2.5× zoom around the tap, and an accessible zoom/reset action provides an alternative. Arrival and dismissal use the shared Compose motion timings with opacity and gentle image scaling, honoring Android's animator duration scale. The overlay remains mounted through dismissal and returns to the same article and carousel position through Close or system Back.

The vehicle-model article retains the existing Read surface inside the full-height native sheet. Its curated Commons photos now download on demand at up to 960px, with a dedicated LRU cache capped at 12 photos and 12 MiB. Article text and attribution remain bundled. The image frame preserves layout while loading, names the loading state, and offers “Fotó újratöltése” after network failure without hiding the article. The original author, free licence, Commons file page and licence link remain in the source disclosure. New fleet entries preserve explicit variant matching and distinguish model capabilities from the facilities of an individual service.

**Creative North Star: "Icy blue ground, ink text, royal-blue actions, fully rounded milky panels"**

MenetrendGO feels calm, legible and direct. Its rounded character comes from solid themed panels and pale tonal separation. A feathered bottom blur softens content behind the floating phone navigation. Large rounded forms hold useful Hungarian text and familiar Material vector icons; the interface does not rely on decorative artwork.

This records the implemented Android interface. Roboto, Material controls, system bars and platform interaction anatomy are intentional. The native source is authoritative when a reference image differs. Source: `MainActivity.kt`, `PlanningScreens.kt`, `UtilityScreens.kt`, `SettingsScreen.kt`, `NearbyScreen.kt` and bundled `map.html` under `app/src/main`.

**Key Characteristics:**

- Icy blue and milky surfaces with strong ink text.
- Fully rounded panels, fields, pills and navigation.
- Native Android typography, controls and feedback.
- Text-led journey information with explicit data status.

## Colors

The primary blue draws attention to actions and selected states; cool neutrals carry most of each screen. Frontmatter colors describe the light scheme unless prefixed `dark-`.

### Primary

- **Royal Blue** (`primary`): primary buttons, meaningful icons and route lines.
- **Pale Blue Selection** (`primary-container`, `on-primary-container`): navigation item backgrounds, tonal actions and route-badge surfaces. Active phone tabs use a translucent primary-container background with primary-colored icon and label.
- **Dark-mode Sky Blue** (`dark-primary`, `dark-on-primary`): the corresponding primary pair in the dark scheme. Dark selection colors use the `dark-primary-container` pair.

### Secondary

- **Service colors**: rail categories use the shared MÁV category palette; other modes use provider route colors, with green for HÉV when its provider color is generic white.
- **Vehicle filter identities**: the filter sheet uses 40dp MenetrendGO mode pictograms, original front-view vehicle drawings generated as bundled Android vector drawables by `scripts/generate_pictograms.py`. They keep the familiar mode colors so riders still recognize them: rail navy (`#003A7E`), coaches yellow (`#FDC300`) with a dark `#3A3A39` glyph, local buses blue (`#009EE3`), HÉV green (`#005E3B`), TramTrain orange (`#F29101`), and replacement buses magenta (`#C4146E`) with a white train, arrow and yellow (`#FECC00`) bus. These are the mode selector pictograms, distinct from map marker/category colors. Preserve their artwork in both themes and switch states; labels and native switches communicate category and selection independently of color. Filter behavior and provider route colors remain unchanged.
- **Map Cluster Blue** (`map-cluster`): count clusters at wider geographic scales. These are map-specific roles, not additional application action colors.

### Neutral

- **Icy Ground** (`background`), **Milky Surface** (`surface`) and **Blue Mist** (`surface-variant`): the screen, solid panels and tonal controls.
- **Ink** (`ink`) and **Slate Ink** (`muted-ink`): principal information and supporting labels. `outline` is the explicit scheme outline role.
- The corresponding `dark-` tokens provide a deep blue ground, lifted blue surfaces and light text. Theme choice can follow Android or be set explicitly.
- Additional Material surface-container tones are defined by the native theme for dialogs and controls. Use the existing scheme roles rather than sampling a screenshot. Errors use Material's error role; no separate custom error palette is established here.

**The Role Pairing Rule.** Change surface and content colors together through `MaterialTheme.colorScheme`; a light-scheme ink or action label must not leak into its dark counterpart.

## Typography

**Display and body font:** Android's default Roboto through the unmodified Material 3 typography scale. There is no separate decorative display face. Sizes are Android `sp`, and spacing/radii are `dp`; the frontmatter preserves those native units.

Headlines are bold and sentence case. Headline-medium carries the planning prompt and prominent departure times; headline-small carries shared page titles, sheets and supporting time values. Title-large is used for section/card headings, with semibold or bold emphasis where the source requests it. Title-medium carries station values, action labels and key rows; selected values often use semibold. Body roles carry supporting labels, descriptions and explanatory status. Label roles carry route badges and compact navigation labels.

The frontmatter headline weights record the repeated heading treatment; individual arrival-time text can use semibold. Unspecified title/body weights retain Material defaults. Font scaling remains native, with wrapping and scrolling instead of fitting text into raster assets.

## Layout

Phone screens are single-column, vertically scrollable and inset from system bars. Content lists share `screenContentPadding()` with page padding (20dp), measured floating-navigation clearance and item gaps (16dp). `AppLayout` defines label gaps (4dp), compact gaps (8dp), related-content gaps (12dp), item gaps (16dp), page/panel insets (20dp), and section/sheet spacing (24dp). The recent-search heading uses a larger section separation, while the pass editor separates its field groups by 24dp. The map fills the available screen behind navigation without a page inset or rounded viewport. Panels and timetable cards use 20dp internal padding; compact vehicle-stop rows retain 16dp. The pass editor and vehicle drawer use sheet padding (24dp). Shared page headers have a 48dp minimum row height, 4dp between title and subtitle, and 8dp between back/title/actions, with no extra vertical padding. Timetable and vehicle rows separate time and stop columns by 12dp.

At the implemented wide-layout threshold (700dp), bottom navigation becomes a navigation rail. Main utility content is centered with a maximum width (760dp); the map can expand further (1600dp). The phone navigation surface shares the content page gutters (20dp) and departure-card corner radius (32dp), with vertical margins (6dp), inner padding (6dp) and gaps (4dp). Each item's inner content has a minimum height (48dp), growing to (64dp) above the font-scale threshold (1.15), with separate item padding (2dp horizontal, 8dp vertical). Labels may occupy two lines. Scrollable pages extend behind the floating navigation surface; its measured bottom inset is added to list content padding so final actions can scroll clear. The map retains safe control bounds. Safe drawing insets, navigation-bar padding and IME padding belong to the layout.

The planning surface opens with “Hová indulunk?” and the reminders bell on one row, followed by search and recents; there is no separate wordmark row. Results open as their own scrollable departure list. Future screens should follow the same navigation conventions.

## Elevation & Depth

The status bar is transparent. Maps and scrolling pages extend behind it, with initial list padding and map controls respecting the top safe-drawing inset. The top inset plus 24dp uses the same feathering as the bottom, mirrored vertically: an 18dp backdrop blur on Android 12+, a mask opaque through fraction .25 of the feather height, then easing to transparent along a 16-stop smootherstep curve (zero slope and curvature at both ends, so no visible knee), and a theme-background tint easing from alpha .82 to zero along the same curve over the full height. Android 9–11 use the same tonal fallback as the bottom. Native pages share one full-page blurred display list, cropped only after blurring with transparent out-of-bounds sampling to avoid stretched edge bands, and crossfaded against the sharp page so dark content is not doubled; the Google map shares the native feathered backdrop, while railway and custom Leaflet maps apply the equivalent CSS backdrop blur and mask inside their WebView to keep tiles live. System icons remain sharp. Expanded layouts retain safe rail controls; landscape display cutouts retain horizontal safe insets.

Depth comes from solid themed surfaces and tonal separation. The reusable panel is still named `Glass` in code, but uses an opaque surface with no gradient or shadow. Result cards and the opaque bottom navigation also have no custom shadow.

Phone content extends behind the floating bar, with no fixed background strip. The bottom slice of the shared display-list blur (18dp on Android 12+) is clipped to the navbar surface top edge (the measured navigation inset minus its 6dp top margin). Its alpha mask goes from transparent at the top to opaque at fraction .55, with a background-color fade from alpha 0 to .82. Navigation controls remain sharp. Android 9–11 use the tonal fade fallback; rail layouts have no bottom veil.

The map retains its own compact marker shadows, in native canvas artwork for Google Maps and CSS for Leaflet. These do not define native Compose elevation. Dialogs, sheets, disabled states and pressed feedback continue to use Material behavior.

Motion uses the shared `AppMotion` timings: 150ms feedback, 220ms arrivals and expansion, 160ms dismissal. Screen changes use a brief opacity transition with a 6dp vertical settle, without retaining outgoing interactive screens. Phone tab selection blends theme colors; the station-swap icon turns a half rotation; itinerary disclosures rotate and reveal their stops along the existing timeline. Compose respects Android's animator duration scale, including Remove animations. Station editing preserves the resting row's text and icon anchors. The focus scrim fades around a cutout expanded by the search panel's 20dp gutter, aligning it with the outer card edges; suggestions expand below the anchored row. No decorative loops or additional animation dependency are used.

All modal drawers use `AppBottomSheet`. A fixed outer top/horizontal safe-drawing inset reserves space for the status bar and display cutouts; only bottom insets remain inside the moving sheet. This keeps the expanded anchor independent of drag-driven top-inset consumption, avoiding full-height scroll/drag bouncing. Native scrolling, swipe dismissal, Back and keyboard handling remain in Material. The vehicle-model article fills the available safe height without its former 78% cap. Emulator regression coverage checks repeated flings, drag reversal frame by frame, article source disclosure and closing a nested article back to its parent drawer.

## Shapes

The update drawer caps its content at 85% of the window height, inside the shared safe-area sheet, retaining its bottom anchor and native drag handle. Long release notes scroll separately from the download, beta-confirmation and reminder actions. Release notes render as native Markdown text with themed links, headings, lists, emphasis, code and strikethrough; text follows the Material body scale and system font scaling.

The native theme's shape scale runs from softly rounded small controls to large containers, as recorded in frontmatter. Reusable panels, station rows and bottom navigation use medium corners (28dp). Result cards and dialog surfaces retain large corners (32dp); autocomplete rows use small corners (24dp). Sheet top corners retain extra-large corners (36dp); each phone navigation item has its own rounded corners (26dp). The planning date/time control uses 20dp corners. Buttons, badges and filter chips use pill geometry; circular icon controls remain circular.

**The Rounded Continuity Rule.** New controls and containers should continue the existing rounded family, including overlays and selection states.

## Components

### Buttons

The primary action is a full-width native pill with a vector icon, a title-medium label and a minimum height (58dp). Its icon is (22dp) with a label gap (10dp). Secondary outlined pills support actions such as additional departures and date selection; text actions handle lighter utilities. Retain Material enabled, disabled, focus, pressed and accessibility behavior rather than inventing desktop hover choreography.

### Cards / Containers

Solid `Glass` panels group related controls or explanations, with 20dp internal padding. Departure cards place wrapping service badges at the top left, followed by equally bold departure/arrival times with station names beneath and an arrow between. Supporting date/duration text follows. Only available live data adds a status label; the scheduled-time label is omitted. Detail surfaces fill the available content width. Full stop lists share the vehicle drawer’s connected timeline and font-aware 56dp time gutter. Itineraries use a connected vertical timeline with solid provider-colored transit lines, dotted walking lines, time/stop nodes, transfer waits and expandable intermediate stops. Recent-search rows use solid surfaces and medium rounded corners, a history icon, two place names and a chevron.

### Inputs / Fields

The “Keresési feltételek” drawer uses wrapping Material filter chips grouped as Utazás, Kocsiosztály and Szolgáltatások. Each chip has a bundled pictogram from the app's own font, a wrapping label, native selected semantics and color-only visual selection; touch targets remain at least 48dp. Chips have 8dp horizontal and vertical gaps and 8dp below group headings. Kocsiosztály offers 2., 1. and 1+; 1+ matches the Prémium osztály provider notice (MNR2007 190). A selection count sits below the title. Reset and Done remain pinned below the scrollable content, including at enlarged text sizes. Wi-Fi and power/USB are omitted because the current OTP source does not reliably expose evidence for them; existing service details still preserve any provider notices. Climate uses the official MÁVPlusz planner condition ESZKOZ_KERESESI_FELTETEL#10907842 on initial and paginated requests, and those results retain query provenance instead of requiring a missing climate display notice. Other conditions require positive evidence on every transit leg. Provider stop-index anchors allow wider validity sections to cover a selected subjourney; overlapping negative rules still reject it. “A szűrésről” reveals data requirements, reservation/advance-notice caveats and separate country-pass/first-class rules.

Station controls are full-width rows on the panel surface, separated by a divider and swap control. They use 4dp horizontal and 12dp vertical padding around a minimum 48dp inner height, and show the field label above the station value, with a blue functional icon and space for long Hungarian names. Tapping a station row opens a single rounded inline panel with a contained text-field label, a subtle divider and flat suggestion rows. A dark scrim covers the rest of the app, including navigation, and outside taps only close the editor. Android Back, including the edge gesture and keyboard dismissal, closes the editor as well. Its autocomplete expands within the search card directly below that field, spans the content width, shows up to four rows and scrolls through the remaining matches. Rows grow with the body text scale and pair provider names with town, rail or bus icons. Exact town aggregates lead, followed by that town’s bus and railway terminals, then locally frequent selections, with provider order retained for ties. Device-local query results persist across restarts, appear immediately and refresh after seven days; cached matches remain usable when refresh fails. Closing or selecting dismisses editing and clears focus. Appearance settings use one selectable native radio row per option, sharing one touch target without a contrasting inner rectangle. Settings present read-only data-source information rather than a backend URL field. Errors stay visible as text using the Material error role. Preserve native focus, labels and keyboard handling.

Each station field exposes a small themed X with a 48dp native target and a Hungarian accessibility label when nonempty, clearing only that endpoint and any typed query while retaining editing focus, with an upward chevron closing the editor separately.

The departure autocomplete offers one flat “Jelenlegi helyzet” row with the Material MyLocation icon before stop suggestions. Only tapping this row requests location permission and a one-shot fix; approximate access works too. Progress replaces the row icon and label, and recoverable errors appear inline. The permission prompt hiding the keyboard does not dismiss the picker. A successful fix selects coordinates and closes editing; leaving the picker cancels pending location work. Captured positions are not saved to station history or recent searches.

The planning date/time control uses surfaceContainerLow with 20dp corners. Date selection uses a rounded Material date dialog, followed by a rounded time dialog with 24-hour numeric entry. The pass editor is a scrollable rounded bottom sheet; type chips, date buttons and the save action follow the same native control family.

### Chips

Transport filters are pill-shaped Material filter chips with a tonal selected state and an outlined unselected state. Route badges pair a tonal surface and compact service number with a vehicle icon on the provider route color. Icon contrast chooses black or white from luminance; missing colors use the theme neutral. MÁVPlusz OTP route.color and trip.trainCategoryName supply service colors and category labels rather than guessing them from numeric route names. Vehicle filters and route badges wrap in FlowRows.

### Navigation

Four persistent destinations use icons and Hungarian labels: Tervezés, Térkép, Bérletek and Beállítások. Phone navigation is a custom native Compose row inside a solid, opaque rounded milky surface; expanded layouts use the native rail. Each equal-width item is selectable with `Role.Tab` semantics. The active background covers the complete icon-and-label item using primary-container alpha (.65), with both icon and label tinted primary; inactive items are transparent with on-surface-variant content. Icons are (24dp), followed by a gap (5dp) and centered label-medium text. Back navigation uses `PredictiveBackPane`: gesture progress previews the previous screen and scales the current pane by up to .08 while translating it by up to .14 of its width away from either swipe edge. It commits on completion, resets on cancellation, and disables pane transformations when system animators are disabled. Native modal sheets retain their own back behavior. Subordinate screens also expose a visible back action. Labels remain visible and may wrap to two lines.

### Settings

Beállítások opens with a compact list linking to Megjelenés, Értesítések, Frissítések and Az alkalmazásról. Home summaries show the current theme, Android notification availability, automatic update checking, and app version. Flat rows share solid 28dp rounded groups, with 20dp horizontal/16dp vertical row padding, title-medium labels, body-medium summaries and directional or external-link icons. Explanations belong on the destination screens. This is a local composition within the existing Android world, with no separate palette or token scale.

Appearance offers System, Light and Dark as native radio rows. Notifications opens Android's app notification settings and refreshes availability on resume. The redundant Data and history destination is removed; search history can still be cleared from Tervezés → Legutóbbi, beside the history itself. About introduces MenetrendGO with its installed version, journey planning, live vehicle map and pass reminders, followed by data provenance and its unofficial status. It has no source-project link and retains the Developer destination; Developer Back returns to About. The default-off raw-data switch uses one semantic toggle row. Settings retain visible Back actions, `PredictiveBackPane`, native text scaling and measured bottom-navigation clearance.

Notifications keeps a compact reminder group with “Értesítés 9 órakor.” and three independent, full-row native switches without redundant descriptions. The expiry follow-up explicitly says “1 nappal a lejárat után”. When Android blocks notifications, a compact notice preserves the saved-choice explanation and offers “Beállítások megnyitása”; otherwise the Android settings row remains available. The footer retains only the battery-saving delay caveat. Reminder scheduling and stored preferences remain unchanged.

### Updates

Frissítések is the last row of the main Settings top group, directly below Adatok és előzmények; its visible and predictive Back actions return to Settings home. It reuses solid rounded settings groups, the native semantic switch and radio rows, full-width pill actions and Material error text. Automatic checking starts enabled without an opt-in prompt; the default channel is Stabil. A compact status panel shows installed version, checking/result/error text and the manual check action. One settings group holds automatic checking without supporting copy and a channel-summary row. Only an installed beta build shows Elérhető verziók, which expands all published installable beta releases and the latest stable release inline; selecting the beta channel on a stable build does not reveal it. The channel chooser uses Stabil and Béta radio rows. Choosing Béta opens a separate consent drawer explaining testing risks, possible failures, travel-data verification and the limits of returning to stable. Confirmation changes the saved channel; dismissal or Mégse returns to the chooser without changing it. Consent actions stay below the scrollable explanation. Béta still includes stable releases. There is no A frissítésről disclosure. Downgrade warnings appear in the release sheet when selecting an older version. The release sheet is scrollable and uses the existing rounded Material surface, blue version heading, release notes, explicit beta acknowledgement, progress and retry/install actions. “Emlékeztess holnap” defers that update for 24 hours; selected version switches use “Mégse”. Android owns installation permission and final installation approval. These are local compositions within the existing native world, with no new tokens or visual identity.

### Developer data viewer

The default-off “Nyers GraphQL adatok” developer setting reveals a top-right overflow menu on journey details. “Nyers adatok” opens a native bottom sheet with selectable, wrapping monospace JSON, copy and close controls. Formatting runs off the main thread and rows are lazy. The viewer describes the complete response containing the selected journey; copy uses the exact original body, preserved before normalization and retained with that journey's search batch. Raw responses stay in memory; only the toggle is saved locally.

### Map

The default map uses the official Google Maps SDK for Android. Railway and custom tile layers retain locally bundled Leaflet HTML in a WebView. Both fill the screen without zoom buttons and pairing touch zoom gestures (pinch, double-tap, and double-tap with vertical swipe) with a contextual native location button. Top-left circular surface controls stack the map-layer button (“Térképtípus”) above the vehicle-filter button (“Járműszűrők”), while the top-right search pill expands leftward up to the top-left control column. The layer sheet selects base tiles (or a developer-enabled custom XYZ URL); the filter sheet toggles individual transport modes (Vonatok, Helyközi autóbuszok, Helyi autóbuszok, HÉV, TramTrain and Vonatpótló autóbuszok) with semantic native switch rows on 24dp rounded surfaces. Native navigation floats above the map; attribution stays clear of its measured inset. Google Maps retains the shared native top and bottom feathering. The Leaflet WebView bypasses display-list backdrop recording to preserve tile rendering. Google business, attraction and built-in transit icons are hidden so the app’s live transit layer remains prominent. Geographic clustering replaces overlapping country-scale vehicle labels; individual targets keep a (48px) hit wrapper. Clusters are count circles; individual markers show the app's own mode vehicle glyph on a mode-colored circle at every zoom, with a white outline, compact shadow and an optional heading arrow. Native markers reuse the bundled glyph paths without the filter artwork’s square backgrounds. Clustering remains active at street zoom, while focused route maps keep individual vehicles. Service numbers and names appear in the drawer and accessible marker labels. Color and contrast match native route badges. Location centers once on entry, updates while foregrounded, and exposes recenter only when outside the unobscured viewport. Vehicle taps open a native modal bottom drawer, with origin → destination, upcoming stops, highlighted next stop, and red struck-through scheduled arrival above a differing live time. Android Back dismisses the drawer before leaving the map. Routes use primary blue. Google marker artwork uses dp with a 48dp hit bitmap. Leaflet pixel values remain local map implementation units. Native stop selection uses the same world-pixel priority and complete-group visibility rules as Leaflet, with rounded geographic enclosures, shared theme-aware labels rendered above street names as screen-facing markers below stop circles and vehicles, multicolor mode rings and nearest-platform tap resolution.

### Get Off Alerts

Leszállási jelzés extends the existing native Operate surface. Its scrollable `AppBottomSheet` retains native typography, wrapping, 48dp actions and permission, error and recovery states. Setup explains the destination circle in Hungarian instead of offering warning-minute choices, and management shows its radius. The active transit leg uses a noninteractive circle on the main map and its matching journey map, with a themed primary outline and 12% fill. Radii are 100m for local services, 150m for coaches and replacement buses, and 250m for rail and HÉV. The circle clears during transfers and when tracking stops. Entry warnings use geographic distance independently of route matching, with location checks requested every 20, 10, 5 or 2 seconds as the circle edge approaches. Route progress may support status, while location uncertainty remains explicit. This adds no palette, type scale or control family.

### Stop maps, departure boards and home-screen widgets

The service-visibility filter drawer (Járműszűrők) includes a persistent, initially enabled “Megállók és állomások” native switch. Stop markers are 14px milky circles with mode-colored rings inside keyboard-accessible 48px targets. Viewport stops load and appear only at close street-level zoom (16 or closer). A deterministic priority-based collision pass in world-pixel coordinates selects seed markers at 24px spacing. Groups form from the complete loaded stop set before selection. If any member passes collision selection and any member intersects the padded viewport, every member is rendered, including closely spaced or offscreen members. The viewport request includes a further 300m margin to load nearby group members. Overlapping pointer targets resolve to the nearest circle, while keyboard activation retains the focused provider identity. Nearby stops with the same normalized full name share a rounded gray enclosure (#737B80, 1.5px outline at 55% opacity and 12% fill) and one theme-aware label. Enclosures pad the visible circle centers by 18px. Every member must be within 300m of every other member, preventing distant namesakes and chained groups from joining. Grouping is visual and preserves each stop's departure board. Provider IDs break ties so feed order and panning do not reshuffle the same candidate set. Stop taps open the board directly without tooltips. Raster basemap icons cannot participate in this collision pass. Station platforms group under their provider parent station, while directional street stops retain their exact provider identity. Tapping opens the shared native `AppBottomSheet`: a wrapping station heading and close action lead into update status, manual refresh, a widget action and a scrolling departure list. Departure entries use the Indulások card family: 32dp rounded surface cards, 20dp internal padding and 16dp list gaps. Official logo badges omit their leading container padding so their visible edge aligns with the destination. Cards place route badges, service labels and direction on the left, with a prominent “x perc” countdown or “Most” (under one minute) on the right, without visible scheduled/live labels. For departures strictly more than 15 minutes away, a smaller regular-weight Budapest-local departure time appears beneath the countdown; it follows the same estimated departure and disappears at the threshold or for cancellations. Countdown colors are black for scheduled data (white in dark mode), green for live on-time/early data, amber-yellow for positive live delays under 10 minutes and red for live delays of at least 10 minutes; screen-reader state descriptions retain the data status. They retain direction text and explicit cancellation status; unknown platforms are omitted. Loading, empty and retryable error states remain textual, and refresh failures retain earlier departures. Automatic refresh runs every 30 seconds while the drawer is foregrounded.

The resizable Android home-screen widget carries the same rounded blue/ink surface family through native light/dark resources and scalable text. It uses the same countdown-only color rules and relative freshness text without visible live/scheduled labels, with a 12sp regular-weight departure time beneath countdowns strictly above 15 minutes, the same service-left/countdown-right hierarchy and route-mark glyph outlines and provider colors as the drawer, rendered from the shared bundled-font outline. It shows its station, last update or failure status, compact departures and an explicit 48dp refresh action; the board body opens that widget's exact station drawer. Each instance stores its own provider identity, absolute departure timestamps and route-mark metadata. A non-waking inexact minute timer redraws cached countdowns locally; Android may defer these ticks. Widgets redraw after an app upgrade and resume scheduling after reboot. The header has compact 8dp vertical insets and relative freshness only; refresh intervals are hidden in both drawer and widget. Widget departures sit on 24dp rounded lifted surfaces over the blue ground, with 16dp internal padding and 12dp gaps. Service, destination and platform have distinct text lines and 8dp/4dp internal spacing. Logos use content-sized, start-aligned image bounds, flush with the destination; the artwork retains its proportions. Each fetch stores the next ten departures. Native row measurement accounts for widget width/height, wrapping and font scale, with no five-row cap. Cards retain fixed padding at every size. On Android 12 and later, each launcher-provided size gets its own fitted layout; older hosts use orientation-specific bounds. The wrap-content panel ends after the last complete card rather than stretching cards into unused launcher space. Departed services roll off and reveal the reserve locally. Network refresh is due five minutes after the last successful fetch or when the non-cancelled reserve is exhausted, with a ten-minute scheduling deadline. Opening the board from the widget and manual refresh request an immediate fetch. Eligible/running jobs coalesce repeated requests. Departed rows disappear immediately; failed fetches back off at least one minute. Background job delivery remains controlled by Android; the foreground drawer retains its independent 30-second feed. This extends the existing design authority without introducing a new palette or control family.

## Do's and Don'ts

The vehicle-model drawer is a Read surface within the native Material sheet. Its offline article presents “A járműről” for general information, then “Érdekesség” or “Érdekességek” for sourced curiosities. Both headings use bold title-large text, a 24dp top gap and 12dp bottom gap, followed by body-large copy. The existing proportional photo pager and source disclosure remain; the current photo's credit labels resizing and links to the license. Galleries contain up to three distinct verified MÁV/Volán photos, with an explicit missing-photo state where none could be verified.

### Do:

- **Do** use theme role pairs for every surface and its text in light and dark modes.
- **Do** retain the fully rounded family and restrained milky tonal depth.
- **Do** keep Hungarian names and labels readable through wrapping, font scaling and scrolling.
- **Do** use semantic native controls and meaningful Hungarian labels for functional vector icons.
- **Do** distinguish scheduled, live, stale, unavailable and unknown states with explicit text.

### Don't:

- **Don't** flatten interface text, controls or navigation into image assets.
- **Don't** use heavy glass blur that compromises information legibility.
- **Don't** promote mockup sample times, badges or system chrome into product facts.
- **Don't** substitute map marker styling for the native application color and elevation system.


Journey time changes: one shared native time component presents scheduled versus live values in departures, journey timelines and the vehicle drawer. Changed scheduled times are red and struck through; the estimate follows underneath in normal content color. A date is shown for each time when a change crosses midnight. “Élő érkezés” is omitted.

Route marks and service pictograms never ship provider or operator artwork. `res/font/transit_pictograms.ttf` ("MenetrendGO Pictograms", generated by `scripts/generate_pictograms.py`) keeps the provider's MNR2007 code points and each glyph's bounding box, so layout is unchanged, but every outline is original: line and category badges are Barlow Semi Condensed lettering (SIL OFL, licence in `assets/transit-pictograms-LICENSE.txt`) on our own rounded badge shapes, long-distance marks (EC, IC, RJ…) are heavy oblique lettering, operator logos (MÁV, HÉV, VOLÁN, GYSEV, Westbahn, eurostar…) are plain names, and pictograms are geometric drawings. Edit the generator, never the font, and regenerate.

Route badges preserve the provider's MNR2007 logo from `route.shortName` before text normalization. Departure cards, journey details, service panels and vehicle drawers use the exact bundled-font category or line glyph, with the provider's `textColor` and `color` pair (including black-on-yellow Z marks). These colors are independent of map-marker category colors. Logo badges have no enclosing background; train numbers and names use the theme's on-surface color. The logo keeps its provider background color, filled within the exact exterior contour of the glyph. Rendering uses visible glyph bounds rather than text advance or baseline padding. Badge labels retain the full train number, category and name supplied by the provider (for example, “4515 személyvonat”); vehicle drawers omit the separate category subtitle when a logo is present. Pictograms retain a fixed optical size while labels scale and wrap. Accessibility announces the Hungarian service category and route, hiding the font character. Missing or malformed logo/color data retains the ordinary mode icon badge.

Departure results use a full-width, transparent “Korábbiak mutatása” text button above the services. Loading an earlier page preserves the visible service and its scroll position; earlier results can be reached by scrolling upward. Departed services have a dashed outline. Only after the earlier-services button has been used, a red “Most” divider marks the boundary between departed and upcoming services using the current boarding time. The lower pagination button loads later departures for both departure-time and arrival-time searches.

Departure card footers show at most six distinct provider amenity pictograms in this priority order: mandatory seat reservation/supplement, country-pass acceptance, buffet/bistro/dining, accessibility, bicycle reservation, bicycle carriage, sleeper/berth, Wi-Fi, power/USB, air conditioning, family facilities, quiet accommodation, first/premium class, other pass acceptance, optional reservation/exemptions, then luggage/toilets/pets. Unclassified information and operating notes stay in journey details. Rank before deduplicating and limiting; retain every matching service's original route and validity section in the explanation. The footer is one accessible, full-width target of at least 48dp height opening “Szolgáltatások”. Its symbols use a single row with slots up to 56dp and scale down together from 24dp when space is tight; pictograms do not grow with text scaling. Detail descriptions retain native text scaling. The full service list remains available per leg in journey details.

The journey route map is a separate subordinate Operate screen. It fills the available width, with a native back control at top left and attribution above system navigation. It has no main navbar, no user-location request and no unrelated vehicle markers. Match selected non-walking trip IDs on their travel date, retain all transfer vehicles, fit route plus available positions initially, and refresh vehicle positions every 75 seconds while foregrounded. Walking sections are dashed. Back returns to the same journey; the all-vehicle Térkép tab remains independent.

The Bérletek screen is an Operate surface focused on expiry. A title and compact Hozzáadás action lead into current/upcoming passes sorted by expiry; the empty state has one full-width add action. Compact rounded cards show a wrapping pass name, validity status and expiry date. Upcoming cards also show their start date; other start dates remain in the editor. A labeled overflow menu holds edit and delete, retaining delete confirmation. Expired passes sit behind a collapsed, counted disclosure. Country and county passes use matching public-domain SVG contours by PM from Wikimedia Commons, converted to native outline vectors for Hungary and all 19 counties. All twenty use the same proportional sizing and stroke. Artwork occupies a fixed 40dp slot, follows the theme foreground and is decorative beside the accessible name. Local passes retain the city icon. Use megye and megyebérlet in screen copy. Imminent expiry (today through three days away) uses the error role; ordinary status and expired history use neutral ink. A compact Emlékeztetők row reports the actual reminder/system state and opens the schedule, existing reminder switches and Android notification settings. A visible Nem utazási igazolvány action opens the full notice and official conditions link. Dates use Hungarian month abbreviations. The full-height-capable, scrollable pass sheet groups type and validity controls, with native wrapping chips for Ország, Megye and Helyi. Local passes require a city name and use the same device-local storage and reminders. Their service acceptance stays unconfirmed. “30 napos” is the new-pass default: both displayed days count, so changing either date moves the other by 29 days. “Egyéni” leaves the dates independent and blocks reversed ranges. Existing saved dates are retained. Sheet state and draft values survive Android recreation; the editor respects keyboard insets and has an explicit close action.

Journey endpoint labels follow time → bold town → stop name and a compact primary-colored platform badge, with departure aligned left and arrival aligned right. The town uses a compact 20sp line height above a single-line stop name, which ellipsizes when space is insufficient. Split only the first comma in provider names; unseparated names stay intact. Platform badges use provider platformCode, hide unknown values, and announce Vágány / kocsiállás to accessibility. Journey timeline endpoints instead wrap the stop name and show explicit vágány / kocsiállás text below it. Transfers with different platforms retain separate arrival and departure rows. Intermediate stops use an inline **town,** stop name: town and comma stay bold in primary ink, with regular-weight secondary ink for the stop at the same title-medium size. These labels wrap naturally when needed, and unseparated names have no added comma.

The itinerary is an Operate surface with departure and arrival as its strongest anchors. Between them, the route and direction form a close group. Duration appears once per leg in the time gutter between departure and arrival, with no duplicate in the page subtitle, and the category is omitted when it already appears in the route name. Vehicle model, registration, provider notices and live-data availability remain visible beneath it. Models are read from the exact dated trip response when available, with the matched live-map vehicle as fallback. An absent model adds no placeholder row. Intermediate stops start collapsed, with smaller markers connected to the same service-colored track and explicit platform text. Their counted disclosure retains its expansion state through recreation. Each eligible service leg carries its own full timetable action inside its block on the track, after the intermediate-stop disclosure and before the leg's arrival, so transfer journeys never repeat route badges below the timeline. The route map action alone follows the final arrival, separated from the connected timeline by a divider. Both use matching full-width text buttons with 20dp leading icons, wrapping labels, trailing chevrons, 48dp minimum targets and 8dp between actions. Timeline rows use 16dp separation, 12dp between intermediate stations and 16dp before arrival, and 4dp within service metadata. The final arrival has no trailing row gap. The font-aware time gutter starts at 56dp, and all station names and action labels wrap. Disclosure and timetable actions have 48dp minimum targets, respectively 16dp and 8dp vertical content padding, no horizontal content inset and extra-small themed corners so multiline labels remain inside their clipping bounds. Station names, service details and action labels share their leading edge, with matching 20dp trailing chevrons. The direction row has a 12dp bottom gap and its arrow centers on the first text line, including enlarged text. Arrival separation also applies when no timetable action is available.


## Dark and OLED appearance

Dark mode uses cool charcoal, soft white text, slate secondary text and blue actions. Background #111317, panel #1B1E24 and stepped Material surface containers keep dense timetables distinct without a blue wash. Every Material role is specified, including secondary, tertiary, error, inverse and outline pairs.

Appearance offers Rendszer szerint, Világos, Sötét and OLED sötét. OLED is an explicit dark choice with #000000 canvas, subtly lifted #111317 cards and panels, neutral raised containers and transparent surface tint. Cards remain visible against the black background. Selection persists and refreshes widgets immediately. Widgets use matching dark text and cards with a pure-black OLED shell. System bar icons, departure countdowns, native map controls and WebView attribution/status surfaces follow the selected theme. Map imagery and transit pictograms retain provider colors. Light mode is unchanged.

Journey provider notices use one compact, 48dp-minimum row per leg, labelled "x figyelmeztetés". Its icon reflects the most severe included notice. Tapping opens a scrollable native sheet containing every notice in provider order, including its full title, validity, formatted description and provider link. Dividers separate notices, and closing returns to the same itinerary. Individual message titles stay inside the sheet.

Expanded intermediate stops show their departure time in the left timeline gutter, with arrival fallback when departure is absent. The shared scheduled/live time rendering retains delay strike-through and cross-midnight dates. Times come from the exact trip and ridden endpoint occurrences, with provider stop IDs and timetable order distinguishing repeated stops. The journey refresh supplies them without additional requests. Stops with no verified timing retain an empty gutter.

The full trip timetable is an Operate surface sharing `TimetableStopRow` with live vehicle drawers: a service-colored connected track, prominent endpoint markers, compact intermediate markers, wrapping stop names, mode-specific platform text and the shared scheduled/live time rendering. It retains every provider stop in order, including stops without timing, and uses departures with arrival fallback at the terminus. Non-live times carry the matching Menetrendi indulás or Menetrendi érkezés label. The vehicle drawer retains its upcoming-stop filter and Következő megálló emphasis. Full timetable stops remain lazy, with no individual stop cards or gaps between connected rows.

Full trip timetables omit the vehicle-position status paragraph and separate map button. A mode-specific vehicle icon sits on the connected track at the reported stop, or between the preceding and upcoming stops when travelling. Its 48dp action opens the vehicle map. Only a matching provider stop relationship places the icon, including disambiguation of repeated stops. Missing or unknown position evidence leaves the timetable without a vehicle marker.

The full timetable vehicle marker uses a filled circular service-color button, a 2dp tonal rim and a contrast-selected icon on a wider track lane. The visible marker is 24dp across with a 12dp icon, within a 48dp tap target. Completed track segments retain the service color, while segments ahead of the reported vehicle use its translucent tint. Stop rings stay in the service color. Marker, rim and track derive from existing app service colors and theme surfaces, with no fixed blue. The live-map drawer keeps its compact timeline spacing.

Vehicle progress has two explicit timetable states. STOPPED_AT replaces that station’s dot. IN_TRANSIT_TO and INCOMING_AT place the compact marker halfway between the preceding and target station dots, based on the rendered row height, with no distance interpolation. The preceding row reserves sufficient height for the 48dp tap target. Without a preceding stop, a travelling vehicle is not rendered as stopped at the first station.
