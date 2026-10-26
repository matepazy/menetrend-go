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

enum class PassReminder(val days: Int, val preferenceKey: String, val title: String, val summary: String) {
    ThreeDays(3, "pass_reminder_three_days", "3 nappal a lejárat előtt", "Hogy legyen időd megvenni a következő bérleted."),
    OneDay(1, "pass_reminder_one_day", "1 nappal a lejárat előtt", "Emlékeztető a lejárat előtti napon."),
    Expired(0, "pass_reminder_expired", "Lejárat után", "Értesítés a lejáratot követő napon.");

    fun date(pass: Pass) = if (days == 0) pass.expires.plusDays(1) else pass.expires.minusDays(days.toLong())
}

object PassNotifications {
    private const val CHANNEL = "pass_expiry"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Bérlet lejárata", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun enabled(context: Context, reminder: PassReminder): Boolean =
        context.getSharedPreferences("menetrendgo", Context.MODE_PRIVATE).getBoolean(reminder.preferenceKey, true)

    fun enabledReminders(context: Context): Set<PassReminder> = PassReminder.entries.filter { enabled(context, it) }.toSet()

    fun setEnabled(context: Context, reminder: PassReminder, enabled: Boolean) {
        context.getSharedPreferences("menetrendgo", Context.MODE_PRIVATE).edit()
            .putBoolean(reminder.preferenceKey, enabled).apply()
        saved(context).forEach { pass ->
            schedule(context, pass)
            if (!enabled) NotificationManagerCompat.from(context).cancel((pass.id + reminder.days).hashCode())
        }
    }

    fun systemEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun pending(context: Context, pass: Pass, days: Int): PendingIntent {
        val intent = Intent(context, PassAlarmReceiver::class.java)
            .setData(android.net.Uri.parse("menetrendgo://expiry/${pass.id}/$days"))
            .putExtra("passId", pass.id).putExtra("days", days)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun schedule(context: Context, pass: Pass) {
        channel(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        for (reminder in PassReminder.entries) {
            val whenMillis = reminder.date(pass).atTime(9, 0).atZone(Budapest).toInstant().toEpochMilli()
            val operation = pending(context, pass, reminder.days)
            manager.cancel(operation)
            if (enabled(context, reminder) && whenMillis > System.currentTimeMillis()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, operation)
        }
    }
    fun cancel(context: Context, pass: Pass) {
        val manager = context.getSystemService(AlarmManager::class.java)
        PassReminder.entries.forEach {
            manager.cancel(pending(context, pass, it.days))
            NotificationManagerCompat.from(context).cancel((pass.id + it.days).hashCode())
        }
    }
    fun saved(context: Context): List<Pass> = runCatching {
        JSONArray(context.getSharedPreferences("menetrendgo", 0).getString("passes", "[]")).objects().map(Pass::parse)
    }.getOrDefault(emptyList())
    fun notify(context: Context, id: String, days: Int) {
        val reminder = PassReminder.entries.find { it.days == days } ?: return
        if (!enabled(context, reminder) || !systemEnabled(context)) return
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
