# MenetrendGO

Magyar nyelvű, natív Android-alkalmazás közösségi közlekedési útvonalak tervezéséhez, élő járműkövetéshez és a bérletek lejáratának nyilvántartásához. Kotlin, Jetpack Compose és Material 3 alapokon, Android 9-től (API 28).

A MenetrendGO független projekt, nem a MÁV hivatalos alkalmazása. A menetrendi és élő adatok elérhetősége, frissessége a külső adatszolgáltatóktól függ.

## Funkciók

- **Tervezés:** megállókeresés, indulási vagy érkezési idő szerinti útvonaltervezés, helyben mentett keresési előzmények.
- **Útvonalrészletek:** átszállások, köztes megállók, menetrendi és várható időpontok, késések, a kiválasztott útvonal megjelenítése térképen.
- **Átszállási segítség:** rövid figyelmeztetés a szoros, nem elérhető vagy kimaradó csatlakozásnál. Ha található megfelelő későbbi járat, egy másodlagos sor jelzi az indulását. A keresés a gyaloglást és két perc felszállási tartalékot is figyelembe vesz, az eredeti következő leszállóhelyig, azonos járműnemmel. A menetrendi alternatíva külön jelölést kap. Normál vagy nem ellenőrizhető átszállásnál nem jelenik meg kiegészítő szöveg. Az adatok előtérben automatikusan frissülnek, háttérértesítés nélkül.
- **Forgalmi közlemények:** az útvonalrészletekben az érintett járat alatt jelenik meg a szolgáltató közleményeinek címe (például késés oka, terelés). Koppintásra egy panel mutatja a teljes leírást, az érvényességi időt és a szolgáltató hivatkozását.
- **Leszállási értesítés:** felszálláskor indítható helyalapú követés, hozzávetőleg 1–3 perces figyelmeztetés, lezárt képernyőnél is frissülő utazási értesítés és átszállási segítség.
- **Térkép:** élő járműpozíciók, járatrészletek és megállók, járműtípus és azonosítók, ha a szolgáltató megadja őket. Alap-, műholdas, hibrid és vasúti térképnézet.
- **Bérletek:** ország-, megye- és helyi bérletek rögzítése, szerkesztése, lejárati emlékeztetők, tájékoztató jellegű érvényességi ellenőrzés az útvonalon.
- **Beállítások:** világos, sötét vagy rendszer szerinti megjelenés, értesítések és helyi adatok kezelése, stabil és béta alkalmazásfrissítések.

Az élő járműadatok és a megnyitott járat adatai előtérben 75 másodpercenként frissülnek. A keresési találatok új kereséssel frissíthetők. A működéshez internetkapcsolat szükséges, az alkalmazás nem tartalmaz offline menetrendi adatbázist.

### Leszállási értesítés használata

A funkció alapértelmezetten rejtett. A **Beállítások → Fejlesztői beállítások → Leszállási jelzés (béta)** kapcsolóval jeleníthető meg. Kikapcsolása az aktív követést és a hozzá tartozó értesítéseket is leállítja.

Az **Utazás részletei** menetrendjében koppints a leszállóhely melletti csengőre. A megnyíló **Leszállási jelzés** alsó lapon felszálláskor indítsd a jelzést. Nincs választható figyelmeztetési idő. A járatot a megérintett leszállóhely határozza meg. Az aktív jelzés állapota ugyanennél a megállónál jelenik meg; a csengővel nyithatók újra a vezérlők. Egyszerre egy utazás követhető. A követés pontos helyhozzáférést és engedélyezett értesítéseket igényel. Android 16-tól támogatott eszközön Live Update megjelenítést kér, más eszközökön hagyományos folyamatos értesítést mutat; a kiemelést a rendszer és a felhasználói beállítások szabályozzák.

A figyelmeztetés a leszállóhely körüli földrajzi körbe belépéskor érkezik. A kör sugara helyi járatnál 100 méter, helyközi és vonatpótló busznál 150 méter, vasútnál és HÉV-nél 250 méter. Az aktív cél köre az utazás térképén és az általános térképen is látható. A jelzés nem függ az útvonalegyezéstől, a sebességből becsült érkezéstől vagy az élő érkezési időtől, ezért terelésnél is működik. Az útvonalhoz illesztés csak a megállószám és a folyamatjelző kiegészítő adata. Eltéréskor légvonalbeli távolság jelenik meg. A cél korábbi megközelítése is jelzést válthat ki, ha az utazás hurkolt vagy a felszállóhely már a körben van.

