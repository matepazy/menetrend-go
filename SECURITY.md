# Biztonsági irányelvek

A MenetrendGO fejlesztésénél fontos a felhasználók biztonsága és adatainak védelme. Az alkalmazásnak nincs saját szervere, nincs fiókrendszere és nem használ analitikát. A jegyek, bérletek, keresési előzmények és beállítások csak az eszközön, a `SharedPreferences` tárolóban találhatók.

## Támogatott verziók

Biztonsági javítást csak a legfrissebb kiadott verzió kap. Kérlek, a hibát a [legújabb kiadáson](https://github.com/matepazy/menetrend-go/releases/latest) is ellenőrizd, mielőtt bejelented.

| Verzió            | Támogatott |
| ----------------- | ---------- |
| Legújabb kiadás   | ✅         |
| Korábbi kiadások  | ❌         |

## Sebezhetőség bejelentése

**Kérlek, ne nyiss nyilvános issue-t biztonsági hibához.**

Használd a GitHub privát bejelentését:

1. Nyisd meg a repó **Security** fülét.
2. Kattints a **Report a vulnerability** gombra.
3. Írd le a hibát a lenti szempontok szerint.

A bejelentésben légy minél pontosabb:

- az érintett verzió (`versionName`) és az Android-verzió,
- a hiba leírása és a lehetséges hatása,
- a reprodukálás lépései vagy egy minimális proof of concept,
- az érintett fájl vagy funkció, ha tudod.

### Mire számíthatsz

- **3 munkanapon belül** visszaigazolom a bejelentést.
- **10 munkanapon belül** jelzem, hogy sebezhetőségnek tartom-e, és mi a tervezett javítás.
- A javított verzió kiadása után, egyeztetve veled, nyilvánosságra hozom a hibát. Ha szeretnéd, a köszönetnyilvánításban név szerint megemlítelek.

A projektet egy ember tartja karban szabadidőben, ezért a határidők a súlyosságtól és a terhelésemtől függően csúszhatnak. Ilyenkor szólok.

## Hatókör

### Hatókörön belül

- a `hu.matepazy.menetrendgo` Android-alkalmazás kódja,
- a helyi adattárolás (bérletek, előzmények, beállítások) védelme,
- a térkép WebView-ja és a `addJavascriptInterface` híd (`map.html`, `leaflet.js`, `vehicle-icons.js`),
- az önfrissítés (`VersionUpdater`, `UpdateController`): letöltés és telepítés a GitHub Releases-ről,
- a helymeghatározást használó előtér-szolgáltatás (`JourneyTrackingService`),
- a kiadott APK aláírása és az R8/ProGuard-konfiguráció,
- a repóban lévő szkriptek (`scripts/`, `tools/`) és a build-konfiguráció.

### Hatókörön kívül

- a MÁV, a MÁVPlusz OTP/GraphQL, az utas.hu, a Wikimedia Commons és más külső szolgáltatók saját rendszerének hibái. Ezeket kérlek, a szolgáltatónak jelezd,
- olyan hibák, amelyekhez rootolt eszköz, fizikai hozzáférés a feloldott telefonhoz vagy előre telepített kártékony alkalmazás kell,
- hibás vagy késő menetrendi adat, helytelen járműadat vagy téves bérletérvényességi tájékoztatás. Ezek hibák, de nem biztonsági problémák, ezért nyiss rájuk normál issue-t,
- szolgáltatásmegtagadás (DoS), tömeges automatizált kérések küldése a külső szolgáltatók felé,
- a függőségek ismert sebezhetősége, ha az alkalmazásból nem érhető el vagy nem kihasználható.

## Különösen érdekes területek

- Az alkalmazás **nem tárol jelszót, fizetési adatot vagy személyazonosító okmányt**. Ha ilyen mégis kiszivárogna a naplókba, biztonsági mentésbe vagy hálózatra, azt kérlek, jelezd.
- A **helyadat** csak a felhasználó engedélyével, funkcióhoz kötötten használt. Jogosulatlan olvasás, tárolás vagy továbbítás sebezhetőségnek számít.
- Az **önfrissítésnél** az APK csak a hivatalos kiadási csatornáról érkezhet, a telepítést pedig mindig a felhasználó indítja. Az ellenőrzés megkerülése vagy a letöltés lecserélhetősége súlyos hibának minősül.
- A **WebView** JavaScript-hídján csak az alkalmazás saját, bundle-ölt tartalma hívhat natív kódot.

## Felelős közzététel és jogi háttér

Ha jóhiszeműen, ezen irányelvek szerint kutatsz, és:

- csak annyi adatot érsz el, amennyi a hiba igazolásához szükséges,
- nem rongálsz, nem töröl és nem tartasz meg más felhasználóktól származó adatot,
- a hibát nem hozod nyilvánosságra a javítás előtt,

akkor a kutatásodat jóhiszeműnek tekintem, és nem indítok jogi lépést ellened. Ez a vállalás csak a MenetrendGO-ra vonatkozik, a külső szolgáltatók rendszereire nem.

A projekt hobbiprojekt, ezért **pénzjutalmat (bug bounty) nem tudok felajánlani**, de őszintén hálás vagyok minden felelős bejelentésért.

## Biztonságos használat felhasználóknak

- Az alkalmazást csak a [hivatalos GitHub-kiadásokról](https://github.com/matepazy/menetrend-go/releases) telepítsd.
- A kiadott APK-k aláírással készülnek. Ha az aláírás eltér a korábbitól, ne telepítsd.
- A bérletérvényesség-ellenőrzés csak tájékoztató jellegű, jogosultság igazolására nem alkalmas.
