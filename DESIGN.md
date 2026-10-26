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
  dark-primary: "#A8C7FF"
  dark-on-primary: "#092B64"
  dark-background: "#101D2A"
  dark-ink: "#E6F0FF"
  dark-surface: "#192B3D"
  dark-surface-variant: "#253B51"
  dark-muted-ink: "#B8CAE0"
  dark-primary-container: "#294C7C"
  dark-on-primary-container: "#D6E6FF"
  dark-outline: "#8CA2BB"
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
    rounded: "{rounded.medium}"
---

# Design System: MenetrendGO

Journey legs and full trip timetables request MÁV `infoServices`. Journey services occupy a full-width panel after the itinerary, headed by the route badge and selected endpoints. Original MNR2007 pictograms align in a fixed leading column beside wrapping Hungarian descriptions and original validity sections, grouped into onboard facilities and ticket/pass rules; secondary operational notices expand separately. Indulások cards end with a divider and official acceptance/reservation icons, regardless of saved passes. Tapping an icon reveals all matching rules, routes and sections. Different sections remain separate, including reservation exemptions alongside mandatory-reservation notices. Missing facilities remain unknown. The bundled official font's glyphs are hidden from accessibility text and announced using service descriptions.

## Overview

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
- **Vehicle filter identities**: the filter sheet uses 40dp original MÁVPlusz mode pictograms, converted to bundled Android vector drawables from the saved `main.56c42ecd9e04ed9c.js` source. Rail is navy (`#003A7E`), coaches yellow (`#FDC300`), local buses blue (`#009EE3`), HÉV green (`#005E3B`), TramTrain orange (`#F29101`), and replacement buses magenta (`#C4146E`) with the original yellow/white combined rail-and-bus symbol. These are the mode selector pictograms, distinct from map marker/category colors. Preserve their artwork in both themes and switch states; labels and native switches communicate category and selection independently of color. Filter behavior and provider route colors remain unchanged.
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

At the implemented wide-layout threshold (700dp), bottom navigation becomes a navigation rail. Main utility content is centered with a maximum width (760dp); the map can expand further (1600dp). The phone navigation surface has horizontal margins (12dp), vertical margins (6dp), inner padding (6dp) and gaps (4dp). Each item's inner content has a minimum height (48dp), growing to (64dp) above the font-scale threshold (1.15), with separate item padding (2dp horizontal, 8dp vertical). Labels may occupy two lines. Scrollable pages extend behind the floating navigation surface; its measured bottom inset is added to list content padding so final actions can scroll clear. The map retains safe control bounds. Safe drawing insets, navigation-bar padding and IME padding belong to the layout.

The planning surface opens with “Hová indulunk?” and the reminders bell on one row, followed by search and recents; there is no separate wordmark row. Results open as their own scrollable departure list. Future screens should follow the same navigation conventions.

## Elevation & Depth

The status bar is transparent. Maps and scrolling pages extend behind it, with initial list padding and map controls respecting the top safe-drawing inset. The top inset plus 24dp uses the same feathering as the bottom, mirrored vertically: an 18dp backdrop blur on Android 12+, a mask opaque through fraction .45 then fading to transparent, and a theme-background fade from alpha .82 to zero. Android 9–11 use the same tonal fallback as the bottom. Native pages share one full-page blurred display list, cropped only after blurring with transparent out-of-bounds sampling to avoid stretched edge bands, and crossfaded against the sharp page so dark content is not doubled; the map applies the equivalent CSS backdrop blur and mask inside its WebView to keep tiles live. System icons remain sharp. Expanded layouts retain safe rail controls; landscape display cutouts retain horizontal safe insets.

Depth comes from solid themed surfaces and tonal separation. The reusable panel is still named `Glass` in code, but uses an opaque surface with no gradient or shadow. Result cards and the opaque bottom navigation also have no custom shadow.

Phone content extends behind the floating bar, with no fixed background strip. The bottom slice of the shared display-list blur (18dp on Android 12+) is clipped to the navbar surface top edge (the measured navigation inset minus its 6dp top margin). Its alpha mask goes from transparent at the top to opaque at fraction .55, with a background-color fade from alpha 0 to .82. Navigation controls remain sharp. Android 9–11 use the tonal fade fallback; rail layouts have no bottom veil.

