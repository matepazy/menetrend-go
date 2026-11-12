package hu.matepazy.menetrendgo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared stop presentation for the complete timetable and live vehicle drawer. */
@Composable internal fun TimetableStopRow(
    name: String,
    time: ServiceTime?,
    platform: String,
    mode: String,
    track: TimelineTrack,
    first: Boolean,
    last: Boolean,
    prominent: Boolean = first || last,
    next: Boolean = false,
    scheduledLabel: String = "Menetrendi érkezés",
) {
    TimelineRow(time, track, stop=true, intermediate=!prominent,
        incoming=if(first) null else track,
        outgoing=if(last) null else track,
        bottomPadding=if(last) 0.dp else if(prominent) AppLayout.ItemGap else AppLayout.RelatedGap) {
        if(next) Text("Következő megálló", style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.primary)
        Text(name, style=MaterialTheme.typography.titleMedium,
            fontWeight=if(prominent) FontWeight.Bold else FontWeight.Normal)
        if(platform.isNotBlank() && platform!="null") Text(vehicleStopPlatform(platform, mode),
            style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(time!=null && !time.realtime) Text(scheduledLabel,
            style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
