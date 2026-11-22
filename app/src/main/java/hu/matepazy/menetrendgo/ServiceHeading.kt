package hu.matepazy.menetrendgo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun serviceColorValue(leg: Leg): Int? = serviceColorHex(leg).removePrefix("#")
    .takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }

private val Leg.routeDescription: String
    get() {
        val categoryName = hungarianServiceName(category, mode)
        return if (walking || displayRoute.contains(categoryName, ignoreCase = true)) displayRoute
        else "$categoryName $displayRoute"
    }

@Composable
private fun LiveRouteBadge(leg: Leg) {
    val logo = leg.displayRouteLogo
    val background = serviceColor(leg)
    val labelColor = when {
        logo != null -> MaterialTheme.colorScheme.onSurface
        background.luminance() > .179f -> Color.Black
        else -> Color.White
    }
    Row(
        Modifier.clearAndSetSemantics { contentDescription = leg.routeDescription },
        horizontalArrangement = Arrangement.spacedBy(AppLayout.LabelGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (logo != null) RouteLogoIcon(logo)
        Text(
            text = leg.displayRoute,
            modifier = if (logo != null) Modifier else Modifier
                .background(background, RoundedCornerShape(dimensionResource(R.dimen.service_badge_radius)))
                .padding(horizontal = dimensionResource(R.dimen.service_badge_horizontal_padding),
                    vertical = dimensionResource(R.dimen.service_badge_vertical_padding)),
            color = labelColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ServiceHeading(
    leg: Leg,
    destination: String,
    modifier: Modifier = Modifier,
    destinationStyle: TextStyle = MaterialTheme.typography.titleSmall
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap),
        verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap, Alignment.CenterVertically),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        LiveRouteBadge(leg)
        if (destination.isNotBlank()) {
            Row(
                Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(AppLayout.LabelGap),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.height(with(LocalDensity.current) { destinationStyle.lineHeight.toDp() }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_service_direction), "Irány",
                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(destination, Modifier.weight(1f, fill = false), style = destinationStyle)
            }
        }
    }
}