Csak friss, legfeljebb 100 méteres pontosságú helyjel használható. A pontossági tartománynak is a körön belül kell lennie, ezért bizonytalan helyjelnél a jelzés késhet vagy elmaradhat. Az irreális helyzetugrásokat kiszűri. A kör elérése egyszer figyelmeztet az adott szakaszon. A megérkezés külön feltétel: két, legalább három másodperccel elválasztott friss, lassú helyjel a cél 100 méteres környezetében. Az élő érkezési idő csak az azonos járat és megálló menetrendi előfordulásának egyezésekor jelenik meg. Ennek hiányában menetrendi idő látható. A jelzés hangját és rezgését a rendszer értesítési csatornája és a Ne zavarjanak mód szabályozza.

A követés a cél körétől mért légvonalbeli távolság alapján sűríti a helyfrissítéseket, terelés közben is. Távolabb 20, közeledéskor 10, közel 5, közvetlenül a kör előtt és azon belül 2 másodperces időközt kér. A váltási távolság a járműnem konzervatív felső sebességét veszi figyelembe. Kezdeti helyjelre várva 5 másodperces időközt kér. A hálózati helymeghatározás ritkább tartalékforrás. Az időközök kérések, a tényleges helyjelek gyakoriságát az Android és a vétel is befolyásolja. Az értesítés csak a látható tartalom változásakor frissül. Élő érkezési adatot induláskor, majd a cél 5 kilométeres környezetében, legfeljebb percenként kér le. A figyelmeztetés után és átszállásra várva nincs ilyen lekérés. A rendszeres állapotmentés kétpercenként történik, a jelzés és az átszállás állapota azonnal mentődik. Az akkumulátorra gyakorolt tényleges hatás készülékfüggő.

Átszálláskor a helyfrissítések szünetelnek, és a következő járműre felszállást meg kell erősíteni. A végső leszállóhely elérésekor a követés befejeződik; kézzel az alkalmazásban vagy az értesítésből is leállítható. Az aktív útvonal és a már kiküldött jelzés állapota helyben megmarad, a helyzetelőzmény nem kerül mentésre vagy feltöltésre. A betöltött útvonal helyalapú követése internet nélkül is működik. Folyamatleállás után a rendszer esetenként helyreállíthatja a szolgáltatást; ha ez nem történik meg, az alkalmazásban a szünetelő követés folytatható. Kényszerített leállítás vagy telefonkikapcsolás alatt nincs jelzés. Egy követés legfeljebb 18 óráig marad aktív.

## Telepítés

