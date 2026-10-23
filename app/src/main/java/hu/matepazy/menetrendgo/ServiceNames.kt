package hu.matepazy.menetrendgo

import java.util.Locale

/** Provider categories can be English even when the rest of the response is Hungarian. */
fun hungarianServiceName(category: String, mode: String): String {
    val name = category.trim()
    return when (name.lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')) {
        "passenger", "passenger train", "passanger train", "local train", "regional train" -> "Személyvonat"
        "fast train" -> "Gyorsvonat"
        "semi fast train", "semi fast" -> "Sebesvonat"
        "express", "express train" -> "Expresszvonat"
        "accelerated passenger train", "accelerated train" -> "Gyorsított személyvonat"
        "zonal train", "zone train" -> "Zónázó vonat"
        "intercity", "intercity train" -> "InterCity"
        "eurocity", "eurocity train" -> "EuroCity"
        "interregio", "interregio train" -> "InterRégió"
        "rail replacement bus", "replacement bus" -> "Vonatpótló autóbusz"
        "bus", "coach" -> "Autóbusz"
        "suburban railway", "suburban train" -> "HÉV"
        "tram" -> "Villamos"
        "", "rail", "train" -> when (mode) {
            "WALK" -> "Gyalog"
            "BUS", "COACH" -> "Autóbusz"
            "RAIL_REPLACEMENT_BUS" -> "Vonatpótló autóbusz"
            "SUBURBAN_RAILWAY" -> "HÉV"
            "TRAMTRAIN" -> "TramTrain"
            "TRAM" -> "Villamos"
            "FERRY" -> "Hajó"
            else -> "Vonat"
        }
        else -> name
    }
}
