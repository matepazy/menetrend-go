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
  headline-large:
    fontFamily: "Roboto"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: "40sp"
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
    padding: "16dp 18dp"
  glass-panel:
    rounded: "{rounded.large}"
    padding: "{spacing.page}"
  result-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.large}"
    padding: "22dp"
  route-badge:
    backgroundColor: "{colors.primary-container}"
    rounded: "{rounded.pill}"
    padding: "7dp 12dp"
  bottom-navigation:
    rounded: "{rounded.extra-large}"
---

# Design System: MenetrendGO

## Overview

**Creative North Star: "Icy blue ground, ink text, royal-blue actions, fully rounded milky panels"**

MenetrendGO feels calm, legible and direct. Its slight glass character comes from pale tonal layers, a restrained vertical highlight and soft native elevation. Large rounded forms hold useful Hungarian text and familiar Material vector icons; the interface does not rely on decorative artwork.

This records the implemented Android world, adapted from the approved concept A. Roboto, Material controls, system bars and platform interaction anatomy are intentional. The native source is authoritative when a reference image differs. Source: `MainActivity.kt`, `PlanningScreens.kt`, `UtilityScreens.kt`, `NearbyScreen.kt` and bundled `map.html` under `app/src/main`.

**Key Characteristics:**

- Icy blue and milky surfaces with strong ink text.
- Fully rounded panels, fields, pills and navigation.
- Native Android typography, controls and feedback.
- Text-led journey information with explicit data status.

## Colors

The primary blue draws attention to actions and selected states; cool neutrals carry most of each screen. Frontmatter colors describe the light scheme unless prefixed `dark-`.

### Primary

- **Royal Blue** (`primary`): primary buttons, meaningful icons and route lines.
- **Pale Blue Selection** (`primary-container`, `on-primary-container`): navigation indicators, tonal actions and route-badge surfaces.
- **Dark-mode Sky Blue** (`dark-primary`, `dark-on-primary`): the corresponding primary pair in the dark scheme. Dark selection colors use the `dark-primary-container` pair.

### Secondary

- **Map Coach Teal** (`map-coach`): the bundled map's coach marker distinction.
- **Map Cluster Blue** (`map-cluster`): count clusters at wider geographic scales. These are map-specific roles, not additional application action colors.

### Neutral

- **Icy Ground** (`background`), **Milky Surface** (`surface`) and **Blue Mist** (`surface-variant`): the screen, controls and tonal glass layering.
- **Ink** (`ink`) and **Slate Ink** (`muted-ink`): principal information and supporting labels. `outline` is the explicit scheme outline role.
- The corresponding `dark-` tokens provide a deep blue ground, lifted blue surfaces and light text. Theme choice can follow Android or be set explicitly.
- Additional Material surface-container tones are defined by the native theme for dialogs and controls. Use the existing scheme roles rather than sampling a screenshot. Errors use Material's error role; no separate custom error palette is established here.

**The Role Pairing Rule.** Change surface and content colors together through `MaterialTheme.colorScheme`; a light-scheme ink or action label must not leak into its dark counterpart.

## Typography

**Display and body font:** Android's default Roboto through the unmodified Material 3 typography scale. There is no separate decorative display face. Sizes are Android `sp`, and spacing/radii are `dp`; the frontmatter preserves those native units.

Headlines are bold and sentence case. The large headline carries the planning prompt; medium headlines carry page titles and prominent departure times; small headlines introduce sheets and supporting time values. Title-large is used for the wordmark and section/card headings, with semibold or bold emphasis where the source requests it. Title-medium carries station values, action labels and key rows; selected values often use semibold. Body roles carry supporting labels, descriptions and explanatory status. Label roles carry route badges and compact navigation labels.

The frontmatter headline weights record the repeated heading treatment; individual arrival-time text can use semibold. Unspecified title/body weights retain Material defaults. Font scaling remains native, with wrapping and scrolling instead of fitting text into raster assets.

## Layout

Phone screens are single-column, vertically scrollable and inset from system bars. Most content lists use page padding (20dp) and gaps (16–20dp). The map uses a narrower horizontal inset (16dp), then fills the remaining height. Panels commonly use the page padding internally; the pass editor uses sheet padding (24dp).

