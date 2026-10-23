package hu.matepazy.menetrendgo

import java.util.Locale

/** Trip numbers and named trains may embed the English category in the label. */
fun hungarianServiceLabel(label: String): String {
    var result=label
    for(category in listOf("accelerated passenger train", "semi-fast train", "passenger train", "passanger train",
        "regional train", "local train", "fast train", "express train", "zonal train", "intercity train", "eurocity train")) {
        result=result.replace(Regex("\\b${Regex.escape(category)}\\b",RegexOption.IGNORE_CASE),hungarianServiceName(category,"RAIL"))
    }
    return result
}

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
