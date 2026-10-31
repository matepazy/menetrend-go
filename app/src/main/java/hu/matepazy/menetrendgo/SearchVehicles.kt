package hu.matepazy.menetrendgo

/** Empty selection means every supported mode; walking remains available for transfers. */
enum class SearchVehicle(val title: String, val mode: String) {
    Train("Vonatok", "RAIL"),
    Coach("Helyközi autóbuszok", "COACH"),
    LocalBus("Helyi autóbuszok", "BUS"),
    Hev("HÉV", "SUBURBAN_RAILWAY"),
    TramTrain("TramTrain", "TRAMTRAIN");
}

internal fun toggleSearchVehicle(selected: Set<SearchVehicle>, vehicle: SearchVehicle): Set<SearchVehicle> {
    val current = selected.ifEmpty { SearchVehicle.entries.toSet() }
    val next = if (vehicle in current) current - vehicle else current + vehicle
    // Route planning needs a transit mode; the last enabled switch stays on.
    return when (next.size) {
        0 -> selected
        SearchVehicle.entries.size -> emptySet()
        else -> next
    }
}

internal fun searchVehicleModes(selected: Set<SearchVehicle>): List<String> =
    SearchVehicle.entries.filter { selected.isEmpty() || it in selected }.map { it.mode } + "WALK"

internal fun searchVehicleSummary(selected: Set<SearchVehicle>): String =
    SearchVehicle.entries.filter { it in selected }.joinToString(" · ") { it.title }

internal fun filterJourneyVehicles(journeys: List<Journey>, selected: Set<SearchVehicle>): List<Journey> {
    if (selected.isEmpty()) return journeys
    val modes = selected.map { it.mode }.toSet()
    return journeys.filter { journey ->
        val transit = journey.legs.filterNot { it.walking }
        transit.isNotEmpty() && transit.all { it.mode in modes }
    }
}
