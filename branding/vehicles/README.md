# Footer train artwork

User-supplied SVGs received on 2026-11-18. The only drawing change is removing the transparent area below the wheels from the root viewport, keeping every path, colour and detail intact.

| Source filename prefix | Vehicle | Viewport |
| --- | --- | --- |
| 710575046 | Bzmot with Bzx trailer | 1160 × 172 |
| 710575047 | Szivárvány consist | 3785 × 199 |
| 710575048 | M62 Szergej with coaches | 2345 × 185 |
| 710575049 | V43 Szili with coaches | 2255 × 193 |
| 710575050 | Siemens Desiro | 2280 × 269 |

Matching SVGs ship in the web repository under `public/vehicles`. Android uses lossless WebP renders at 372px tall under `app/src/main/res/drawable-nodpi`. FooterVehicle normalizes their proportional widths to the existing 124-unit animation coordinate system. Both the Settings footer and VehicleProgress draw from this shared set.

To regenerate Android images from this repository, run the following in the sibling web checkout, which provides Sharp through Next.js.

```powershell
node -e "const fs=require('fs'),sharp=require('sharp'); (async()=>{for(const name of ['szili','desiro','szergej','bzmot','szivarvany']) await sharp(fs.readFileSync('../menetrend-go/branding/vehicles/'+name+'.svg')).resize({height:372}).webp({lossless:true}).toFile('../menetrend-go/app/src/main/res/drawable-nodpi/footer_'+name+'.webp');})();"
```
