package hu.matepazy.menetrendgo

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant
import android.net.Uri
import androidx.core.location.LocationManagerCompat.getCurrentLocation

internal data class TrackingView(
    val session: TrackingSession? = null,
    val running: Boolean = false,
    val message: String = "Helymeghatározás…",
    val estimate: TrackingEstimate? = null,
)

internal object JourneyTracking {
    private val mutable = MutableStateFlow(TrackingView())
    val state = mutable.asStateFlow()
    fun enabled(context: Context): Boolean = context.getSharedPreferences("menetrendgo", Context.MODE_PRIVATE)
        .getBoolean("journey_tracking_beta_enabled", false)
    private fun prefs(context: Context) = context.getSharedPreferences("journey_tracking", Context.MODE_PRIVATE)
    fun saved(context: Context): TrackingSession? = runCatching {
        prefs(context).getString("session", null)?.let { TrackingSession.parse(JSONObject(it)) }
    }.getOrNull()?.takeIf { System.currentTimeMillis() - it.startedAt in 0..18 * 60 * 60_000L }
    fun load(context: Context) {
        if (!enabled(context)) { stop(context); return }
        if (mutable.value.session == null) saved(context)?.let {
            mutable.value = TrackingView(it, message = "A követés szünetel. Folytasd, ha még ezen az úton vagy.")
        }
    }
    fun publish(view: TrackingView) { mutable.value = view }
    fun persist(context: Context, session: TrackingSession, durable: Boolean = false): Boolean {
        val editor = prefs(context).edit().putString("session", session.json().toString())
        if (durable) return editor.commit()
        editor.apply(); return true
    }
    fun clear(context: Context) {
        prefs(context).edit().remove("session").commit()
        mutable.value = TrackingView()
    }
    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            listOf(JourneyTrackingService.ONGOING, JourneyTrackingService.ALERTS).none {
                manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE
            }
    }
    fun start(context: Context, session: TrackingSession) {
        check(enabled(context)) { "A leszállási jelzés béta funkció nincs bekapcsolva." }
        check(persist(context, session, durable = true)) { "Nem sikerült menteni az utazás követését." }
        mutable.value = TrackingView(session, message = "Követés indítása…")
        ContextCompat.startForegroundService(context, Intent(context, JourneyTrackingService::class.java).setAction("start"))
    }
    fun stop(context: Context) {
        clear(context)
        context.stopService(Intent(context, JourneyTrackingService::class.java))
        NotificationManagerCompat.from(context).cancel(JourneyTrackingService.ALERT_ID)
    }
    fun controlIntent(context: Context, action: String, session: TrackingSession) =
        Intent(context, JourneyTrackingService::class.java).setAction(action)
            .setData(Uri.parse("menetrendgo://tracking/${Uri.encode(session.controlToken)}/$action"))
            .putExtra("trackingToken", session.controlToken)
    fun board(context: Context, session: TrackingSession) { context.startService(controlIntent(context, "board", session)) }
}

