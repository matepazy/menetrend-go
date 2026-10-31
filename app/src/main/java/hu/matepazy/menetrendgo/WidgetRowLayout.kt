package hu.matepazy.menetrendgo

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** Fit real native rows, including the current font scale and wrapping, into the host size. */
internal fun fittingWidgetRows(context: Context, shell: RemoteViews, rows: List<RemoteViews>,
    widthDp: Int, heightDp: Int): List<RemoteViews> {
    if (rows.isEmpty()) return emptyList()
    val density = context.resources.displayMetrics.density
    val root = shell.apply(context, null)
    root.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).roundToInt().coerceAtLeast(1), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val list = root.findViewById<ViewGroup>(R.id.widget_departures)
    val available = (heightDp * density).roundToInt() - root.measuredHeight
    val fitted = mutableListOf<RemoteViews>()
    var used = 0
    for (row in rows) {
        val view = row.apply(context, list)
        view.measure(View.MeasureSpec.makeMeasureSpec(list.measuredWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val gap = view.findViewById<View>(R.id.widget_card_gap).measuredHeight
        if (used + view.measuredHeight - gap > available) break
        fitted += row
        used += view.measuredHeight
    }
    // Keep the card rhythm at every host size. The wrap-content panel ends after the
    // final card instead of turning unused launcher space into oversized card padding.
    return fitted
}