The map retains its own compact marker shadows; those CSS shadows do not define native Compose elevation. Dialogs, sheets, disabled states and pressed feedback continue to use Material behavior.

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

Solid `Glass` panels group related controls or explanations, with 20dp internal padding. Departure cards place wrapping service badges at the top left, followed by equally bold departure/arrival times with station names beneath and an arrow between. Supporting date/duration text follows. Only available live data adds a status label; the scheduled-time label is omitted. Detail surfaces fill the available content width. Full stop-list time columns use `64.sp.toDp()` through the current density to grow with font scale. Itineraries use a connected vertical timeline with solid provider-colored transit lines, dotted walking lines, time/stop nodes, transfer waits and expandable intermediate stops. Recent-search rows use solid surfaces and medium rounded corners, a history icon, two place names and a chevron.

### Inputs / Fields

The “Keresési feltételek” drawer uses wrapping Material filter chips grouped as Utazás, Kocsiosztály and Szolgáltatások. Each chip has a bundled MNR2007 pictogram, a wrapping label, native selected semantics and color-only visual selection; touch targets remain at least 48dp. Chips have 8dp horizontal and vertical gaps and 8dp below group headings. Kocsiosztály offers 2., 1. and 1+; 1+ matches the Prémium osztály provider notice (MNR2007 190). A selection count sits below the title. Reset and Done remain pinned below the scrollable content, including at enlarged text sizes. Wi-Fi and power/USB are omitted because the current OTP source does not reliably expose evidence for them; existing service details still preserve any provider notices. Climate uses the official MÁVPlusz planner condition ESZKOZ_KERESESI_FELTETEL#10907842 on initial and paginated requests, and those results retain query provenance instead of requiring a missing climate display notice. Other conditions require positive evidence on every transit leg. Provider stop-index anchors allow wider validity sections to cover a selected subjourney; overlapping negative rules still reject it. “A szűrésről” reveals data requirements, reservation/advance-notice caveats and separate country-pass/first-class rules.

Station controls are full-width rows on the panel surface, separated by a divider and swap control. They use 4dp horizontal and 12dp vertical padding around a minimum 48dp inner height, and show the field label above the station value, with a blue functional icon and space for long Hungarian names. Tapping a station row opens a single rounded inline panel with a contained text-field label, a subtle divider and flat suggestion rows. A dark scrim covers the rest of the app, including navigation, and outside taps only close the editor. Android Back, including the edge gesture and keyboard dismissal, closes the editor as well. Its autocomplete expands within the search card directly below that field, spans the content width, shows up to four rows and scrolls through the remaining matches. Rows grow with the body text scale and pair provider names with town, rail or bus icons. Exact town aggregates lead, followed by that town’s bus and railway terminals, then locally frequent selections, with provider order retained for ties. Device-local query results persist across restarts, appear immediately and refresh after seven days; cached matches remain usable when refresh fails. Closing or selecting dismisses editing and clears focus. Appearance settings use one selectable native radio row per option, sharing one touch target without a contrasting inner rectangle. Settings present read-only data-source information rather than a backend URL field. Errors stay visible as text using the Material error role. Preserve native focus, labels and keyboard handling.

The planning date/time control uses surfaceContainerLow with 20dp corners. Date selection uses a rounded Material date dialog, followed by a rounded time dialog with 24-hour numeric entry. The pass editor is a scrollable rounded bottom sheet; type chips, date buttons and the save action follow the same native control family.

### Chips

Transport filters are pill-shaped Material filter chips with a tonal selected state and an outlined unselected state. Route badges pair a tonal surface and compact service number with a vehicle icon on the provider route color. Icon contrast chooses black or white from luminance; missing colors use the theme neutral. MÁVPlusz OTP route.color and trip.trainCategoryName supply service colors and category labels rather than guessing them from numeric route names. Vehicle filters and route badges wrap in FlowRows.

### Navigation

