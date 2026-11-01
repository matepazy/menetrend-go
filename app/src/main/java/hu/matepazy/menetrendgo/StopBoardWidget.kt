package hu.matepazy.menetrendgo

import android.app.PendingIntent
import android.app.AlarmManager
import android.app.job.*
import android.appwidget.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject

class StopBoardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, it) }; schedule(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove("station_$it").remove("board_$it").remove("departures_$it").remove("updated_$it").remove("error_$it").remove("attempt_$it") }
        edit.apply()
        if (widgetIds(context).none { it !in ids }) {
            context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
            RefreshJobs.forEach { context.getSystemService(JobScheduler::class.java).cancel(it) }
        } else planNext(context)
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { render(context, id) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val ids = widgetIds(context)
            ids.forEach { render(context, it) }
            if (ids.isNotEmpty()) schedule(context)
        }
        if (intent.action == TICK || intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            widgetIds(context).forEach { render(context, it) }
            if (nextRefreshAt(context) <= System.currentTimeMillis()) schedule(context)
            else if (intent.action != TICK) planNext(context)
        }
        if (intent.action == PINNED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val owner = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
            if (owner == ComponentName(context, StopBoardWidget::class.java)) {
                val station = runCatching { Station.parse(JSONObject(intent.getStringExtra("station") ?: "")) }.getOrNull()
                if (station != null) { save(context, id, station); render(context, id); schedule(context) }
            }
        }
        if (intent.action == REFRESH) {
            widgetIds(context).forEach { render(context, it, refreshing = true) }
            schedule(context)
        }
    }
    companion object {
        private const val PINNED = "hu.matepazy.menetrendgo.STOP_WIDGET_PINNED"
        private const val REFRESH = "hu.matepazy.menetrendgo.STOP_WIDGET_REFRESH"
        private const val TICK = "hu.matepazy.menetrendgo.STOP_WIDGET_TICK"
        internal val RefreshJobs = intArrayOf(7421, 7422)
        private fun tickIntent(context: Context) = PendingIntent.getBroadcast(context, 1,
            Intent(context, StopBoardWidget::class.java).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        internal fun prefs(context: Context) = context.getSharedPreferences("stop_widgets", Context.MODE_PRIVATE)
        internal fun widgetIds(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StopBoardWidget::class.java))
        internal fun station(context: Context, id: Int): Station? = runCatching {
            Station.parse(JSONObject(prefs(context).getString("station_$id", "") ?: ""))
        }.getOrNull()
        internal fun save(context: Context, id: Int, station: Station) {
            prefs(context).edit().putString("station_$id", station.json().toString()).remove("board_$id").remove("departures_$id").remove("updated_$id").remove("error_$id").remove("attempt_$id").apply()
        }
        fun requestPin(context: Context, station: Station): String {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return "A kezdőképernyő widgetválasztójában add hozzá a MenetrendGO indulási táblát."
            val callback = Intent(context, StopBoardWidget::class.java).setAction(PINNED)
                .setData(Uri.parse("menetrendgo://pin/${java.util.UUID.randomUUID()}"))
                .putExtra("station", station.json().toString())
            val pending = PendingIntent.getBroadcast(context, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            manager.requestPinAppWidget(ComponentName(context, StopBoardWidget::class.java), Bundle(), pending)
            return "Erősítsd meg a hozzáadást a kezdőképernyő ablakában."
        }
        internal fun schedule(context: Context) {
            // Local countdown redraws do not fetch data unless a network refresh is due.
            context.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + 60_000, 60_000, tickIntent(context))
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val now = System.currentTimeMillis()
            // An eligible/running request already covers this tap. Never cancel its fetch.
            if (RefreshJobs.any { scheduler.getPendingJob(it)?.extras?.getLong("dueAt", Long.MAX_VALUE)?.let { due -> due <= now } == true }) return
            RefreshJobs.forEach(scheduler::cancel)
            enqueue(context, RefreshJobs[0], now)
        }
        internal fun cachedRows(context: Context, id: Int): List<StopDeparture> = runCatching {
            org.json.JSONArray(prefs(context).getString("departures_$id", "[]")).objects().map(::cachedDeparture).take(WidgetDeparturePoolSize)
        }.getOrDefault(emptyList())
        internal fun nextRefreshAt(context: Context, now: Long = System.currentTimeMillis()): Long {
            val prefs = prefs(context)
            return widgetIds(context).filter { station(context, it) != null }.minOfOrNull { id ->
                widgetRefreshAt(prefs.getLong("updated_$id", 0), prefs.getLong("attempt_$id", 0),
                    prefs.getBoolean("error_$id", false), cachedRows(context, id), now)
            } ?: Long.MAX_VALUE
        }
        internal fun planNext(context: Context, finishedJob: Int = 7422) {
            val dueAt = nextRefreshAt(context)
            if (dueAt == Long.MAX_VALUE) return
            // Alternate IDs: finishing one job must not stop its own running coroutine.
            val nextJob = if (finishedJob == 7421) 7422 else 7421
            val pending = context.getSystemService(JobScheduler::class.java).getPendingJob(nextJob)
            if (pending != null && pending.extras.getLong("dueAt", Long.MAX_VALUE) <= System.currentTimeMillis()) return
            enqueue(context, nextJob, dueAt)
        }
        private fun enqueue(context: Context, id: Int, dueAt: Long) {
            val delay = (dueAt - System.currentTimeMillis()).coerceAtLeast(0)
            context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(id, ComponentName(context, StopBoardWidgetJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delay)
                .setOverrideDeadline((delay + WidgetRefreshInterval).coerceAtMost(2 * WidgetRefreshInterval))
                .setExtras(android.os.PersistableBundle().apply { putLong("dueAt", dueAt) }).build())
        }
        internal fun render(context: Context, id: Int, refreshing: Boolean = false) {
            val themeContext = departureThemeContext(context)
            val station = station(context, id)
            val prefs = prefs(context)
            val updated = prefs.getLong("updated_$id", 0)
            val now = System.currentTimeMillis()
            val rows = upcomingWidgetDepartures(cachedRows(context, id), now)
            val error = prefs.getBoolean("error_$id", false)
            val manager = AppWidgetManager.getInstance(context)
            val options = manager.getAppWidgetOptions(id)
            fun sizedViews(width: Int, height: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.stop_board_widget).apply { applyDepartureTheme(themeContext) }
            views.setTextViewText(R.id.widget_station, station?.name ?: "Válassz megállót")
            val lastUpdate = if (updated > 0) updateAge(updated, now) else ""
            views.setTextViewText(R.id.widget_status, when {
                refreshing -> "Frissítés…"
                error -> "Sikertelen frissítés${if (lastUpdate.isBlank()) "" else " · $lastUpdate"}"
                updated == 0L -> "Indulások betöltése…"
                else -> "Frissítve $lastUpdate"
            })
            views.removeAllViews(R.id.widget_departures)
            if (rows.isEmpty()) {
                val empty = RemoteViews(context.packageName, R.layout.stop_board_widget_message).apply { applyDepartureTheme(themeContext) }
                empty.setTextViewText(R.id.widget_row_text, if (updated > 0) "Nincs további tárolt indulás. Frissítsd a táblát." else "A tábla internetkapcsolattal frissül.")
                views.addView(R.id.widget_departures, empty)
            }
            val candidates = rows.map { departure ->
                val row = RemoteViews(context.packageName, R.layout.stop_board_widget_row).apply { applyDepartureTheme(themeContext) }
                row.setTextViewText(R.id.widget_service, departure.leg.route)
                row.setTextViewText(R.id.widget_destination, departure.headsign)
                row.setTextViewText(R.id.widget_detail, if (departure.platform.isBlank()) "" else "${departure.platform}. vágány / állás")
                row.setViewVisibility(R.id.widget_detail, if (departure.platform.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
                row.setTextViewText(R.id.widget_countdown, if (departure.cancelled) "Kimarad" else departureCountdown(departure.time.estimate, now))
                val clock = departureClockTime(departure.time.estimate, now, departure.cancelled)
                row.setTextViewText(R.id.widget_departure_time, clock.orEmpty())
                row.setViewVisibility(R.id.widget_departure_time, if (clock == null) android.view.View.GONE else android.view.View.VISIBLE)
                row.setTextColor(R.id.widget_countdown, themeContext.getColor(departureColorResource(departure.time, departure.cancelled)))
                row.setContentDescription(R.id.widget_countdown, (if (departure.cancelled) "Kimarad" else departureCountdown(departure.time.estimate, now)) + ", " + departureStateDescription(departure))
                val bitmap = departure.leg.routeLogo?.let { routeLogoBitmap(context, it) }
                if (bitmap != null) row.setImageViewBitmap(R.id.widget_logo, bitmap)
                else row.setImageViewResource(R.id.widget_logo, when (departure.leg.mode) {
                    "BUS" -> R.drawable.ic_mode_local_bus
                    "COACH" -> R.drawable.ic_mode_coach
                    "SUBURBAN_RAILWAY" -> R.drawable.ic_mode_hev
                    "TRAMTRAIN" -> R.drawable.ic_mode_tramtrain
                    "RAIL_REPLACEMENT_BUS" -> R.drawable.ic_mode_replacement_bus
                    else -> R.drawable.ic_mode_rail
                })
                row.setContentDescription(R.id.widget_logo, null)
                row
            }
            val fitted = fittingWidgetRows(context, views, candidates, width, height)
            if (fitted.isEmpty() && candidates.isNotEmpty()) {
                val hint = RemoteViews(context.packageName, R.layout.stop_board_widget_message).apply { applyDepartureTheme(themeContext) }
                hint.setTextViewText(R.id.widget_row_text, "Nagyítsd a widgetet az indulásokhoz.")
                views.addView(R.id.widget_departures, hint)
            }
            fitted.forEachIndexed { index, row ->
                row.setViewVisibility(R.id.widget_card_gap, if (index == fitted.lastIndex) android.view.View.GONE else android.view.View.VISIBLE)
                views.addView(R.id.widget_departures, row)
            }
            val open = Intent(context, StopBoardWidgetActivity::class.java).setAction("board").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("menetrendgo://board/$id"))
            views.setOnClickPendingIntent(R.id.widget_body, PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val refresh = Intent(context, StopBoardWidget::class.java).setAction(REFRESH)
            views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 0, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
            }
            val sizes = if (android.os.Build.VERSION.SDK_INT >= 31)
                options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.distinct().orEmpty()
                else emptyList()
            val views = if (android.os.Build.VERSION.SDK_INT >= 31 && sizes.isNotEmpty()) {
                RemoteViews(sizes.take(16).associateWith { size -> sizedViews(size.width.toInt(), size.height.toInt()) })
            } else {
                val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                sizedViews(options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300),
                    options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

class StopBoardWidgetJob : JobService() {
    private val tasks = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        tasks[params.jobId] = CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = MavClient()
                StopBoardWidget.widgetIds(this@StopBoardWidgetJob).forEach { id ->
                    val station = StopBoardWidget.station(this@StopBoardWidgetJob, id) ?: return@forEach
                    StopBoardWidget.prefs(this@StopBoardWidgetJob).edit().putLong("attempt_$id", System.currentTimeMillis()).apply()
                    try {
                        val board = client.stopDepartures(station.id)
                        if (StopBoardWidget.station(this@StopBoardWidgetJob, id)?.id != station.id) return@forEach
                        StopBoardWidget.prefs(this@StopBoardWidgetJob).edit()
                            .putString("departures_$id", org.json.JSONArray(board.take(WidgetDeparturePoolSize).map { it.cacheJson() }).toString()).remove("board_$id")
                            .putLong("updated_$id", System.currentTimeMillis()).putBoolean("error_$id", false).apply()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { StopBoardWidget.prefs(this@StopBoardWidgetJob).edit().putBoolean("error_$id", true).apply() }
                    StopBoardWidget.render(this@StopBoardWidgetJob, id)
                }
                withContext(Dispatchers.Main) {
                    tasks.remove(params.jobId)
                    jobFinished(params, false)
                    StopBoardWidget.planNext(this@StopBoardWidgetJob, params.jobId)
                }
            } catch (e: CancellationException) { throw e }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { tasks.remove(params.jobId)?.cancel(); return true }
    override fun onDestroy() { tasks.values.forEach { it.cancel() }; tasks.clear(); super.onDestroy() }
}

class StopBoardWidgetActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (intent.action == "board" && StopBoardWidget.station(this, id) != null &&
            AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider == ComponentName(this, StopBoardWidget::class.java)) {
            StopBoardWidget.schedule(this)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider != ComponentName(this, StopBoardWidget::class.java)) { finish(); return }
        val station = StopBoardWidget.station(this, id)
        val isBoard = intent.action == "board" && station != null
        setContent { MenetrendTheme(savedTheme(this)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (isBoard) StopBoardDrawer(station!!, dismiss={ finish() })
                else WidgetStationPicker { selected ->
                    StopBoardWidget.save(this, id, selected)
                    StopBoardWidget.render(this, id)
                    StopBoardWidget.schedule(this)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                }
            }
        } }
    }
}

@Composable private fun WidgetStationPicker(select: (Station) -> Unit) {
    var query by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Station>>(emptyList()) }
    var stops by remember { mutableStateOf<List<Station>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var lookup by remember { mutableStateOf<Job?>(null) }
    val client = remember { MavClient() }
    LaunchedEffect(query) {
        lookup?.cancel(); loading = false
        stops = null; places = emptyList(); error = null
        if (query.trim().length < 2) return@LaunchedEffect
        delay(350); loading = true
        try { places = client.stations(query) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    DisposableEffect(Unit) { onDispose { lookup?.cancel() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Indulási tábla widget", style = MaterialTheme.typography.headlineSmall)
        Text("Keress egy állomást vagy megállót, majd válaszd ki a pontos helyet.")
        OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Állomás vagy megálló") }, singleLine = true)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && query.length >= 2 && (stops ?: places).isEmpty() && error == null) Text("Nincs találat. Próbálj másik megállónevet.")
        LazyColumn {
            items(stops ?: places, key = { it.id }) { place ->
                TextButton(onClick = {
                    if (stops != null) select(place)
                    else { lookup = scope.launch {
                        loading = true; error = null
                        try { stops = client.nearbyStops(place.lat, place.lon, 700) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { loading = false }
                    } }
                }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(place.name + if (place.platform.isBlank()) "" else " · ${place.platform}. állás")
                }
            }
        }
    }
}
