package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shared native spacing roles. Map geometry and timeline geometry stay local. */
internal object AppLayout {
    val LabelGap = 4.dp
    val CompactGap = 8.dp
    val RelatedGap = 12.dp
    val ItemGap = 16.dp
    val PageGutter = 20.dp
    val PanelPadding = 20.dp
    val SectionGap = 24.dp
    val SheetPadding = 24.dp
}

@Composable
internal fun screenContentPadding() = PaddingValues(
    start = AppLayout.PageGutter,
    top = AppLayout.PageGutter,
    end = AppLayout.PageGutter,
    bottom = AppLayout.PageGutter + LocalBottomOverlayInset.current
)
