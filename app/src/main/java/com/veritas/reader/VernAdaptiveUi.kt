package com.veritas.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun Context.vernActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.vernActivity()
    else -> null
}

internal fun vernFolds(activity: Activity) = WindowInfoTracker.getOrCreate(activity)
    .windowLayoutInfo(activity).map { info ->
        info.displayFeatures.filterIsInstance<FoldingFeature>()
            .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
            .map { fold -> fold.bounds.let { VernFoldBounds(VernPaneBounds(it.left, it.top, it.right, it.bottom),
                fold.orientation == FoldingFeature.Orientation.VERTICAL) } }
    }.catch { emit(emptyList()) }

// Also permits deterministic component coverage without pretending a phone has a physical hinge.
internal val LocalVernFoldOverride = staticCompositionLocalOf<List<VernFoldBounds>?> { null }

@Composable
internal fun rememberVernFolds(): List<VernFoldBounds> {
    LocalVernFoldOverride.current?.let { return it }
    val activity = LocalContext.current.vernActivity() ?: return emptyList()
    val flow = remember(activity) { vernFolds(activity) }
    val folds by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return folds
}

/** Full-window screens stay inside a single unobscured pane in book/tabletop posture. */
@Composable
internal fun VernFoldAwarePane(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val folds = rememberVernFolds()
    val activity = LocalContext.current.vernActivity()
    val view = LocalView.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    Layout(modifier = modifier.onGloballyPositioned {
        val screen = IntArray(2); val window = IntArray(2); val owner = IntArray(2)
        view.getLocationOnScreen(screen); view.getLocationInWindow(window)
        activity?.window?.decorView?.getLocationOnScreen(owner)
        val local = it.positionInWindow()
        origin = IntOffset((local.x + screen[0] - window[0] - owner[0]).roundToInt(),
            (local.y + screen[1] - window[1] - owner[1]).roundToInt())
    }, content = { Box(Modifier.clipToBounds(), content = content) }) { items, constraints ->
        if (folds.isEmpty() || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val child = items.single().measure(constraints)
            layout(child.width, child.height) { child.place(0, 0) }
        } else {
            val viewport = VernPaneBounds(origin.x, origin.y, origin.x + constraints.maxWidth, origin.y + constraints.maxHeight)
            val pane = vernUsablePane(viewport, folds, rtl)
            val child = items.single().measure(Constraints.fixed(pane.width, pane.height))
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(pane.left - origin.x, pane.top - origin.y) }
        }
    }
}

/** Floating dialogs use their actual Window, rather than assuming the activity's center is usable. */
internal fun Modifier.vernDialogPane(): Modifier = composed {
    val folds = rememberVernFolds()
    val view = LocalView.current
    val activity = LocalContext.current.vernActivity()
    val window = (view.parent as? DialogWindowProvider)?.window
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    DisposableEffect(window, folds) {
        val original = if (window != null && folds.isNotEmpty()) WindowManager.LayoutParams().apply { copyFrom(window.attributes) } else null
        onDispose { if (original != null) window?.attributes = original }
    }
    onGloballyPositioned {
        if (window != null && activity != null && folds.isNotEmpty()) {
            val decor = activity.window.decorView
            val location = IntArray(2); decor.getLocationOnScreen(location)
            val safe = ViewCompat.getRootWindowInsets(decor)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            var viewport = VernPaneBounds(safe?.left ?: 0, safe?.top ?: 0,
                decor.width - (safe?.right ?: 0), decor.height - (safe?.bottom ?: 0))
            val visible = Rect(); view.getWindowVisibleDisplayFrame(visible)
            if (!visible.isEmpty) viewport = VernPaneBounds(maxOf(viewport.left, visible.left - location[0]),
                maxOf(viewport.top, visible.top - location[1]), minOf(viewport.right, visible.right - location[0]),
                minOf(viewport.bottom, visible.bottom - location[1]))
            val pane = vernUsablePane(viewport, folds, rtl)
            val dialog = window.decorView
            if (pane.area > 0 && dialog.width > 0 && dialog.height > 0) {
                val position = IntArray(2); dialog.getLocationOnScreen(position)
                val dx = location[0] + (pane.left + pane.right) / 2 - position[0] - dialog.width / 2
                val dy = location[1] + (pane.top + pane.bottom) / 2 - position[1] - dialog.height / 2
                val attrs = window.attributes
                val gravity = Gravity.getAbsoluteGravity(attrs.gravity, view.layoutDirection)
                val width = minOf(dialog.width, pane.width); val height = minOf(dialog.height, pane.height)
                if (abs(dx) > 1 || abs(dy) > 1 || width < dialog.width || height < dialog.height) {
                    attrs.x += if ((gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) -dx else dx
                    attrs.y += if ((gravity and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) -dy else dy
                    if (width < dialog.width) attrs.width = width
                    if (height < dialog.height) attrs.height = height
                    window.attributes = attrs
                }
            }
        }
    }
}

@Composable
internal fun VernDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest, properties) {
        if (properties.usePlatformDefaultWidth) {
            Box(Modifier.vernDialogPane()) { content() }
        } else {
            VernFoldAwarePane(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
internal fun VernAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = androidx.compose.material3.AlertDialog(onDismissRequest, confirmButton, modifier.vernDialogPane(), dismissButton,
    icon, title, text, shape, containerColor, iconContentColor, titleContentColor, textContentColor, tonalElevation, properties)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VernAlertDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) =
    BasicAlertDialog(onDismissRequest, modifier.vernDialogPane(), properties, content)
