package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    val backEasing = remember { CubicBezierEasing(.1f, .1f, 0f, 1f) }
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val animateDismiss: () -> Unit = { scope.launch { state.hide() }; Unit }
    LaunchedEffect(state) {
        state.partialExpand()
        snapshotFlow { state.currentValue }.collect { if (it == SheetValue.Hidden) dismiss() }
    }
    Dialog(onDismissRequest = animateDismiss,
        properties = DialogProperties(dismissOnBackPress = false,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // This handler belongs to the dialog, so Back dismisses only the top sheet.
        // Keep its predictive scale until hide() completes, just like ModalBottomSheet.
        PredictiveBackHandler {
            try {
                it.collect { event ->
                    backProgress.snapTo(if (motionEnabled) backEasing.transform(event.progress) else 0f)
                }
                state.hide()
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { backProgress.animateTo(0f) }
                throw cancelled
            }
        }
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
        val scrimAlpha by animateFloatAsState(if (state.targetValue == SheetValue.Hidden) 0f else 1f,
            label = "Expandable sheet scrim")
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrimAlpha }
                .background(BottomSheetDefaults.ScrimColor).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "Bezárás", onClick = animateDismiss,
            ))
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ).padding(top = AppLayout.RelatedGap)) {
                val bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
                // The old cap applied to content below the 48dp Material drag handle,
                // with the bottom safe area added outside that content.
                val fixedHeight = 48.dp + bottomInset
                val sheetWidth = minOf(maxWidth, BottomSheetDefaults.SheetMaxWidth)
                // Use the measured sheet dimensions throughout dismissal. The visible
                // height approaches zero during hide(), but the sheet itself does not.
                val horizontalBackDistance = (48.dp / sheetWidth).coerceAtMost(1f)
                val verticalBackDistance = (24.dp / maxHeight).coerceAtMost(1f)
                val preserveContentProportions = Modifier.graphicsLayer {
                    val progress = backProgress.value
                    val parentScaleX = 1f - horizontalBackDistance * progress
                    val parentScaleY = 1f - verticalBackDistance * progress
                    // Material scales the surface independently on each axis, then
                    // compensates its children so text and icons shrink uniformly.
                    scaleY = if (parentScaleY > 0f) parentScaleX / parentScaleY else 1f
                    transformOrigin = TransformOrigin(.5f, 0f)
                }
                BottomSheetScaffold(
                    modifier = Modifier.graphicsLayer {
                        val progress = backProgress.value
                        scaleX = 1f - horizontalBackDistance * progress
                        scaleY = 1f - verticalBackDistance * progress
                        transformOrigin = TransformOrigin(.5f, 1f)
                    },
                    scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = state),
                    sheetPeekHeight = (maxHeight - fixedHeight) * initialHeightFraction + fixedHeight,
                    sheetShape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
                    sheetDragHandle = {
                        Box(preserveContentProportions) { BottomSheetDefaults.DragHandle() }
                    },
                    containerColor = Color.Transparent,
                    sheetContent = {
                        Column(Modifier.fillMaxWidth().fillMaxHeight().then(preserveContentProportions).windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        ), content = content)
                    },
                ) {}
            }
        }
    }
}
