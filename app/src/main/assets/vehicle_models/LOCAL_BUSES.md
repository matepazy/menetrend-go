# Helyi autóbuszok és a fotók licence

A 2026. október 25-i MÁVPlusz BUS-pozíciólekérdezés 65 különböző, nem üres
típusnevéhez tartozik ismertető és legalább egy fotó. A megfigyelt nevek a
`src/test/resources/local-bus-models-2026-10-25.txt` fájlban szerepelnek.
Ez egy élő adatfolyam pillanatfelvétele, nem az összes magyar város teljes
járműállományának felsorolása. Üres típusadatból nem következtetünk modellre.

Az új kiegészítés 35 ismertetőt és 39 fotóbejegyzést tartalmaz. A magyar
szövegek forrásai a profilok `sources` mezőiben találhatók, elsősorban a BKV,
MÁV-csoport, MVK, V-Busz, ArrivaBus és a járműgyártók oldalaira mutatnak.
A szolgáltatói elnevezések között az egyértelmű szinonimák közös profilra
vezetnek, a különböző hajtások és az ismert generációk külön maradnak.
A közös Volvo Hybrid és Solaris Urbino 12 nevek korlátait az ismertető jelzi.
A paksi Urbino 12 a szolgáltatónév alapján külön elektromos profilra vezet.

## Fotóhasználat

Az új fotók Wikimedia Commons-fájllapján és az ImageInfo licencadataiban
ellenőrzött CC BY 4.0, CC BY-SA 3.0, CC BY-SA 4.0 vagy CC0 licenc szerepel.
Nem használunk NC, ND, kizárólag sajtócélú vagy engedély nélküli fotót.
A képeket vizuálisan is ellenőriztük, majd legfeljebb 1000 képpontos méretű,
vágás nélküli JPEG-változatként csomagoltuk. A fotók nem generált képek.

Az egyes képek szerzője, eredeti fájllapja, licence és licenchivatkozása a
`catalog.json` megfelelő `photos` bejegyzésében szerepel, és az alkalmazás
„Fotó és források” részében elérhető. A JPEG megjegyzésmezője ugyanezt a
forrásmegjelölést és az átméretezés tényét is tartalmazza. A származékos
képek az eredeti fotó licencfeltételei szerint használhatók tovább, beleértve
a CC BY-SA képek azonos licenc alatti továbbadását. A fotólicencek nem
vonatkoznak automatikusan az alkalmazás kódjára vagy a többi tartalomra.

Helyi busznál az alkalmazás egy fotót választ. Az `agencies` mezőben
felsorolt pontos szolgáltatónév egyezése elsőbbséget ad az adott városban
fotózott vagy az adott szolgáltató festését viselő járműnek. Egyezés nélkül
egy ellenőrzött helyi típusfotó, ennek hiányában az általános katalógusfotó
jelenik meg. A képaláírás jelzi a tényleges várost vagy a külföldi
típusillusztrációt. A fénykép nem azonosítja az éppen érkező példányt.

## Ellenőrzés

`node tools/verify-vehicle-catalog.mjs --local-buses` ellenőrzi a megfigyelt
helyi típusnevek lefedettségét, a fotófájlokat, a szabad licenceket és a
forrásmegjelölést. A `--release` az egész, vasúti és helyközi katalógusra is
fotót követel, így a korábbi hiányokat is jelzi.
