package hu.matepazy.menetrendgo

import kotlin.math.cos
import kotlin.math.sqrt

/** Where live vehicle positions come from (Beállítások › Adatforrások kezelése). */
enum class DataSourceMode(val id: String, val title: String, val summary: String) {
    Single("single", "Egyforrásos (régi)", "Csak a MÁVPlusz járműadatai. A korábbi, megszokott működés."),
    Aggregated("aggregated", "Aggregált", "A MÁVPlusz és az utas.hu élő adatai egyesítve. Több jármű, a duplikátumok összevonva.");

    companion object {
        fun fromId(id: String?): DataSourceMode = entries.firstOrNull { it.id == id } ?: Single
    }
}

/** Bit flags: which providers contributed to one map vehicle. */
object VehicleSources {
    const val MAV = 1
    const val UTAS = 2
}

/** Same train number reported by both providers within this distance = same vehicle. */
private const val SAME_TRIP_MAX_M = 5_000.0
/** Same route, no train number or plate: must be this close to be the same vehicle. */
private const val SAME_ROUTE_MAX_M = 150.0
/** utas.hu reports older than this are not shown as live positions. */
internal const val UTAS_MAX_AGE_SECONDS = 300L

/** Coarse family: a bus and a train never merge, even with the same number. */
internal fun modeFamily(mode: String): Int = when (mode) {
    "RAIL", "SUBURBAN_RAILWAY", "TRAMTRAIN" -> 0
    "BUS", "COACH", "RAIL_REPLACEMENT_BUS", "TROLLEYBUS" -> 1
    "TRAM", "SUBWAY" -> 2
    "FERRY" -> 3
    else -> 4
}

/** "LKC-088", "lkc 088" → "LKC088". */
internal fun normalizedPlate(value: String): String =
    value.filter { it.isLetterOrDigit() }.uppercase()

/** "IC 552", "0552" → "552": the last run of digits is the train number. */
internal fun trainNumber(value: String): String? =
    value.split(Regex("[^0-9]+")).lastOrNull { it.isNotEmpty() }?.trimStart('0')?.takeIf { it.isNotEmpty() }

private fun normalizedRoute(value: String) = value.filterNot { it.isWhitespace() }.uppercase()

/** Equirectangular approximation, accurate enough at the sub-5 km scale used here. */
internal fun approxDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
    val y = Math.toRadians(lat2 - lat1)
    return sqrt(x * x + y * y) * 6_371_000.0
}

/** The freshest GPS fix wins; every descriptive field is taken from whichever provider has it
 * for this exact vehicle (MÁVPlusz first, so trip identity and timetable access are preserved). */
internal fun absorbVehicle(mav: Vehicle, utas: Vehicle): Vehicle {
    fun pick(a: String, b: String) = a.ifBlank { b }
    val utasFresher = (utas.positionUpdatedSeconds ?: 0) > (mav.positionUpdatedSeconds ?: 0)
    return mav.copy(
        lat = if (utasFresher) utas.lat else mav.lat,
        lon = if (utasFresher) utas.lon else mav.lon,
        heading = (if (utasFresher) utas.heading ?: mav.heading else mav.heading ?: utas.heading),
        positionUpdatedSeconds = if (utasFresher) utas.positionUpdatedSeconds else mav.positionUpdatedSeconds,
        speedMetersPerSecond = mav.speedMetersPerSecond ?: utas.speedMetersPerSecond,
        headsign = pick(mav.headsign, utas.headsign),
        routeColor = pick(mav.routeColor, utas.routeColor),
        routeLongName = pick(mav.routeLongName, utas.routeLongName),
        routeShortName = pick(mav.routeShortName, utas.routeShortName),
        model = pick(mav.model, utas.model),
        registration = pick(mav.registration, utas.registration),
        uicCode = pick(mav.uicCode, utas.uicCode),
        agencyName = pick(mav.agencyName, utas.agencyName),
        routeLogo = mav.routeLogo ?: utas.routeLogo,
        sources = mav.sources or utas.sources,
    )
}

/**
 * Merges utas.hu reports into the MÁVPlusz list. A utas.hu vehicle joins a MÁV vehicle of the
 * same mode family when, in order: (0) it runs the same OTP trip, (1) the licence plate is identical, (2) for rail, the train
 * number is identical within 5 km, (3) the route is identical within 150 m. Each MÁV vehicle
 * absorbs at most one utas.hu report; unmatched, fresh utas.hu vehicles are appended.
 */
internal fun mergeVehicleSources(mav: List<Vehicle>, utas: List<Vehicle>, nowSeconds: Long): List<Vehicle> {
    if (utas.isEmpty()) return mav
    val out = mav.toMutableList()
    val used = BooleanArray(mav.size)
    val byTrip = HashMap<String, Int>()
    val byPlate = HashMap<Pair<Int, String>, Int>()
    val byTrain = HashMap<Pair<Int, String>, MutableList<Int>>()
    val byRoute = HashMap<Pair<Int, String>, MutableList<Int>>()
    mav.forEachIndexed { i, v ->
        val family = modeFamily(v.mode)
        byTrip.putIfAbsent(v.id, i)
        normalizedPlate(v.registration).takeIf { it.length >= 4 }?.let { byPlate.putIfAbsent(family to it, i) }
        if (family == 0) trainNumber(v.name.ifBlank { v.routeShortName })?.let { byTrain.getOrPut(family to it) { mutableListOf() } += i }
        v.routeShortName.takeIf { it.isNotBlank() }?.let { byRoute.getOrPut(family to normalizedRoute(it)) { mutableListOf() } += i }
    }
    val appended = HashSet<String>()
    fun near(i: Int, v: Vehicle, max: Double) = !used[i] && approxDistanceMeters(mav[i].lat, mav[i].lon, v.lat, v.lon) <= max

    for (v in utas) {
        val ts = v.positionUpdatedSeconds
        if (ts != null && nowSeconds - ts > UTAS_MAX_AGE_SECONDS) continue
        val family = modeFamily(v.mode)
        val match = byTrip[v.id]?.takeIf { !used[it] }
            ?: normalizedPlate(v.registration).takeIf { it.length >= 4 }
            ?.let { byPlate[family to it] }?.takeIf { !used[it] }
            ?: (if (family == 0) trainNumber(v.name)?.let { n -> byTrain[family to n]?.firstOrNull { near(it, v, SAME_TRIP_MAX_M) } } else null)
            ?: v.routeShortName.takeIf { it.isNotBlank() }
                ?.let { r -> byRoute[family to normalizedRoute(r)]?.firstOrNull { near(it, v, SAME_ROUTE_MAX_M) } }
        if (match != null) {
            used[match] = true
            out[match] = absorbVehicle(out[match], v)
        // Map ids must stay unique: a second vehicle on an already shown trip keeps its utas.hu id.
        } else out += if (v.id in byTrip || !appended.add(v.id)) v.copy(id = "utas:${v.vehicleId}") else v
    }
    return out
}
