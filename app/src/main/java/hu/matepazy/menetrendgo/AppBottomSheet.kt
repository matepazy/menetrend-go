package hu.matepazy.menetrendgo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Keep the top safe area and breathing room outside the moving sheet's measurement.
 * Material 3 consumes top content insets according to the current drag offset. On tall sheets
 * that changes the measured height and expanded anchor during a drag/settle, causing bouncing.
 * A fixed outer inset keeps that anchor stable; bottom/IME handling and native gestures remain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled: Boolean = true,
    shape: CornerBasedShape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        ).padding(top = AppLayout.RelatedGap).then(modifier),
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        // Sheets meet the bottom edge, including behind the navigation bar. Only
        // the top corners should round, even when a caller supplies a card shape.
        shape = shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        containerColor = containerColor,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        content = content,
    )
}

/** A modal sheet with a configurable resting height and native full-height expansion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppExpandableBottomSheet(
    onDismissRequest: () -> Unit,
    initialHeightFraction: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val dismiss by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(state) {
        state.partialExpand()
        snapshotFlow { state.currentValue }.collect { if (it == SheetValue.Hidden) dismiss() }
    }
    Dialog(onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect {
            window?.let {
                // The Compose scrim supplies dimming, including behind the system bars.
                it.setDimAmount(0f)
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "Bezárás", onClick = onDismissRequest,
            ))
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ).padding(top = AppLayout.RelatedGap)) {
                val bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
                // The old cap applied to content below the 48dp Material drag handle,
                // with the bottom safe area added outside that content.
                val fixedHeight = 48.dp + bottomInset
                BottomSheetScaffold(
                    scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = state),
                    sheetPeekHeight = (maxHeight - fixedHeight) * initialHeightFraction + fixedHeight,
                    sheetShape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
                    containerColor = Color.Transparent,
                    sheetContent = {
                        Column(Modifier.fillMaxWidth().fillMaxHeight().windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        ), content = content)
                    },
                ) {}
            }
        }
    }
}