Four persistent destinations use icons and Hungarian labels: Tervezés, Térkép, Bérletek and Beállítások. Phone navigation is a custom native Compose row inside a solid, opaque rounded milky surface; expanded layouts use the native rail. Each equal-width item is selectable with `Role.Tab` semantics. The active background covers the complete icon-and-label item using primary-container alpha (.65), with both icon and label tinted primary; inactive items are transparent with on-surface-variant content. Icons are (24dp), followed by a gap (5dp) and centered label-medium text. Back navigation uses `PredictiveBackPane`: gesture progress previews the previous screen and scales the current pane by up to .08 while translating it by up to .14 of its width away from either swipe edge. It commits on completion, resets on cancellation, and disables pane transformations when system animators are disabled. Native modal sheets retain their own back behavior. Subordinate screens also expose a visible back action. Labels remain visible and may wrap to two lines.

### Settings

Beállítások opens with a compact list linking to Megjelenés, Értesítések, Adatok és előzmények and Az alkalmazásról. Home summaries show the current theme, Android notification availability, saved-search count or empty state, and app version. Flat rows share solid 28dp rounded groups, with 20dp horizontal/16dp vertical row padding, title-medium labels, body-medium summaries and directional or external-link icons. Explanations belong on the destination screens. This is a local composition within the existing Android world, with no separate palette or token scale.

Appearance offers System, Light and Dark as native radio rows. Notifications opens Android's app notification settings and refreshes availability on resume. Data requires confirmation before clearing search history, preserves passes and disables the action when history is empty. About introduces MenetrendGO with its installed version, journey planning, live vehicle map and pass reminders, followed by data provenance and its unofficial status. It has no source-project link and retains the Developer destination; Developer Back returns to About. The default-off raw-data switch uses one semantic toggle row. Settings retain visible Back actions, `PredictiveBackPane`, native text scaling and measured bottom-navigation clearance.

### Updates

Frissítések is the last row of the main Settings top group, directly below Adatok és előzmények; its visible and predictive Back actions return to Settings home. It reuses solid rounded settings groups, the native semantic switch and radio rows, full-width pill actions and Material error text. Automatic checking starts enabled without an opt-in prompt; the default channel is Stabil. A status panel shows installed version, checking/result/error text and the manual check action, followed by automatic-check/channel controls and version management. The release sheet is scrollable and uses the existing rounded Material surface, blue version heading, release notes, explicit beta acknowledgement, progress and retry/install actions. “Emlékeztess holnap” defers that update for 24 hours; selected version switches use “Mégse”. Android owns installation permission and final installation approval. These are local compositions within the existing native world, with no new tokens or visual identity.

### Developer data viewer

The default-off “Nyers GraphQL adatok” developer setting reveals a top-right overflow menu on journey details. “Nyers adatok” opens a native bottom sheet with selectable, wrapping monospace JSON, copy and close controls. Formatting runs off the main thread and rows are lazy. The viewer describes the complete response containing the selected journey; copy uses the exact original body, preserved before normalization and retained with that journey's search batch. Raw responses stay in memory; only the toggle is saved locally.

### Map

The map is locally bundled Leaflet HTML in a WebView, filling its screen without zoom buttons and pairing touch zoom gestures (pinch, double-tap, and double-tap with vertical swipe) with a contextual native location button. Top-left circular surface controls stack the map-layer button (“Térképtípus”) above the vehicle-filter button (“Járműszűrők”), while the top-right search pill expands leftward up to the top-left control column. The layer sheet selects base tiles (or a developer-enabled custom XYZ URL); the filter sheet toggles individual transport modes (Vonatok, Helyközi autóbuszok, Helyi autóbuszok, HÉV, TramTrain and Vonatpótló autóbuszok) with semantic native switch rows on 24dp rounded surfaces. Native navigation floats above the map; attribution stays clear of its measured inset. Its WebView bypasses display-list backdrop recording to preserve tile rendering. Geographic clustering replaces overlapping country-scale vehicle labels; individual targets keep a (48px) hit wrapper. Clusters are count circles; individual markers show a vector vehicle icon on a service-colored circle at every zoom. Service numbers and names appear in the drawer and accessible marker labels. Color and contrast match native route badges. Location centers once on entry, updates while foregrounded, and exposes recenter only when outside the unobscured viewport. Vehicle taps open a native modal bottom drawer, with origin → destination, upcoming stops, highlighted next stop, and red struck-through scheduled arrival above a differing live time. Android Back dismisses the drawer before leaving the map. Routes use primary blue. HTML pixel values remain local map implementation units.

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

