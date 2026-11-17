# Közreműködési útmutató

Köszönöm, hogy hozzá szeretnél járulni a MenetrendGO-hoz! Hibajelzést, ötletet és kódot is szívesen fogadok.

## Mielőtt elkezdesz

- **Hiba vagy ötlet:** nyiss issue-t a megfelelő sablonnal. Nagyobb változtatás előtt egyeztessünk, hogy ne végezz felesleges munkát.
- **Biztonsági hiba:** ne nyilvános issue-ban jelezd, hanem a [`SECURITY.md`](SECURITY.md) szerint.
- **Hatókör:** a projekt céljait és korlátait a [PRODUCT.md](PRODUCT.md) írja le. Nincs backend, fiókrendszer és analitika, és ezek nem is kerülnek be.

## Fejlesztői környezet

Szükséges: JDK 21, Android SDK (`sdk.dir` a `local.properties`-ben vagy `ANDROID_HOME`).

Windowson `.\gradlew.bat`, Linuxon és macOS-en `sh ./gradlew`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug   # alap ellenőrzés
./gradlew :app:testDebugUnitTest --tests "hu.matepazy.menetrendgo.PassRulesTest"   # egy tesztosztály
./gradlew :app:installDebug              # telepítés eszközre vagy emulátorra
./gradlew :app:connectedDebugAndroidTest # Compose UI tesztek, eszköz kell hozzá
```

A `start_emu.bat` emulátort indít.

## Kódolási szabályok

- **Nyelv:** a felhasználónak szóló szövegek, a README és a dokumentáció magyarul vannak. A kód azonosítói és a commit üzenetek angolok.
- **Stílus:** sűrű, tömör Kotlin. Igazodj a környező kódhoz.
- **Logika és felület szétválasztása:** a logika sima Kotlin fájlokban éljen, hogy JVM-teszttel (JUnit4, `org.json`) lefedhető legyen. A mintaadatok az `app/src/test/resources` mappába kerülnek.
- **Állapot:** a UI-állapot az `AppState` ViewModelben van, a `menetrendgo` SharedPreferences-ben mentve.
- **Design:** a színek, betűk és komponensek a [DESIGN.md](DESIGN.md) szerint készülnek. Új felületnél kezeld a töltés, az üres és a hibás állapotot is.
- **Adatok:** a szolgáltatói állomásazonosítókat meg kell őrizni, a menetrendi és a valós idejű adat pedig látványosan különbözzön.
- **Térkép:** a `map.html`, `leaflet.js` és `vehicle-icons.js` a Compose-ból hívott `window.*` függvényeken keresztül kommunikál. Ha a szerződés bármelyik oldalán változtatsz, a másikat és a `proguard-rules.pro`-t is frissítsd.
- **Grafika:** szolgáltatói vagy üzemeltetői grafikát ne tölts be. A piktogramokat és ikonokat a generátor szkriptek állítják elő (`scripts/generate_pictograms.py`, `scripts/generate_branding.py`). Ne a generált fájlokat szerkeszd, hanem a generátort, és futtasd újra.
- **Fotók és katalógus:** a járműfotók Wikimedia Commons forrásból jönnek, ellenőrzött licenccel (`node scripts/refresh-fleet-photo-urls.mjs`). Az adat az `app/src/main/assets/vehicle_models/catalog.json` fájlban van.

## Tesztelés

- Új logikához írj JVM-tesztet.
- Felületi viselkedéshez az `app/src/androidTest` alatt van helye az instrumentált tesztnek.
- Beküldés előtt mindig futtasd az alap ellenőrzést (lásd fent), és ha lehet, próbáld ki eszközön is.

## Pull request menete

1. Forkold a repót, és ágazz le a `main`-ről beszédes nevű ágra (pl. `fix/megallotabla-ures-allapot`).
2. Egy PR egy témáról szóljon. A független változtatásokat külön PR-ba tedd.
3. A commit üzenet egy rövid, felszólító módú angol tárgysor, törzs nélkül, például: `Fix empty state in the stop board`.
4. Töltsd ki a PR sablont.
5. Reagálj a visszajelzésekre. A PR akkor kerül be, ha az ellenőrzés zöld.

## Kiadás (csak karbantartóknak)

- Növeld a `versionCode` és a `versionName` értékét az `app/build.gradle.kts` fájlban.
- Add hozzá a kiadási jegyzetet a `ReleaseNotes.kt` és a `CHANGELOG.md` fájlhoz.
- Az aláírt APK `./gradlew :app:exportReleaseApk` paranccsal készül (`signing/signing.properties` kell hozzá, a sablon: `signing.properties.example`). A `signing/` mappát és a kulcsokat soha ne commitold.
- A release build R8-at használ, ezért a reflexióval olvasott modelleknek és a WebView JS-hídnak a `proguard-rules.pro`-ban kell maradniuk.

## Viselkedési elvárások

Légy udvarias és építő. Személyeskedést, zaklatást és mások adatainak kiadását nem tűrök. A jelentéseket privátban kezelem.

## Licenc

A hozzájárulásod a projekt licence alatt kerül be. A licenc szövegét a `LICENSE` fájl tartalmazza.
