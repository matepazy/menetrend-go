package hu.matepazy.menetrendgo

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

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

internal fun RemoteViews.bindWidgetService(context: Context, departure: StopDeparture) {
    val leg = departure.leg
    val ink = context.getColor(R.color.widget_ink)
    val logo = leg.displayRouteLogo?.let { routeLogoBitmap(context, it) }
    val fill = serviceColorValue(leg) ?: context.getColor(R.color.widget_muted)
    val inline = leg.mode.isBusService && departure.headsign.isNotBlank()
    val labelColor = if (logo != null) ink
        else if (ColorUtils.calculateLuminance(fill) > .179) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    val horizontal = context.resources.getDimensionPixelSize(R.dimen.service_badge_horizontal_padding)
    val vertical = context.resources.getDimensionPixelSize(R.dimen.service_badge_vertical_padding)

    setTextViewText(R.id.widget_service, leg.displayRoute)
    setTextColor(R.id.widget_service, labelColor)
    setViewVisibility(R.id.widget_logo, if (logo == null) View.GONE else View.VISIBLE)
    setViewVisibility(R.id.widget_badge_fill, if (logo == null) View.VISIBLE else View.GONE)
    if (logo != null) {
        setImageViewBitmap(R.id.widget_logo, logo)
    } else {
        setViewPadding(R.id.widget_service, horizontal, vertical, horizontal, vertical)
        setInt(R.id.widget_badge_fill, "setColorFilter", fill)
    }
    setViewVisibility(R.id.widget_inline_direction, if (inline) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.widget_inline_destination, if (inline) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.widget_direction_row, if (inline || departure.headsign.isBlank()) View.GONE else View.VISIBLE)
    setInt(R.id.widget_direction, "setColorFilter", ink)
    setInt(R.id.widget_inline_direction, "setColorFilter", ink)
    setTextColor(R.id.widget_inline_destination, ink)
    setTextViewText(if (inline) R.id.widget_inline_destination else R.id.widget_destination, departure.headsign)
}