A kiadott APK-k a [GitHub Releases oldalon](https://github.com/matepazy/menetrend-go/releases) érhetők el, amikor van közzétett kiadás. Androidon engedélyezni kell a telepítést a letöltéshez használt alkalmazásból.

A beépített frissítéskezelő ugyanezt a nyilvános kiadási listát használja. Az induláskori ellenőrzés alapértelmezetten be van kapcsolva, és a **Beállítások → Frissítések** alatt kikapcsolható. Az APK letöltését és telepítését a felhasználó indítja el.

## Fejlesztői környezet

- JDK 21.
- Android SDK Platform 37. Az alkalmazás `targetSdk` értéke jelenleg 36.
- Android Studio vagy parancssori Android SDK-eszközök.

A Gradle Wrapper a repó része, külön Gradle-telepítés nem szükséges. A függőségek és a Gradle első letöltéséhez internetkapcsolat kell. Az Android-alkalmazáshoz külön szerver vagy GitHub-token nem szükséges. Az alapértelmezett Google térképhez saját Maps SDK for Android API-kulcs kell.

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

### Google Maps API-kulcs

Az Alap, Műhold és Hibrid térkép a hivatalos Google Maps SDK for Androidot használja. A Vasúti és Saját térkép továbbra is Leaflet-alapú.

1. A [Google Cloud Console](https://console.cloud.google.com/google/maps-apis/credentials) projektjében kapcsold be a **Maps SDK for Android** szolgáltatást és a számlázást, majd hozz létre API-kulcsot.
2. A repó gyökerében található, Gitből kizárt `local.properties` fájlban add meg a kulcsot az SDK útvonala alatt. A `local.properties.example` mintaként használható:

```properties
sdk.dir=C:/Users/USERNAME/AppData/Local/Android/Sdk
MAPS_API_KEY=IDE_MASOLD_A_GOOGLE_MAPS_ANDROID_API_KULCSOT
```

3. Korlátozd a kulcsot a **Maps SDK for Android** API-ra, Android-alkalmazásként pedig a `hu.matepazy.menetrendgo` csomagnévre és a megfelelő aláírókulcs SHA-1 ujjlenyomatára. A debug és release aláírás külön ujjlenyomatot használ. Ezeket a `.\gradlew.bat :app:signingReport` paranccsal listázhatod. Google Play App Signing esetén a Play Console alkalmazásaláíró tanúsítványa is szükséges.
4. Szinkronizáld a Gradle-projektet, majd fordítsd újra és telepítsd az alkalmazást. A kulcs fordításkor kerül a manifestbe. Ne másold forráskódba vagy verziókövetett fájlba.

CI-környezetben a `MAPS_API_KEY` környezeti változó is használható, amely elsőbbséget élvez a helyi fájllal szemben. Kulcs nélkül a Google térkép beállítási üzenetet mutat, a Vasúti és Saját térkép használható marad. A Google térképhez Google Play-szolgáltatásokat tartalmazó telefon vagy emulátor szükséges. Hitelesítési hiba esetén ellenőrizd a szolgáltatás engedélyezését, a számlázást és a csomagnév/SHA-1 korlátozásokat a [Google beállítási útmutatója](https://developers.google.com/maps/documentation/android-sdk/get-api-key) alapján.

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
ellenőrzött, szabad licencű Wikimedia Commons-fájlokból, megnyitáskor töltődnek le.
A külön fotógyorsítótár legfeljebb 12 képet és 12 MiB adatot őriz, a legrégebben
megtekintett képeket törli. Egy letöltés legfeljebb 2 MiB lehet. Az ismertetők,
az adatforrások és a fotók szerzői, illetve licencadatai továbbra is offline
elérhetők. A még nem tárolt fotókhoz internet kell. Az `app/proguard-rules.pro`
megőrzi a frissítéskezelő reflektíven olvasott adatmodelljeit és a térkép
JavaScript-hídjának metódusait. Kiadás előtt a release buildben is ellenőrizd
a frissítéslistát, a térképes járműkiválasztást és a fotók megjelenítését.
A képek URL-jeit és szabad licenceit a `node scripts/refresh-fleet-photo-urls.mjs`
parancs ellenőrzi újra a Commons API-n keresztül. A program csak a katalógusban
már kiválasztott képeket kezeli, nem választ automatikusan típusfotókat.
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

### Megállók, indulási tábla és kezdőképernyős widget

A Térkép lap szűrőgombjával megnyitható **Járműszűrők** munkalapon a **Megállók és állomások** kapcsolóval állítható a megállójelölők láthatósága. A beállítás megmarad. Közelíts a térképen a megállókhoz; egy jelölőre koppintva megnyílik az adott hely indulási táblája. Az állomások peronjait egy közös állomás fogja össze, az utcai megállók saját szolgáltatói azonosítójukat tartják meg.

Az indulási tábla bal oldalon a járat hivatalos jelét és nevét, jobb oldalon az indulásig hátralévő időt (**x perc**, egy percen belül **Most**) mutatja. A visszaszámlálás a kijelzett becslésből frissül; a MÁV-adatok előtérben 30 másodpercenként és kézi frissítéssel töltődnek. Pontos indulási idő és külön élő/menetrendi felirat helyett a visszaszámlálás színe jelzi az adatállapotot: fekete a menetrendi adat, zöld az élő adat késés nélkül, sárga a 10 percnél kisebb élő késés, piros a legalább 10 perces élő késés. Sötét témában a menetrendi érték fehér. A kimaradás és az ismert vágány vagy kocsiállás továbbra is látható. Sikertelen frissítéskor a korábbi adatok mellett hibaüzenet jelenik meg.

A **Widget a kezdőképernyőre** gomb az adott megállóhoz kér Android-widgetet. A kezdőképernyő widgetválasztójából is hozzáadható: ekkor kereséssel választható ki a pontos megálló. Több widget különböző megállót is követhet. A widget ugyanazt a járat-balra / visszaszámlálás-jobbra elrendezést és a beépített hivatalos MÁV-jelképeket használja. A tárolt indulási időkből a visszaszámlálást hálózati kérés nélkül, nem ébresztő helyi időzítővel frissíti; az Android ezt is késleltetheti. A widget átméretezhető, kézzel frissíthető, és a táblára koppintva megnyitja a teljes indulási listát. Egy adatfrissítés a következő tíz indulást tárolja. Az elindult járatok helyére a helyi készlet következő elemei kerülnek, új hálózati kérés nélkül. Az Indulások laphoz illő, lekerekített kártyák és tudatos térközök tagolják az indulásokat; a hivatalos jel és az úti cél azonos bal szélhez igazodik; a widget a tényleges szövegméret és sortörések alapján annyi teljes indulást mutat, amennyi elfér, ötös megjelenítési korlát nélkül. Friss adatot kér ötpercenként (tízperces ütemezési határidővel), a helyi készlet elfogyásakor, a widgetből történő megnyitáskor és a frissítés gomb megnyomásakor. Az Android energiatakarékossága vagy a kapcsolat hiánya késleltetheti a háttérfeladatot. Az elindult járat azonnal kikerül a helyi listából; sikertelen kérésnél legalább egy perc telik el az újrapróbálásig. A widget és a megálló munkalapja csak a legutóbbi frissítés óta eltelt időt mutatja, frissítési gyakoriságot nem. Az utolsó frissítés kora mindig látható, a háttérben frissülő widget nem folyamatosan nyitott élő nézet.

Az alkalmazás közvetlenül, HTTPS-en éri el a MÁVPlusz OTP GraphQL- és megállókereső végpontjait. A lekérdezések és az adatok feldolgozásának felépítése a [hovamegy.hu](https://github.com/matepazy/hovamegy.hu) megoldását követi. Az alapértelmezett térkép a natív Google Maps SDK for Androidot használja. A Vasúti és Saját térkép Leafletet használ egy WebView-ban, a többi felület natív Compose.

A bérletek, keresési előzmények és beállítások az eszközön maradnak. Nincs felhasználói fiók vagy analitikai SDK. A hozzávetőleges helyhozzáférés opcionális, és a térkép középre igazítására szolgál. A menetrendi kérések a kereséshez szükséges adatokat elküldik a MÁVPlusznak, a térképszolgáltatók pedig a csempekérésekből látják a megtekintett területet és a hálózati kapcsolat adatait. A frissítésellenőrzés a GitHub nyilvános API-ját hívja.

A bérletnyilvántartás nem jegytárca, a rögzített adatok nem igazolják az utazási jogosultságot. Az útvonalhoz jelzett bérletérvényesség tájékoztató jellegű, különösen határ menti szakaszok, helyi járatok és helyjegyköteles vonatok esetén.

## Felhasznált térképek és források

- [Google Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk) – az Alap, Műhold és Hibrid térképek.
- [Leaflet](https://leafletjs.com/) – a beágyazott példány licence: `app/src/main/assets/leaflet-LICENSE.txt`.
- OpenStreetMap, Esri és OpenRailwayMap – a térképen feltüntetett forrásmegjelölésekkel. A térképszolgáltatók használati feltételei a saját példányokra is vonatkoznak.
- Megyehatárok – az adatok eredete és felhasználási feltételei: [hungary-counties-LICENSE.md](app/src/main/assets/hungary-counties-LICENSE.md).

Hibajelentéshez add meg az alkalmazás és az Android verzióját, a reprodukálás lépéseit, menetrendi eltérésnél pedig a keresés dátumát és az érintett járatot. Ne csatolj aláírókulcsot, jelszót vagy személyes bérletadatot.
