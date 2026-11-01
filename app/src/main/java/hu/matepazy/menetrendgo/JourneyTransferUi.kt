package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/** A quiet timeline annotation. Ordinary and unverifiable connections add no UI. */
@Composable
internal fun JourneyTransferNotice(connection: TransferConnection, times: TransferTimes, now: Long) {
    val title = transferWarning(connection, times, now) ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = AppLayout.CompactGap),
        verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error)
        times.nextDeparture?.takeIf { now - it.fetchedAt in 0..TransferFreshnessMs && it.time.estimate >= now }?.let {
            val day = dayLabel(it.time.estimate).takeIf(String::isNotBlank)?.let { day -> "$day " }.orEmpty()
            Text("Következő: $day${timeLabel(it.time.estimate)} · ${it.route}" + if (it.time.realtime) "" else " (menetrend)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
