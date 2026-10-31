package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StopBoardUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val station = Station("test:station", "Budapest-Déli", 47.499, 19.025)
    @Test fun widgetRefreshCanScheduleANetworkJob() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scheduler = app.getSystemService(android.app.job.JobScheduler::class.java)
            StopBoardWidget.RefreshJobs.forEach(scheduler::cancel)
            try {
                StopBoardWidget.schedule(app)
                val first = scheduler.getPendingJob(7421)!!
                StopBoardWidget.schedule(app)
                val second = scheduler.getPendingJob(7421)!!
                assertEquals(first.extras.getLong("dueAt"), second.extras.getLong("dueAt"))
                assertEquals(1, scheduler.allPendingJobs.count { it.id in StopBoardWidget.RefreshJobs })
                assertEquals(android.app.job.JobInfo.NETWORK_TYPE_ANY, second.networkType)
            } finally { StopBoardWidget.RefreshJobs.forEach(scheduler::cancel) }
        }
    }

    @Test fun boardRecoversFromFailureAndDisplaysScheduledLiveAndCancelledDepartures() {
        var attempts = 0
        var dark by mutableStateOf(false)
        var large by mutableStateOf(false)
        var dismissed = false
        val now = System.currentTimeMillis()
        fun departure(id: String, realtime: Boolean, cancelled: Boolean) = StopDeparture(id, "Székesfehérvár",
            ServiceTime(now+600000, now+660000, realtime), "2", cancelled,
            Leg("RAIL", now+660000, now+700000, station, station, "S30", id, realtime, 60, emptyList(), ""))
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.3f else 1f)) {
                    StopBoardDrawer(station, { dismissed = true }, fetch = {
                        attempts++
                        if (attempts == 1) error("Teszt kapcsolati hiba")
                        listOf(departure("live", true, false), departure("scheduled", false, false), departure("cancelled", true, true))
                    })
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Újrapróbálás").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Újrapróbálás").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Kimarad").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("11 perc").assertCountEquals(2)
        compose.onNodeWithText("Valós idejű adat").assertDoesNotExist()
        compose.onNodeWithText("Menetrend szerint").assertDoesNotExist()
        compose.onNodeWithText(timeLabel(now+600000)).assertDoesNotExist()
        compose.onNodeWithText(timeLabel(now+660000)).assertDoesNotExist()
        compose.onNodeWithText("Kimarad").assertExists()
        compose.onNodeWithText("Widget a kezdőképernyőre").assertHasClickAction()
        compose.runOnIdle { dark = true; large = true }
        compose.onNodeWithText("Budapest-Déli").assertIsDisplayed()
        compose.onNodeWithContentDescription("Indulási tábla bezárása").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals(2, attempts) }
    }

    @Test fun stopVisibilitySettingPersistsAndCanBeRestored() {
        val vm = AppState(app)
        val original = vm.showMapStops
        var filters by mutableStateOf(false)
        try {
            compose.setContent { MenetrendTheme("light") {
                if (filters) MapFilterDrawer(vm) {} else MapTypeDrawer(vm) {}
            } }
            compose.onNodeWithText("Megállók és állomások").assertDoesNotExist()
            compose.runOnIdle { filters = true }
            compose.onNodeWithText("Megállók és állomások").performClick()
            compose.runOnIdle {
                assertEquals(!original, vm.showMapStops)
                assertEquals(!original, AppState(app).showMapStops)
            }
        } finally { compose.runOnIdle { vm.chooseMapStops(original) } }
    }
}
