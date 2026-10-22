package hu.matepazy.menetrendgo

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

object PassNotifications {
    private const val CHANNEL = "pass_expiry"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Bérlet lejárata", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun pending(context: Context, pass: Pass, days: Int): PendingIntent {
        val intent = Intent(context, PassAlarmReceiver::class.java)
            .setData(android.net.Uri.parse("menetrendgo://expiry/${pass.id}/$days"))
            .putExtra("passId", pass.id).putExtra("days", days)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun schedule(context: Context, pass: Pass) {
        channel(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        for (days in listOf(3, 1, 0)) {
            val date = if (days == 0) pass.expires.plusDays(1) else pass.expires.minusDays(days.toLong())
            val whenMillis = date.atTime(9, 0).atZone(Budapest).toInstant().toEpochMilli()
            val operation = pending(context, pass, days)
            manager.cancel(operation)
            if (whenMillis > System.currentTimeMillis()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, operation)
        }
    }
    fun cancel(context: Context, pass: Pass) {
        val manager = context.getSystemService(AlarmManager::class.java)
        listOf(3, 1, 0).forEach { manager.cancel(pending(context, pass, it)) }
    }
    fun saved(context: Context): List<Pass> = runCatching {
        JSONArray(context.getSharedPreferences("menetrendgo", 0).getString("passes", "[]")).objects().map(Pass::parse)
    }.getOrDefault(emptyList())
    fun notify(context: Context, id: String, days: Int) {
        val pass = saved(context).find { it.id == id } ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channel(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("tab", "passes"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (days == 0) "Lejárt a bérleted" else "$days nap múlva lejár a bérleted"
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText("${pass.name} · ${pass.expires}")
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify((id + days).hashCode(), notification)
    }
}
class PassAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PassNotifications.notify(context, intent.getStringExtra("passId") ?: return, intent.getIntExtra("days", 0))
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) PassNotifications.saved(context).forEach { PassNotifications.schedule(context, it) }
    }
}
