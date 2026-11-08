package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DepartureThemeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun launcherInflationKeepsTheSelectedPaletteEvenWithTheOppositeHostTheme() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val prefs = context.getSharedPreferences("menetrendgo", 0)
            val original = prefs.getString("theme", null)
            try {
                for (choice in listOf("light", "dark", "oled")) {
                    val dark = choice != "light"
                    val host = departureThemeContext(context, !dark)
                    prefs.edit().putString("theme", choice).commit()
                    val selected = departureThemeContext(host)
                    assertEquals(dark, selected.resources.configuration.uiMode and 0x30 == 0x20)
                    val shell = RemoteViews(context.packageName, R.layout.stop_board_widget).apply {
                        applyDepartureTheme(selected)
                    }.apply(host, null)
                    val panel = shell.findViewById<View>(R.id.widget_panel)
                    val panelBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                    panel.background.setBounds(0, 0, 100, 100)
                    panel.background.draw(Canvas(panelBitmap))
                    assertEquals(if (choice == "oled") android.graphics.Color.BLACK else selected.getColor(R.color.widget_surface), panelBitmap.getPixel(50, 50))
                    assertEquals(selected.getColor(R.color.widget_ink), shell.findViewById<TextView>(R.id.widget_station).currentTextColor)
                    val view = RemoteViews(context.packageName, R.layout.stop_board_widget_row).apply {
                        applyDepartureTheme(selected)
                        setTextColor(R.id.widget_countdown, selected.getColor(R.color.departure_scheduled))
                    }.apply(host, null)
                    val card = view.findViewById<View>(R.id.widget_row_content)
                    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                    card.background.setBounds(0, 0, 100, 100)
                    card.background.draw(Canvas(bitmap))
                    val background = bitmap.getPixel(50, 50)
                    assertEquals(selected.getColor(R.color.widget_card), background)
                    assertEquals(selected.getColor(R.color.widget_ink), view.findViewById<TextView>(R.id.widget_destination).currentTextColor)
                    for (color in listOf(R.color.departure_scheduled, R.color.departure_green, R.color.departure_yellow, R.color.departure_red)) {
                        assertTrue("Countdown contrast in ${if (dark) "dark" else "light"} mode", ColorUtils.calculateContrast(selected.getColor(color), background) >= 3.0)
                    }
                }
            } finally {
                prefs.edit().apply { if (original == null) remove("theme") else putString("theme", original) }.commit()
            }
        }
    }

    @Test fun countdownUsesTheAppThemeWhenTheSystemThemeDisagrees() {
        var dark by mutableStateOf(false)
        val station = Station("theme-test", "Budapest-Déli", 47.499, 19.025)
        val now = System.currentTimeMillis()
        val time = now + 600_000
        val departure = StopDeparture("theme-trip", "Győr", ServiceTime(time, time, false), "", false,
            Leg("RAIL", time, time + 600_000, station, station, "S10", "theme-trip", false, 0, emptyList(), ""))
        compose.setContent {
            val oppositeSystem = remember(dark) { departureThemeContext(context, !dark) }
            CompositionLocalProvider(LocalContext provides oppositeSystem) {
                MenetrendTheme(if (dark) "dark" else "light") {
                    StopBoardDrawer(station, {}, fetch = { listOf(departure) })
                }
            }
        }
        for (useDark in listOf(false, true)) {
            compose.runOnIdle { dark = useDark }
            compose.waitUntil(5000) { compose.onAllNodesWithText("10 perc").fetchSemanticsNodes().isNotEmpty() }
            val pixels = compose.onNodeWithText("10 perc").captureToImage().toPixelMap()
            val expected = departureThemeContext(context, useDark).getColor(R.color.departure_scheduled)
            var matching = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].toArgb() == expected) matching++
            }
            assertTrue("Countdown must use the selected palette", matching > 10)
        }
    }
}
