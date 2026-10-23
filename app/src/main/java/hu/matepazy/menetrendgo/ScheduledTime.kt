package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.time.Instant

data class ServiceTime(val scheduled: Long, val estimate: Long, val realtime: Boolean) {
    val changed get() = realtime && scheduled / 60_000 != estimate / 60_000
    val crossesDay get() = realtime && Instant.ofEpochMilli(scheduled).atZone(Budapest).toLocalDate()!=
        Instant.ofEpochMilli(estimate).atZone(Budapest).toLocalDate()
    companion object {
        /** OTP start/end timestamps already contain the delay, expressed separately in seconds. */
        fun fromEstimate(estimate: Long, delaySeconds: Int, realtime: Boolean) =
            ServiceTime(if(realtime) estimate-delaySeconds*1000L else estimate,estimate,realtime)
    }
}

@Composable fun ScheduledTime(time: ServiceTime, modifier: Modifier=Modifier,
    style: TextStyle=MaterialTheme.typography.titleMedium, alignment: Alignment.Horizontal=Alignment.Start) {
    val textAlign=if(alignment==Alignment.End) TextAlign.End else TextAlign.Start
    val scheduledStyle=if(time.changed) style.copy(
        fontSize=(style.fontSize.value*.75f).coerceAtLeast(12f).sp,
        lineHeight=(style.lineHeight.value*.75f).coerceAtLeast(16f).sp
    ) else style
    Column(modifier,horizontalAlignment=alignment) {
        Text(timeLabel(time.scheduled),style=scheduledStyle,fontWeight=FontWeight.Bold,textAlign=textAlign,
            color=if(time.changed) MaterialTheme.colorScheme.error else LocalContentColor.current,
            textDecoration=if(time.changed) TextDecoration.LineThrough else TextDecoration.None)
        if(time.crossesDay) Text(dayLabel(time.scheduled).ifBlank {"Ma"},style=MaterialTheme.typography.labelSmall,textAlign=textAlign)
        if(time.changed) Text(timeLabel(time.estimate),style=style,fontWeight=FontWeight.Bold,textAlign=textAlign)
        if(time.crossesDay) Text(dayLabel(time.estimate).ifBlank {"Ma"},style=MaterialTheme.typography.labelSmall,textAlign=textAlign)
    }
}
