package hu.matepazy.menetrendgo

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetRowLayoutTest {
    private fun checkLayout(context: Context, width: Int, height: Int): Int {
        val shell = RemoteViews(context.packageName, R.layout.stop_board_widget).apply {
            setTextViewText(R.id.widget_station, "Budapest-Déli")
            setTextViewText(R.id.widget_status, "Frissítve most")
        }
        val candidates = (1..10).map { index -> RemoteViews(context.packageName, R.layout.stop_board_widget_row).apply {
            setTextViewText(R.id.widget_service, "$index személyvonat")
            setTextViewText(R.id.widget_destination, "Győr")
            setTextViewText(R.id.widget_detail, "9. vágány / állás")
            setTextViewText(R.id.widget_countdown, "$index perc")
            if (index % 2 == 0) {
                setTextViewText(R.id.widget_countdown, "60 perc")
                setTextViewText(R.id.widget_departure_time, "14:00")
                setViewVisibility(R.id.widget_departure_time, View.VISIBLE)
            }
            setImageViewResource(R.id.widget_logo, R.drawable.ic_mode_rail)
        } }
        val fitted = fittingWidgetRows(context, shell, candidates, width, height)
        fitted.forEachIndexed { index, row ->
            row.setViewVisibility(R.id.widget_card_gap, if (index == fitted.lastIndex) View.GONE else View.VISIBLE)
            shell.addView(R.id.widget_departures, row)
        }
        val root = shell.apply(context, null)
        val density = context.resources.displayMetrics.density
        val w = (width * density).toInt()
        val h = (height * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST))
        root.layout(0, 0, w, root.measuredHeight)
        val list = root.findViewById<ViewGroup>(R.id.widget_departures)
        assertTrue(fitted.isNotEmpty())
        val last = list.getChildAt(list.childCount - 1)
        fun leadingEdge(view: View): Int = view.left + ((view.parent as? View)?.let { leadingEdge(it) } ?: 0)
        for (index in 0 until list.childCount) {
            val card = list.getChildAt(index)
            val logo = card.findViewById<android.widget.ImageView>(R.id.widget_logo)
            val destination = card.findViewById<View>(R.id.widget_destination)
            assertEquals("Official logo and destination share their leading edge", leadingEdge(destination), leadingEdge(logo))
            assertEquals(android.widget.ImageView.ScaleType.FIT_START, logo.scaleType)
            val clock = card.findViewById<android.widget.TextView>(R.id.widget_departure_time)
            val countdown = card.findViewById<android.widget.TextView>(R.id.widget_countdown)
            if (clock.visibility == View.VISIBLE) {
                assertFalse(clock.typeface.isBold)
                assertTrue(clock.textSize < countdown.textSize)
                assertTrue(clock.top >= countdown.bottom)
            }
            assertTrue("Card padding is retained during fitting", card.findViewById<View>(R.id.widget_row_content).paddingLeft > 0)
            assertEquals("Cards must not stretch to fill launcher space", (16 * density).toInt(), card.findViewById<View>(R.id.widget_row_content).paddingTop)
        }
        assertTrue("Last service must fit completely", last.bottom <= list.height)
        if (fitted.size < candidates.size) assertTrue("No unused bottom band", list.height - last.bottom <= 2)
        assertEquals(View.GONE, last.findViewById<View>(R.id.widget_card_gap).visibility)
        assertTrue("Panel stays within the host", root.height <= h)
        if (fitted.size < candidates.size) {
            // The unused host area must be too small for the next complete card.
            assertTrue(h - root.height < last.height + (12 * density).toInt())
        }
        return fitted.size
    }

    @Test fun fitsMoreThanFiveServicesAndAdaptsToHeightAndFontScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val normal = checkLayout(context, 360, 800)
            assertTrue("A tall widget must not stop at five rows", normal > 5)
            val compact = checkLayout(context, 260, 350)
            assertTrue(compact < normal)
            assertTrue(checkLayout(context, 280, 400) >= 2)
            assertTrue(checkLayout(context, 280, 520) >= 3)
            val largeText = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1.3f })
            assertTrue(checkLayout(largeText, 360, 800) <= normal)
        }
    }
}
