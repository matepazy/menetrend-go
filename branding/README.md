# MenetrendGO logo

The approved front-facing train, reconstructed as clean paths. Blue: `#2864D9`.
The windshield and headlights are transparent cutouts. All SVGs are real vectors
without embedded raster images, fonts or external dependencies.

| Asset | Use |
| --- | --- |
| `menetrendgo-mark.svg` | Blue train on transparent background |
| `menetrendgo-mark-white.svg` | White train for dark/blue backgrounds |
| `menetrendgo-icon.svg` | Square icon, including store artwork |
| `menetrendgo-icon-rounded.svg` | Rounded-square icon for websites/documents |
| `menetrendgo-icon-round.svg` | Circular icon for websites/documents |
| `menetrendgo-launcher-foreground.svg` | Transparent 108 × 108 Android layer |

## Android sizing

Foreground and background layers use a 108dp viewport. The train is approximately
40 × 51dp, centered at (54, 54). Its complete silhouette fits inside the central
66dp-diameter safe circle, with about 28.5dp vertical layer padding. Inside a
72dp launcher mask it occupies about 56% of the width and 71% of the height.
The rails remain visible under circular and rounded-square masks.

Both `android:icon` and `android:roundIcon` reference adaptive icons, sharing the
same foreground and edge-to-edge blue background. Android supplies the outline,
so there is no second rounded tile inside the launcher mask. API 33+ also gets a
monochrome layer from the same transparent train vector for themed icons.
The pass-reminder notification icon uses the same paths, fitted to a 24dp canvas.

The standalone icon SVGs preview the visible 72dp viewport, rather than the
108dp layer that includes Android's hidden animation margins. The standalone
mark files use a tighter canvas for general logo placement.

## Regeneration

Run `python scripts/generate_branding.py` from the repository root. Edit paths or
sizing there, then regenerate to keep SVG masters and Android resources identical.
The earlier `menetrendgo-logo-concept*.png` files are exploration references.

Sizing reference: [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
