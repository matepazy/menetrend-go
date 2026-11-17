package hu.matepazy.menetrendgo

import android.content.SharedPreferences

/** Apply the new default once, preserving every choice made after this migration. */
internal fun initializeDataSourcePreferences(prefs: SharedPreferences, existingInstallation: Boolean): DataSourceMode {
    if (!prefs.getBoolean("data_source_aggregated_default_applied", false)) {
        val wasAggregated = prefs.getString("data_source_mode", null) == DataSourceMode.Aggregated.id
        prefs.edit()
            .putString("data_source_mode", DataSourceMode.Aggregated.id)
            .putBoolean("data_source_aggregated_default_applied", true)
            .putBoolean("data_source_intro_pending", existingInstallation && !wasAggregated)
            .apply()
    }
    return DataSourceMode.fromId(prefs.getString("data_source_mode", null))
}
