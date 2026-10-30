# MenetrendGO

Magyar nyelvű, natív Android-alkalmazás közösségi közlekedési útvonalak tervezéséhez, élő járműkövetéshez és a bérletek lejáratának nyilvántartásához. Kotlin, Jetpack Compose és Material 3 alapokon, Android 9-től (API 28).

A MenetrendGO független projekt, nem a MÁV hivatalos alkalmazása. A menetrendi és élő adatok elérhetősége, frissessége a külső adatszolgáltatóktól függ.

## Funkciók

- **Tervezés:** megállókeresés, indulási vagy érkezési idő szerinti útvonaltervezés, helyben mentett keresési előzmények.
- **Útvonalrészletek:** átszállások, köztes megállók, menetrendi és várható időpontok, késések, a kiválasztott útvonal megjelenítése térképen.
- **Leszállási értesítés:** felszálláskor indítható helyalapú követés, hozzávetőleg 1–3 perces figyelmeztetés, lezárt képernyőnél is frissülő utazási értesítés és átszállási segítség.
- **Térkép:** élő járműpozíciók, járatrészletek és megállók, járműtípus és azonosítók, ha a szolgáltató megadja őket. Alap-, műholdas, hibrid és vasúti térképnézet.
- **Bérletek:** ország-, megye- és helyi bérletek rögzítése, szerkesztése, lejárati emlékeztetők, tájékoztató jellegű érvényességi ellenőrzés az útvonalon.
- **Beállítások:** világos, sötét vagy rendszer szerinti megjelenés, értesítések és helyi adatok kezelése, stabil és béta alkalmazásfrissítések.

Az élő járműadatok és a megnyitott járat adatai előtérben 75 másodpercenként frissülnek. A keresési találatok új kereséssel frissíthetők. A működéshez internetkapcsolat szükséges, az alkalmazás nem tartalmaz offline menetrendi adatbázist.

### Leszállási értesítés használata

Az **Utazás részletei** menetrendjében koppints a leszállóhely melletti csengőre. A megnyíló **Leszállási jelzés** alsó lapon válaszd ki a figyelmeztetés idejét, majd felszálláskor indítsd a jelzést. A járatot a megérintett leszállóhely határozza meg. Az aktív jelzés állapota ugyanennél a megállónál jelenik meg; a csengővel nyithatók újra a vezérlők. Egyszerre egy utazás követhető. A követés pontos helyhozzáférést és engedélyezett értesítéseket igényel. Android 16-tól támogatott eszközön Live Update megjelenítést kér, más eszközökön hagyományos folyamatos értesítést mutat; a kiemelést a rendszer és a felhasználói beállítások szabályozzák.

A figyelmeztetés az útvonal menti hátralévő távolság és a telefon mozgása alapján becsült, közvetlenül a megálló közelében távolságalapú tartalékjelzéssel. Gyenge, régi vagy az útvonaltól távoli helyjelre nem ad biztos leszállási jelzést, hanem jelzi a bizonytalanságot. Az élő érkezési idő csak az azonos járat és megálló menetrendi előfordulásának egyezésekor jelenik meg; ennek hiányában menetrendi idő látható. A jelzés hangját és rezgését a rendszer értesítési csatornája és a Ne zavarjanak mód szabályozza.

Átszálláskor a helyfrissítések szünetelnek, és a következő járműre felszállást meg kell erősíteni. A végső leszállóhely elérésekor a követés befejeződik; kézzel az alkalmazásban vagy az értesítésből is leállítható. Az aktív útvonal és a már kiküldött jelzés állapota helyben megmarad, a helyzetelőzmény nem kerül mentésre vagy feltöltésre. A betöltött útvonal helyalapú követése internet nélkül is működik. Folyamatleállás után a rendszer esetenként helyreállíthatja a szolgáltatást; ha ez nem történik meg, az alkalmazásban a szünetelő követés folytatható. Kényszerített leállítás vagy telefonkikapcsolás alatt nincs jelzés. Egy követés legfeljebb 18 óráig marad aktív.

