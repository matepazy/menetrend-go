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
        val liveTime = now + 30_000L
        val scheduledTime = now + 10 * 60_000L
        val cancelledTime = now + 12 * 60_000L
        fun departure(id: String, time: ServiceTime, cancelled: Boolean = false) = StopDeparture(
            id, "Székesfehérvár", time, "2", cancelled,
            Leg("RAIL", time.estimate, time.estimate + 60_000L, station, station, "S30", id,
                time.realtime, ((time.estimate - time.scheduled) / 1000).toInt(), emptyList(), ""))
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.3f else 1f)) {
                    StopBoardDrawer(station, { dismissed = true }, fetch = {
                        attempts++
                        if (attempts == 1) error("Teszt kapcsolati hiba")
                        listOf(
                            departure("live", ServiceTime(liveTime - 60_000L, liveTime, true)),
                            departure("scheduled", ServiceTime(scheduledTime, scheduledTime, false)),
                            departure("cancelled", ServiceTime(cancelledTime, cancelledTime + 60_000L, true), cancelled = true)
                        )
                    })
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Újrapróbálás").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Újrapróbálás").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Kimarad").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Most").assertIsDisplayed()
        compose.onNodeWithText("Valós idejű adat").assertDoesNotExist()
        compose.onNodeWithText("Menetrend szerint").assertDoesNotExist()
        compose.onNodeWithText(timeLabel(liveTime)).assertIsDisplayed()
        compose.onNodeWithText(timeLabel(scheduledTime)).assertIsDisplayed()
        compose.onNodeWithText(timeLabel(cancelledTime)).assertIsDisplayed()
        compose.onNodeWithText(timeLabel(cancelledTime + 60_000L)).assertDoesNotExist()
        compose.onNodeWithText("Kimarad").assertExists()
        compose.onNodeWithText("Widget a kezdőképernyőre").assertHasClickAction()
        compose.runOnIdle { dark = true; large = true }
        compose.onNodeWithText("Budapest-Déli").assertIsDisplayed()
        compose.onNodeWithText(timeLabel(liveTime)).assertIsDisplayed()
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
