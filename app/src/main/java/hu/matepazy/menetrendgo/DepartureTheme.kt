package hu.matepazy.menetrendgo

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews

internal fun savedTheme(context: Context): String =
    context.getSharedPreferences("menetrendgo", Context.MODE_PRIVATE).getString("theme", "system") ?: "system"

internal fun departureThemeContext(context: Context, dark: Boolean = when (savedTheme(context)) {
    "light" -> false
    "dark", "oled" -> true
    else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}): Context = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
})

/** Launcher hosts inflate XML with their own configuration. Send every theme role explicitly. */
internal fun RemoteViews.applyDepartureTheme(context: Context) {
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val oled = dark && savedTheme(context) == "oled"
    when (layoutId) {
        R.layout.stop_board_widget -> {
            setInt(R.id.widget_panel, "setBackgroundResource", if (oled) R.drawable.stop_widget_background_oled else if (dark) R.drawable.stop_widget_background_dark else R.drawable.stop_widget_background_light)
            setTextColor(R.id.widget_station, context.getColor(R.color.widget_ink))
            setTextColor(R.id.widget_status, context.getColor(R.color.widget_muted))
            setInt(R.id.widget_refresh, "setColorFilter", context.getColor(R.color.widget_action))
        }
        R.layout.stop_board_widget_row -> {
            setInt(R.id.widget_row_content, "setBackgroundResource", if (dark) R.drawable.stop_widget_card_dark else R.drawable.stop_widget_card_light)
            setTextColor(R.id.widget_service, context.getColor(R.color.widget_ink))
            setTextColor(R.id.widget_destination, context.getColor(R.color.widget_ink))
            setTextColor(R.id.widget_detail, context.getColor(R.color.widget_muted))
            setTextColor(R.id.widget_departure_time, context.getColor(R.color.widget_muted))
        }
        R.layout.stop_board_widget_message -> setTextColor(R.id.widget_row_text, context.getColor(R.color.widget_ink))
    }
}