At the implemented wide-layout threshold (700dp), bottom navigation becomes a navigation rail. Main utility content is centered with a maximum width (760dp); the map can expand further (1600dp). The bottom navigation has horizontal margins (12dp), and its labels may occupy two lines. Above the font-scale threshold (1.15), its minimum height grows from (80dp) to (100dp). Safe drawing insets, navigation-bar padding and IME padding belong to the layout.

The planning surface contains search and recents. Results open as their own scrollable departure list. This is the approved surface flow, not a rule that every future screen must copy the planning composition.

## Elevation & Depth

Depth is a restrained hybrid of tonal layering and native elevation. The reusable glass panel uses the current surface at alpha (.86), overlaid with a vertical gradient from surface alpha (.95) to surface-variant alpha (.32), with shadow elevation (3dp). Result cards use (2dp). The bottom navigation uses surface alpha (.96) and shadow elevation (5dp). This is translucent tonal styling, not backdrop-blurred text.

The map retains its own compact marker and zoom-control shadows; those CSS shadows do not define native Compose elevation. Dialogs, sheets, disabled states and pressed feedback continue to use Material behavior. No application-specific motion duration or easing scale has been established.

## Shapes

The native theme's shape scale runs from softly rounded small controls to large containers, as recorded in frontmatter. Reusable glass panels, result cards, dialog surfaces and the map viewport use large rounded corners (32dp). Station rows use medium corners (28dp); autocomplete rows use small corners (24dp). Bottom navigation and sheet top corners use extra-large corners (36dp). Buttons, date rows, badges and filter chips use pill geometry; circular icon controls remain circular.

**The Rounded Continuity Rule.** New controls and containers should continue the existing rounded family, including overlays and selection states.

## Components

### Buttons

The primary action is a full-width native pill with a vector icon, a title-medium label and a minimum height (58dp). Its icon is (22dp) with a label gap (10dp). Secondary outlined pills support actions such as additional departures and date selection; text actions handle lighter utilities. Retain Material enabled, disabled, focus, pressed and accessibility behavior rather than inventing desktop hover choreography.

### Cards / Containers

Glass panels group related controls or explanations. Their opacity, gradient and elevation follow Elevation & Depth. Departure cards use a solid surface, clear departure/arrival hierarchy, supporting date/duration text, wrapping route badges and a separate live-or-scheduled status label. Recent-search rows use solid surfaces and medium rounded corners, a history icon, two place names and a chevron.

### Inputs / Fields

Station controls show the field label above the station value, with a blue functional icon and space for long Hungarian names. They open a native rounded bottom sheet containing a pill outlined query field and scrollable autocomplete rows. The backend URL field uses small rounded corners. Errors stay visible as text using the Material error role. Preserve native focus, labels and keyboard handling.

Date selection uses a rounded Material date dialog, followed by a rounded time dialog with 24-hour numeric entry. The pass editor is a scrollable rounded bottom sheet; type chips, date buttons and the save action follow the same native control family.

### Chips

Transport filters are pill-shaped Material filter chips with a tonal selected state and an outlined unselected state. Route badges use primary-container surfaces with a transport vector icon and a compact label. Wrapping groups use flow layouts rather than forcing a horizontal overflow of badges.

### Navigation

Four persistent destinations use icons and Hungarian labels: Tervezés, Térkép, Bérletek and Beállítások. Phone navigation sits inside a rounded milky surface; expanded layouts use the native rail. Back navigation follows native Android behavior, with a visible back action on subordinate screens. Labels remain visible and may wrap.

### Map

The map is locally bundled Leaflet HTML in a WebView, surrounded by native filters, refresh and location controls. It uses runtime OpenStreetMap tiles with visible attribution. Geographic clustering replaces overlapping country-scale vehicle labels; individual targets keep a (48px) hit wrapper. Clusters are count circles; individual markers become compact dots below zoom (12) and labeled pills at closer scales. Routes use primary blue. These HTML pixel values are local map implementation values, not replacements for Android `dp`.

## Do's and Don'ts

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
