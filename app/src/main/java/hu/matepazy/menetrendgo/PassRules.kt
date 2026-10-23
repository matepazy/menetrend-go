package hu.matepazy.menetrendgo

/** Rules checked against MÁV/BKK public tariff information on 2026-10-23.
 * https://bkk.hu/jegyek-es-berletek/arak/orszagberlet-teljesaru/
 * https://bkk.hu/jegyek-es-berletek/arak/pest-varmegyeberlet-teljesaru/
 * https://www.mavcsoport.hu/mav-szemelyszallitas/belfoldi-utazas/dijszabasi-informaciok-vasuti-es-buszos-utazashoz
 * Reservation information is category-based: OTP does not expose a mandatory-seat flag.
 */
fun passAdvice(passes: List<Pass>, journey: Journey, geography: PassGeography?=null): PassAdvice {
    val transit=journey.legs.filterNot {it.walking}
    if(transit.isEmpty()) return PassAdvice("Gyalogos útvonal","Ehhez a szakaszhoz nem szükséges bérlet.",true)
    if(passes.isEmpty()) return PassAdvice("Nincs mentett bérlet","A Bérletek oldalon hozzáadhatod a bérletedet.")
    var reservationCheck=false
    val used=linkedSetOf<String>()
    for(leg in transit) {
        val active=passes.filter {it.validAt(leg.start) && it.validAt(leg.end)}
        if(active.isEmpty()) return PassAdvice("A mentett bérlet ekkor nem érvényes","Ellenőrizd az utazás napját és a bérlet kezdő, illetve lejárati dátumát.")
        val operator=leg.agencyName.uppercase(java.util.Locale.ROOT)
        val supported=operator.startsWith("MÁV SZEMÉLYSZÁLLÍTÁSI") || operator.startsWith("MÁV-START") ||
            operator.startsWith("GYSEV") || operator.startsWith("VOLÁNBUSZ") || operator.startsWith("MÁV-HÉV")
        val bkk=operator=="BKK" || operator.contains("BUDAPESTI KÖZLEKEDÉSI KÖZPONT")
        val regional=leg.mode in setOf("RAIL","COACH","SUBURBAN_RAILWAY","TRAMTRAIN","RAIL_REPLACEMENT_BUS")
        if((!supported || !regional) && !bkk) return PassAdvice("A szolgáltató elfogadását ellenőrizni kell",
            "${leg.route}: a mentett bérlet dátuma megfelelő, de erre a helyi vagy különleges járatra nincs igazolt elfogadási szabály.")
        if(bkk && leg.route.trim().equals("100E",true)) return PassAdvice("A 100E-re külön jegy szükséges","Az ország- és vármegyebérlet nem érvényes a Repülőtéri Expresszre.")
        val national=active.firstOrNull {it.county==null}
        val counties=active.mapNotNull {it.county}.toSet()
        val territory=geography?.coverage(leg,if(national!=null) null else counties) ?: Coverage.UNKNOWN
        if(territory==Coverage.OUTSIDE) return PassAdvice("A bérlet nem fedi le a teljes útvonalat",
            "${leg.route}: az útvonal a mentett bérletek területén kívül is halad. Erre a szakaszra külön jegy vagy további bérlet kellhet.")
        if(territory==Coverage.UNKNOWN) return PassAdvice("A területi érvényességet ellenőrizni kell",
            "${leg.route}: a dátum megfelelő, de az útvonal hiányzik vagy közel halad a bérlet területének határához. A határmegállók és kivételek feltételeit ellenőrizd a szolgáltatónál.")
        if(bkk && national==null && "Pest" !in counties) return PassAdvice("Ehhez a helyi járathoz másik jegy kell",
            "A BKK járatain a mentett vármegyebérletek közül csak a Pest vármegyebérlet használható.")
        if(leg.mode=="RAIL") {
            val name=hungarianServiceName(leg.category,leg.mode)
            val usuallyUnreserved=name in setOf("Személyvonat","Gyorsított személyvonat","Zónázó vonat","InterRégió","Sebesvonat") ||
                Regex("^[SZG]\\d+$").matches(leg.route.trim())
            if(!usuallyUnreserved && national==null) return PassAdvice("A vonat helyjegymentességét ellenőrizni kell",
                "${leg.route}: a vármegyebérlet csak a helyjegy nélkül igénybe vehető 2. osztályon használható. A vonatkategória alapján a kiválasztott szakasz vagy kocsi helyjegymentessége nem dönthető el.")
            reservationCheck=reservationCheck || !usuallyUnreserved
        }
        used+=national?.name ?: active.filter {it.county in counties}.joinToString(" + ") {it.name}
    }
    return if(reservationCheck) PassAdvice("Országbérlet · a helyjegyet ellenőrizd",
        "A mentett országbérlet dátuma és területe megfelelő. A vonatra vagy kocsiba külön helyjegy kellhet. A kategória nem mutatja a szakaszonkénti kivételeket, ezért a helyjegykötelezettséget a MÁV-nál ellenőrizd.")
    else PassAdvice("A mentett bérlettel utazhatsz",
        "${used.joinToString("; ")}: a dátum és az útvonal területe megfelelő. Vasúton a 2. osztályon, ezek a vonatkategóriák általában helyjegy nélkül használhatók. A külön meghirdetett járati kivételeket ellenőrizd.",true)
}