Route badges preserve the provider's MNR2007 logo from `route.shortName` before text normalization. Departure cards, journey details, service panels and vehicle drawers use the exact bundled-font category or line glyph, with the provider's `textColor` and `color` pair (including black-on-yellow Z marks). These colors are independent of map-marker category colors. Logo badges have no enclosing background; train numbers and names use the theme's on-surface color. The logo keeps its official background, filled within the exact exterior contour of the glyph. Rendering uses visible glyph bounds rather than text advance or baseline padding. Badge labels retain the full train number, category and name supplied by the provider (for example, “4515 személyvonat”); vehicle drawers omit the separate category subtitle when a logo is present. Pictograms retain a fixed optical size while labels scale and wrap. Accessibility announces the Hungarian service category and route, hiding the font character. Missing or malformed logo/color data retains the ordinary mode icon badge.

Departure results use a full-width, transparent “Korábbiak mutatása” text button above the services. Loading an earlier page preserves the visible service and its scroll position; earlier results can be reached by scrolling upward. Departed services have a dashed outline. Only after the earlier-services button has been used, a red “Most” divider marks the boundary between departed and upcoming services using the current boarding time. The lower pagination button loads later departures for both departure-time and arrival-time searches.

Departure card footers show at most six distinct provider amenity pictograms in this priority order: mandatory seat reservation/supplement, country-pass acceptance, buffet/bistro/dining, accessibility, bicycle reservation, bicycle carriage, sleeper/berth, Wi-Fi, power/USB, air conditioning, family facilities, quiet accommodation, first/premium class, other pass acceptance, optional reservation/exemptions, then luggage/toilets/pets. Unclassified information and operating notes stay in journey details. Rank before deduplicating and limiting; retain every matching service's original route and validity section in the explanation. The footer is one accessible, full-width target of at least 48dp height opening “Szolgáltatások”. Its symbols use a single row with slots up to 56dp and scale down together from 24dp when space is tight; pictograms do not grow with text scaling. Detail descriptions retain native text scaling. The full service list remains available per leg in journey details.

The journey route map is a separate subordinate Operate screen. It fills the available width, with a native back control at top left and attribution above system navigation. It has no main navbar, no user-location request and no unrelated vehicle markers. Match selected non-walking trip IDs on their travel date, retain all transfer vehicles, fit route plus available positions initially, and refresh vehicle positions every 75 seconds while foregrounded. Walking sections are dashed. Back returns to the same journey; the all-vehicle Térkép tab remains independent.

The Bérletek screen sorts unexpired passes before expired ones, then by expiry. Cards pair a wrapping pass name with a labeled edit icon, separate beginning and expiry dates, and a status row with a labeled delete action. Dates use Hungarian month abbreviations. The full-height-capable, scrollable pass sheet groups type and validity controls, with native wrapping chips for Ország, Megye and Helyi. Local passes require a city name and use the same device-local storage and reminders. Their service acceptance stays unconfirmed. “30 napos” is the new-pass default: both displayed days count, so changing either date moves the other by 29 days. “Egyéni” leaves the dates independent and blocks reversed ranges. Existing saved dates are retained. Sheet state and draft values survive Android recreation; the editor respects keyboard insets and has an explicit close action.

Journey endpoint labels follow time → bold town → stop name and a compact primary-colored platform badge, with departure aligned left and arrival aligned right. The town uses a compact 20sp line height above a single-line stop name, which ellipsizes when space is insufficient. Split only the first comma in provider names; unseparated names stay intact. Platform badges use provider platformCode, hide unknown values, and announce Vágány / kocsiállás to accessibility. Journey timeline endpoints share the same labels; transfers with different platforms retain separate arrival and departure rows. Expanded intermediate stops instead use an inline **town,** stop name: town and comma stay bold in primary ink, with regular-weight secondary ink for the stop at the same title-medium size. These labels wrap naturally when needed, and unseparated names have no added comma.
