# Pass artwork

County outlines are cropped, proportionally scaled paths from **HU counties blank.svg**,
by PM, with the current source revision uploaded by Droo on 7 December 2008.
The author released this work into the public domain worldwide.

Source page: https://commons.wikimedia.org/wiki/File:HU_counties_blank.svg
Original SVG: https://upload.wikimedia.org/wikipedia/commons/b/b3/HU_counties_blank.svg

All 19 counties and the Hungary national outline are included. The historical Csongrád path is labeled
Csongrád-Csanád in the app. Budapest is not a separate county icon.
No geometry is used for pass-acceptance decisions.

Run `python scripts/generate_pass_county_icons.py` to regenerate the individual
SVGs and native Android VectorDrawables. Only cropping, scaling and stroke
styling are changed; the original path contours are preserved.

The country-pass icon uses the source SVG's national outline (`mo` path), with
the same cropping, sizing and stroke as the county icons.