## Telepítés

A kiadott APK-k a [GitHub Releases oldalon](https://github.com/matepazy/menetrend-go/releases) érhetők el, amikor van közzétett kiadás. Androidon engedélyezni kell a telepítést a letöltéshez használt alkalmazásból.

A beépített frissítéskezelő ugyanezt a nyilvános kiadási listát használja. Az induláskori ellenőrzés alapértelmezetten be van kapcsolva, és a **Beállítások → Frissítések** alatt kikapcsolható. Az APK letöltését és telepítését a felhasználó indítja el.

## Fejlesztői környezet

- JDK 21.
- Android SDK Platform 37. Az alkalmazás `targetSdk` értéke jelenleg 36.
- Android Studio vagy parancssori Android SDK-eszközök.

A Gradle Wrapper a repó része, külön Gradle-telepítés nem szükséges. A függőségek és a Gradle első letöltéséhez internetkapcsolat kell. Az Android-alkalmazáshoz külön szerver, API-kulcs vagy GitHub-token nem szükséges.

```sh
git clone https://github.com/matepazy/menetrend-go.git
cd menetrend-go
```

Nyisd meg a projektet Android Studióban, és válaszd a JDK 21-et Gradle JDK-ként. Parancssori fordításhoz a `JAVA_HOME` mutasson a megfelelő JDK-ra, az SDK útvonalát pedig az `ANDROID_HOME` környezeti változóval vagy a repó gyökerében létrehozott `local.properties` fájlban add meg:

```properties
# Linux/macOS példa
sdk.dir=/path/to/Android/Sdk
```

Windows alatt például `sdk.dir=C:/Users/USERNAME/AppData/Local/Android/Sdk`. A `local.properties` gépspecifikus fájl, ezért a Git figyelmen kívül hagyja.

### Fordítás és ellenőrzés

Linux/macOS:

```sh
sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

A fejlesztői APK helye: `app/build/outputs/apk/debug/app-debug.apk`. Csatlakoztatott telefonra vagy emulátorra a `:app:installDebug` feladattal telepíthető. Ez a build fejlesztői kulccsal van aláírva.

A JVM-tesztek többek között az időpontkezelést, a bérletszabályokat, az állomás- és járműkeresést, a járatadatok feldolgozását és a verzió-összehasonlítást ellenőrzik.

A debug APK fejlesztői eszközöket és optimalizálatlan könyvtárakat is tartalmaz.
Terjesztéshez a release APK-t használd: az R8 eltávolítja a nem használt kódot
és erőforrásokat, köztük a fel nem használt Material ikonokat. A járműfotók
továbbra is a csomag részei, offline is elérhetők. Az `app/proguard-rules.pro`
megőrzi a frissítéskezelő reflektíven olvasott adatmodelljeit és a térkép
JavaScript-hídjának metódusait. Kiadás előtt a release buildben is ellenőrizd
a frissítéslistát, a térképes járműkiválasztást és a fotók megjelenítését.
A hibakereséshez őrizd meg az adott buildhez tartozó
`app/build/outputs/mapping/release/mapping.txt` fájlt.

### Saját kiadási APK

1. Hozz létre saját kiadási kulcsot az Android Studio **Generate Signed App Bundle or APK** eszközével vagy a JDK `keytool` parancsával.
2. Hozz létre egy `signing/` könyvtárat a repó gyökerében, és helyezd el benne a kulcstárat.
3. Másold a `signing.properties.example` fájlt `signing/signing.properties` néven, majd add meg a kulcstár útvonalát, jelszavait és a kulcs aliasát. A `storeFile` útvonala a repó gyökeréhez képest értendő, Windows alatt is használhatsz `/` elválasztót.
4. Futtasd a kiadási feladatot:

```sh
sh ./gradlew :app:exportReleaseApk
```

PowerShell alatt használd a `.\gradlew.bat :app:exportReleaseApk` parancsot. Az exportált fájl az `artifacts/releases/menetrendgo-v<verzió>.apk` útvonalra kerül.

A `signing/` könyvtár, a kulcstárak és a fordítási kimenetek Gitből kizártak. Az aláírási beállítások nélkül a release build aláíratlan APK-t készít. Közzétételhez saját kulccsal aláírt APK szükséges.

Új kiadás előtt növeld a `versionCode` és `versionName` értékét az `app/build.gradle.kts` fájlban. Használj például `v1.0.1` vagy `v1.1.0-beta.1` GitHub release taget, a béta kiadást jelöld előzetes kiadásként, és csatold az exportált APK-t. A meglévő telepítések frissítéséhez ugyanaz az alkalmazásazonosító és aláírókulcs szükséges.

Fork esetén a frissítési forrást a `VersionUpdater.kt` fájlban, a projekt hivatkozását és térképes User-Agentjét pedig a `NearbyScreen.kt` fájlban lehet átállítani a saját repóra.

## Projektfelépítés

| Útvonal | Tartalom |
| --- | --- |
| `app/src/main/java/hu/matepazy/menetrendgo/` | Compose-felület, állapotkezelés, adatkapcsolat, bérletek és frissítések |
| `app/src/main/assets/` | Leaflet-térkép, megyehatárok és a hozzájuk tartozó licencinformációk |
| `app/src/main/res/` | Android-erőforrások és ikonok |
| `app/src/test/` | JVM-egységtesztek |
| `branding/` | Vektoros logók és ikonváltozatok |
| `scripts/generate_branding.py` | SVG-k és Android-vektorok közös forrásból történő generálása |
| `DESIGN.md` | A felület vizuális és komponensszabályai |

Az ikonok újragenerálása a repó gyökeréből: `python scripts/generate_branding.py`. Ehhez Python 3 szükséges, külső Python-csomag nem kell.

## Adatkapcsolat és adatkezelés

Az alkalmazás közvetlenül, HTTPS-en éri el a MÁVPlusz OTP GraphQL- és megállókereső végpontjait. A lekérdezések és az adatok feldolgozásának felépítése a [hovamegy.hu](https://github.com/matepazy/hovamegy.hu) megoldását követi. A térkép Leafletet használ egy WebView-ban, a többi felület natív Compose.

A bérletek, keresési előzmények és beállítások az eszközön maradnak. Nincs felhasználói fiók vagy analitikai SDK. A hozzávetőleges helyhozzáférés opcionális, és a térkép középre igazítására szolgál. A menetrendi kérések a kereséshez szükséges adatokat elküldik a MÁVPlusznak, a térképszolgáltatók pedig a csempekérésekből látják a megtekintett területet és a hálózati kapcsolat adatait. A frissítésellenőrzés a GitHub nyilvános API-ját hívja.

A bérletnyilvántartás nem jegytárca, a rögzített adatok nem igazolják az utazási jogosultságot. Az útvonalhoz jelzett bérletérvényesség tájékoztató jellegű, különösen határ menti szakaszok, helyi járatok és helyjegyköteles vonatok esetén.

## Felhasznált térképek és források

- [Leaflet](https://leafletjs.com/) – a beágyazott példány licence: `app/src/main/assets/leaflet-LICENSE.txt`.
- OpenStreetMap, Esri és OpenRailwayMap – a térképen feltüntetett forrásmegjelölésekkel. A térképszolgáltatók használati feltételei a saját példányokra is vonatkoznak.
- Megyehatárok – az adatok eredete és felhasználási feltételei: [hungary-counties-LICENSE.md](app/src/main/assets/hungary-counties-LICENSE.md).

Hibajelentéshez add meg az alkalmazás és az Android verzióját, a reprodukálás lépéseit, menetrendi eltérésnél pedig a keresés dátumát és az érintett járatot. Ne csatolj aláírókulcsot, jelszót vagy személyes bérletadatot.
