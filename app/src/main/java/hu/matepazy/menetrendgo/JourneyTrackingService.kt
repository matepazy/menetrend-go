package hu.matepazy.menetrendgo

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
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

internal data class TrackingView(
    val session: TrackingSession? = null,
    val running: Boolean = false,
    val message: String = "Helymeghatározás…",
    val estimate: TrackingEstimate? = null,
)

internal object JourneyTracking {
    private val mutable = MutableStateFlow(TrackingView())
    val state = mutable.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("journey_tracking", Context.MODE_PRIVATE)
    fun saved(context: Context): TrackingSession? = runCatching {
        prefs(context).getString("session", null)?.let { TrackingSession.parse(JSONObject(it)) }
    }.getOrNull()?.takeIf { System.currentTimeMillis() - it.startedAt in 0..18 * 60 * 60_000L }
    fun load(context: Context) {
        if (mutable.value.session == null) saved(context)?.let {
            mutable.value = TrackingView(it, message = "A követés szünetel. Folytasd, ha még ezen az úton vagy.")
        }
    }
    fun publish(view: TrackingView) { mutable.value = view }
    fun persist(context: Context, session: TrackingSession) {
        prefs(context).edit().putString("session", session.json().toString()).apply()
    }
    fun clear(context: Context) {
        prefs(context).edit().remove("session").apply()
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
        persist(context, session)
        mutable.value = TrackingView(session, message = "Követés indítása…")
        ContextCompat.startForegroundService(context, Intent(context, JourneyTrackingService::class.java).setAction("start"))
    }
    fun stop(context: Context) {
        clear(context)
        context.stopService(Intent(context, JourneyTrackingService::class.java))
        NotificationManagerCompat.from(context).cancel(JourneyTrackingService.ALERT_ID)
    }
}