/** User-started navigation, independent of Activity/ViewModel lifecycle. No background-location grant. */
class JourneyTrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = MavClient()
    private lateinit var locations: LocationManager
    private var session: TrackingSession? = null
    private var engine: JourneyProgress? = null
    private var motion: TrackingMotion? = null
    private val fixes = mutableMapOf<String, CancellationSignal>()
    private val registeredProviders = mutableSetOf<String>()
    private var lastFixNanos: Long? = null
    private var lastGoodFixNanos: Long? = null
    private var lastGoodAccuracy = Float.POSITIVE_INFINITY
    private var lastPersist = 0L
    private var liveArrival: Pair<Long, Long>? = null
    private var estimate: TrackingEstimate? = null
    private var lastRouteEstimate: TrackingEstimate? = null
    private var message = "Helymeghatározás…"
    private var timer: Job? = null
    private var fetcher: Job? = null

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ONGOING, "Utazás követése", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(ALERTS, "Leszállási figyelmeztetések", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Figyelmeztetés a kiválasztott leszállóhely előtt"
            enableVibration(true)
            // Android delivers this with the notification, including while the screen is off.
            // Existing channels retain the user's settings and their original default pattern.
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
        })
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!JourneyTracking.enabled(this)) {
            JourneyTracking.clear(this)
            NotificationManagerCompat.from(this).cancel(ALERT_ID)
            stopSelf(); return START_NOT_STICKY
        }
        val restored = if (intent?.action == "start" || session == null) JourneyTracking.saved(this) else session
        if (intent?.action in listOf("stop", "board") && intent?.getStringExtra("trackingToken") != restored?.controlToken) {
            if (session == null) stopSelf()
            return if (session == null) START_NOT_STICKY else START_STICKY
        }
        if (intent?.action == "stop") {
            JourneyTracking.clear(this)
            NotificationManagerCompat.from(this).cancel(ALERT_ID)
            session = null; stopSelf(); return START_NOT_STICKY
        }
        if (restored == null || !hasLocation() || !JourneyTracking.notificationsEnabled(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        session = restored
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, notification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        catch (_: IllegalStateException) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "board" && restored.awaitingBoarding) {
            val next = restored.nextLegIndex
            if (next == null) { finish(); return START_NOT_STICKY }
            session = restored.copy(legIndex = next, awaitingBoarding = false, alerted = false, progress = null)
            NotificationManagerCompat.from(this).cancel(ALERT_ID)
        }
        engine = JourneyProgress(session!!.leg)
        motion = TrackingMotion(session!!.leg.mode)
        estimate = null; lastRouteEstimate = null; lastFixNanos = null; lastGoodFixNanos = null; liveArrival = null
        lastGoodAccuracy = Float.POSITIVE_INFINITY
        message = if (session!!.awaitingBoarding) "Szállj át, majd erősítsd meg a felszállást." else "Helymeghatározás…"
        JourneyTracking.persist(this, session!!, durable = true)
        stopLocations()
        if (!session!!.awaitingBoarding) locations.allProviders.filter { it != LocationManager.PASSIVE_PROVIDER }.forEach(::requestProvider)
        timer?.cancel()
        timer = scope.launch {
            while (isActive) {
                val current = session ?: break
                if (!JourneyTracking.enabled(this@JourneyTrackingService)) { finish(); break }
                if (System.currentTimeMillis() - current.startedAt > 18 * 60 * 60_000L) { finish(); break }
                if (!hasLocation() || !JourneyTracking.notificationsEnabled(this@JourneyTrackingService)) {
                    message = "A követés leállt. Ellenőrizd a hely- és értesítési engedélyeket."
                    stopSelf(); break
                }
                if (!current.awaitingBoarding) {
                    if (!LocationManagerCompat.isLocationEnabled(locations)) {
                        message = "Kapcsold be a helymeghatározást. A leszállási jelzés most nem megbízható."
                        estimate = null
                    } else if (lastGoodFixNanos == null || (SystemClock.elapsedRealtimeNanos() - lastGoodFixNanos!!) > 45_000_000_000L) {
                        message = "Pontos helyjelre várunk. A leszállási jelzés most nem megbízható."
                        estimate = null
                    }
                }
                val live = liveArrival
                val locationAge = lastGoodFixNanos?.let { (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000 } ?: Long.MAX_VALUE
                if (locationAge > 15_000 && liveArrivalWarning(current, live?.first,
                        live?.let { SystemClock.elapsedRealtime() - it.second } ?: Long.MAX_VALUE,
                        locationAge, lastRouteEstimate?.remainingMeters, System.currentTimeMillis())) {
                    if (alert(current, liveBased = true)) {
                        session = current.copy(alerted = true)
                        JourneyTracking.persist(this@JourneyTrackingService, session!!, durable = true)
                        message = "Élő érkezés alapján készülj a leszállásra. A helyjel bizonytalan."
                    }
                }
                publish()
                delay(10_000)
            }
        }
        fetcher?.cancel()
        fetcher = scope.launch {
            while (isActive) {
                val current = session ?: break
                if (!current.awaitingBoarding && current.leg.tripId.isNotBlank()) {
                    try {
                        val leg = current.leg
                        val day = Instant.ofEpochMilli(leg.start).atZone(Budapest).toLocalDate().toString()
                        val arrival = trackingArrival(client.trip(leg.tripId, day, forceRefresh = true), leg)
                        if (session?.legIndex == current.legIndex && session?.awaitingBoarding == false) {
                            liveArrival = arrival?.let { it to SystemClock.elapsedRealtime() }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { liveArrival = null }
                }
                delay(if ((lastRouteEstimate?.remainingMeters ?: Double.MAX_VALUE) <= 5_000) 30_000 else 75_000)
            }
        }
        publish()
        return START_STICKY
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun requestProvider(provider: String) {
        if (session == null || session!!.awaitingBoarding || !hasLocation() || provider == LocationManager.PASSIVE_PROVIDER) return
        try {
            // Zero distance keeps fresh fixes coming while stopped, including arrival confirmation.
            if (provider !in registeredProviders) {
                locations.requestLocationUpdates(provider, 5_000L, 0f, this, Looper.getMainLooper())
                registeredProviders.add(provider)
            }
            fixes.remove(provider)?.cancel()
            val signal = CancellationSignal().also { fixes[provider] = it }
            getCurrentLocation(locations, provider, signal, ContextCompat.getMainExecutor(this)) { fix ->
                if (fix != null && session != null && !signal.isCanceled) onLocationChanged(fix)
            }
        } catch (_: IllegalArgumentException) { /* One unavailable provider must not disable the others. */ }
        catch (_: SecurityException) { if (!hasLocation()) stopSelf() }
    }

    private fun stopLocations() {
        fixes.values.forEach { it.cancel() }; fixes.clear()
        locations.removeUpdates(this)
        registeredProviders.clear()
    }

    override fun onLocationChanged(location: Location) {
        if (!JourneyTracking.enabled(this)) { finish(); return }
        val current = session ?: return
        if (current.awaitingBoarding) return
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        if (nowNanos - location.elapsedRealtimeNanos !in 0..15_000_000_000L ||
            (lastFixNanos != null && location.elapsedRealtimeNanos <= lastFixNanos!!)) return
        val elapsed = lastGoodFixNanos?.let { (location.elapsedRealtimeNanos - it) / 1e9 }
            ?: ((System.currentTimeMillis() - current.progressAt).coerceAtLeast(0) / 1000.0)
        val hasRecentFix = lastGoodFixNanos?.let { nowNanos - it <= 15_000_000_000L } == true
        if (hasRecentFix && location.accuracy > maxOf(30f, lastGoodAccuracy * 2)) return
        val speed = location.speed.takeIf { location.hasSpeed() && (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond <= 3) }
        val spatial = engine?.estimate(TrackPoint(location.latitude, location.longitude),
            if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
            speed,
            current.progress, elapsed, current.warningMinutes, current.alerted)
        if (spatial == null) {
            if (hasRecentFix) return // A lone bad provider/jump must not displace fresh route evidence.
            motion?.reset()
            message = "A helyjel pontatlan, vagy eltértél az útvonaltól. Ellenőrizd az utazást."
            estimate = null
            publish(); return
        }
        val result = motion!!.update(spatial, location.elapsedRealtimeNanos / 1_000_000, speed,
            location.accuracy, current.warningMinutes, current.alerted)
        lastFixNanos = location.elapsedRealtimeNanos
        lastGoodFixNanos = location.elapsedRealtimeNanos
        lastGoodAccuracy = location.accuracy
        estimate = result
        lastRouteEstimate = result
        session = current.copy(progress = result.progress,
            progressAt = System.currentTimeMillis())
        message = when {
            result.arrived -> "Megérkeztél a leszállóhelyhez."
            current.alerted || result.shouldAlert -> "Készülj a leszállásra!"
            else -> "${result.stopsRemaining} megálló a leszállásig"
        }
        if (result.shouldAlert) {
            if (alert(current)) {
                session = session!!.copy(alerted = true)
                JourneyTracking.persist(this, session!!, durable = true)
            } else {
                message = "Nem sikerült elküldeni a jelzést. Ellenőrizd az értesítési engedélyeket."
            }
        }
        if (result.arrived) {
            if (!session!!.alerted) { publish(); return }
            if (current.nextLegIndex == null) { finish(); return }
            session = session!!.copy(awaitingBoarding = true)
            estimate = null
            liveArrival = null
            stopLocations()
            message = "Szállj át, majd erősítsd meg a felszállást."
            JourneyTracking.persist(this, session!!, durable = true)
        }
        if (SystemClock.elapsedRealtime() - lastPersist > 30_000) {
            JourneyTracking.persist(this, session!!); lastPersist = SystemClock.elapsedRealtime()
        }
        publish()
    }

    @Deprecated("Legacy location callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) { requestProvider(provider) }
    override fun onProviderDisabled(provider: String) = Unit

    private fun open(): PendingIntent = PendingIntent.getActivity(this, 701,
        Intent(this, MainActivity::class.java).putExtra("tracking", true).putExtra("trackingSession", session!!.json().toString())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(name: String): PendingIntent = PendingIntent.getService(this, name.hashCode(),
        JourneyTracking.controlIntent(this, name, session!!), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val current = session!!
        val leg = current.leg
        val eta = estimate?.secondsRemaining?.let { "kb. ${maxOf(1, (it + 59) / 60)} perc · helyzet alapján" }
            ?: liveArrival?.takeIf { SystemClock.elapsedRealtime() - it.second < 120_000 }?.let { "Élő érkezés: ${timeLabel(it.first)}" }
            ?: "Menetrendi érkezés: ${timeLabel(leg.arrivalTime.scheduled)}"
        val builder = NotificationCompat.Builder(this, ONGOING).setSmallIcon(R.drawable.ic_notification)
            .addExtras(Bundle().apply { putString("trackingSessionToken", current.controlToken) })
            .setContentTitle(if (current.awaitingBoarding) "Átszállás · ${leg.to.name}" else "${leg.route} · ${leg.to.name}")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(if (current.awaitingBoarding) message else "$message\n$eta"))
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Leállítás", action("stop"))
        if (current.awaitingBoarding) builder.addAction(0, "Felszálltam", action("board"))
        if (!current.awaitingBoarding) {
            val progress = estimate
            val title = "${leg.route} · ${leg.to.name}"
            val stops = progress?.let { "${it.stopsRemaining} megálló" } ?: "Helyzetre vár"
            fun content(layout: Int) = RemoteViews(packageName, layout).apply {
                setTextViewText(R.id.tracking_title, title)
                setTextViewText(R.id.tracking_stops, stops)
                val total = progress?.let { it.progress + it.remainingMeters } ?: 0.0
                val value = if (total > 0) ((progress!!.progress / total).coerceIn(0.0, 1.0) * 1000).toInt() else 0
                setProgressBar(R.id.tracking_progress, 1000, value, progress == null)
                setContentDescription(R.id.tracking_stops,
                    progress?.let { "${it.stopsRemaining} megálló a leszállásig" } ?: "Helymeghatározás folyamatban")
            }
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(content(R.layout.notification_journey))
                .setCustomBigContentView(content(R.layout.notification_journey_expanded).apply {
                    setInt(R.id.tracking_title, "setMaxLines", 2)
                    setTextViewText(R.id.tracking_status, message)
                    setTextViewText(R.id.tracking_eta, eta)
                })
        }
        if (Build.VERSION.SDK_INT >= 36) builder.setRequestPromotedOngoing(true)
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun alert(current: TrackingSession, liveBased: Boolean = false): Boolean {
        if (!JourneyTracking.enabled(this) || !JourneyTracking.notificationsEnabled(this)) return false
        val key = current.controlToken
        if (getSystemService(NotificationManager::class.java).activeNotifications.any {
                it.id == ALERT_ID && it.notification.extras.getString("trackingAlertKey") == key }) return true
        val stop = current.leg.to.name
        return try {
            NotificationManagerCompat.from(this).notify(ALERT_ID,
            NotificationCompat.Builder(this, ALERTS).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Hamarosan leszállás")
                .setContentText(if (liveBased) "Élő érkezés alapján készülj a leszállásra: $stop" else "Készülj a leszállásra: $stop")
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (liveBased) "Élő érkezés alapján készülj a leszállásra: $stop" else "Készülj a leszállásra: $stop"))
                .addExtras(Bundle().apply { putString("trackingAlertKey", key) }).setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open()).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
            true
        } catch (_: SecurityException) { false }
    }

    @SuppressLint("MissingPermission")
    private fun publish() {
        JourneyTracking.publish(TrackingView(session, true, message, estimate))
        NotificationManagerCompat.from(this).notify(ONGOING_ID, notification())
    }

    private fun finish() { JourneyTracking.clear(this); session = null; stopSelf() }
    override fun onDestroy() {
        stopLocations()
        scope.cancel()
        session?.takeIf { JourneyTracking.saved(this) != null }?.let { JourneyTracking.publish(TrackingView(it, false,
            "A követés szünetel. Folytasd, ha még ezen az úton vagy.")) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        internal const val ONGOING = "journey_progress"
        internal const val ALERTS = "journey_alighting"
        internal const val ONGOING_ID = 7001
        internal const val ALERT_ID = 7002
    }
}