/** User-started navigation, independent of Activity/ViewModel lifecycle. No background-location grant. */
class JourneyTrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = MavClient()
    private lateinit var locations: LocationManager
    private var session: TrackingSession? = null
    private var engine: JourneyProgress? = null
    private var lastFixNanos: Long? = null
    private var lastGoodFixNanos: Long? = null
    private var lastPersist = 0L
    private var liveArrival: Pair<Long, Long>? = null
    private var estimate: TrackingEstimate? = null
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
        })
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            JourneyTracking.clear(this)
            NotificationManagerCompat.from(this).cancel(ALERT_ID)
            stopSelf(); return START_NOT_STICKY
        }
        val restored = if (intent?.action == "start" || session == null) JourneyTracking.saved(this) else session
        if (restored == null || !hasLocation() || !JourneyTracking.notificationsEnabled(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        session = restored
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, notification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "board" && restored.awaitingBoarding) {
            val next = restored.nextLegIndex
            if (next == null) { finish(); return START_NOT_STICKY }
            session = restored.copy(legIndex = next, awaitingBoarding = false, alerted = false, progress = null)
            NotificationManagerCompat.from(this).cancel(ALERT_ID)
        }
        engine = JourneyProgress(session!!.leg)
        estimate = null; lastFixNanos = null; lastGoodFixNanos = null; liveArrival = null
        message = if (session!!.awaitingBoarding) "Szállj át, majd erősítsd meg a felszállást." else "Helymeghatározás…"
        JourneyTracking.persist(this, session!!)
        locations.removeUpdates(this)
        try {
            if (!session!!.awaitingBoarding) locations.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }.forEach {
                locations.requestLocationUpdates(it, 5_000L, 10f, this, Looper.getMainLooper())
            }
        } catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        timer?.cancel()
        timer = scope.launch {
            while (isActive) {
                val current = session ?: break
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
                        val arrival = trackingArrival(client.trip(leg.tripId, day), leg)
                        if (session?.legIndex == current.legIndex && session?.awaitingBoarding == false) {
                            liveArrival = arrival?.let { it to SystemClock.elapsedRealtime() }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { liveArrival = null }
                }
                delay(75_000)
            }
        }
        publish()
        return START_STICKY
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onLocationChanged(location: Location) {
        val current = session ?: return
        if (current.awaitingBoarding) return
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        if (nowNanos - location.elapsedRealtimeNanos !in 0..30_000_000_000L ||
            (lastFixNanos != null && location.elapsedRealtimeNanos <= lastFixNanos!!)) return
        val elapsed = lastGoodFixNanos?.let { (location.elapsedRealtimeNanos - it) / 1e9 }
            ?: ((System.currentTimeMillis() - current.progressAt).coerceAtLeast(0) / 1000.0)
        lastFixNanos = location.elapsedRealtimeNanos
        val result = engine?.estimate(TrackPoint(location.latitude, location.longitude),
            if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
            location.speed.takeIf { location.hasSpeed() && (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond <= 3) },
            current.progress, elapsed, current.warningMinutes, current.alerted)
        if (result == null) {
            message = "A helyjel pontatlan, vagy eltértél az útvonaltól. Ellenőrizd az utazást."
            estimate = null
            publish(); return
        }
        lastGoodFixNanos = location.elapsedRealtimeNanos
        estimate = result
        session = current.copy(progress = result.progress, alerted = current.alerted || result.shouldAlert,
            progressAt = System.currentTimeMillis())
        message = when {
            result.arrived -> "Megérkeztél a leszállóhelyhez."
            session!!.alerted -> "Készülj a leszállásra!"
            else -> "${result.stopsRemaining} megálló a leszállásig"
        }
        if (result.shouldAlert) {
            JourneyTracking.persist(this, session!!)
            alert(current.leg.to.name)
        }
        if (result.arrived) {
            if (current.nextLegIndex == null) { finish(); return }
            session = session!!.copy(awaitingBoarding = true)
            estimate = null
            liveArrival = null
            locations.removeUpdates(this)
            message = "Szállj át, majd erősítsd meg a felszállást."
            JourneyTracking.persist(this, session!!)
        }
        if (SystemClock.elapsedRealtime() - lastPersist > 30_000) {
            JourneyTracking.persist(this, session!!); lastPersist = SystemClock.elapsedRealtime()
        }
        publish()
    }

    @Deprecated("Legacy location callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun open(): PendingIntent = PendingIntent.getActivity(this, 701,
        Intent(this, MainActivity::class.java).putExtra("tracking", true).putExtra("trackingSession", session!!.json().toString())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(name: String): PendingIntent = PendingIntent.getService(this, name.hashCode(),
        Intent(this, JourneyTrackingService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val current = session!!
        val leg = current.leg
        val eta = estimate?.secondsRemaining?.let { "kb. ${maxOf(1, (it + 59) / 60)} perc · helyzet alapján" }
            ?: liveArrival?.takeIf { SystemClock.elapsedRealtime() - it.second < 120_000 }?.let { "Élő érkezés: ${timeLabel(it.first)}" }
            ?: "Menetrendi érkezés: ${timeLabel(leg.arrivalTime.scheduled)}"
        val builder = NotificationCompat.Builder(this, ONGOING).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (current.awaitingBoarding) "Átszállás · ${leg.to.name}" else "${leg.route} · ${leg.to.name}")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(if (current.awaitingBoarding) message else "$message\n$eta"))
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Leállítás", action("stop"))
        if (current.awaitingBoarding) builder.addAction(0, "Felszálltam", action("board"))
        if (Build.VERSION.SDK_INT >= 36) builder.setRequestPromotedOngoing(true)
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun alert(stop: String) {
        NotificationManagerCompat.from(this).notify(ALERT_ID,
            NotificationCompat.Builder(this, ALERTS).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Hamarosan leszállás")
                .setContentText("Készülj a leszállásra: $stop")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Készülj a leszállásra: $stop"))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open()).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }

    @SuppressLint("MissingPermission")
    private fun publish() {
        JourneyTracking.publish(TrackingView(session, true, message, estimate))
        NotificationManagerCompat.from(this).notify(ONGOING_ID, notification())
    }

    private fun finish() { JourneyTracking.clear(this); session = null; stopSelf() }
    override fun onDestroy() {
        locations.removeUpdates(this)
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
