package com.useconatus.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Long-press and drag to reorder rows of a LazyColumn. While a row is held, [onMove] moves it to
 * the row under its centre (by key), so the rest of the list makes room; [onDrop] commits.
 * No autoscroll at the edges yet; add it if long lists need dragging past the screen.
 */
class DragState(private val list: LazyListState) {
    var dragging by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    internal var canDrop: (Any) -> Boolean = { true }
    internal var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    internal var onDrop: (Any) -> Unit = {}

    fun start(key: Any) {
        dragging = key
        offset = 0f
    }

    fun drag(dy: Float) {
        val key = dragging ?: return
        offset += dy
        val items = list.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val centre = (current.offset + offset + current.size / 2f).toInt()
        val target = items.firstOrNull { it.key != key && canDrop(it.key) && centre in it.offset until it.offset + it.size } ?: return
        onMove(key, target.key)
        // The row now sits where the target was: keep it under the finger.
        offset += current.offset - target.offset
    }

    fun end() {
        dragging?.let(onDrop)
        dragging = null
        offset = 0f
    }
}

@Composable
fun rememberDragState(
    list: LazyListState,
    canDrop: (Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
    onDrop: (Any) -> Unit,
): DragState {
    val state = remember(list) { DragState(list) }
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val can by rememberUpdatedState(canDrop)
    state.canDrop = { can(it) }
    state.onMove = { from, to -> move(from, to) }
    state.onDrop = { drop(it) }
    return state
}

/**
 * Makes a row draggable by long press, and lifts it above the others while it moves. A long press
 * let go without moving is [onHold] instead.
 */
@Composable
fun Modifier.draggableRow(state: DragState, key: Any, enabled: Boolean = true, onHold: () -> Unit = {}): Modifier {
    if (!enabled) return this
    val haptics = LocalHapticFeedback.current
    val hold by rememberUpdatedState(onHold)
    val lifted = state.dragging == key
    return this
        .then(if (lifted) Modifier.zIndex(1f).graphicsLayer { translationY = state.offset; shadowElevation = 8f } else Modifier)
        .pointerInput(state, key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(key)
                var travelled = 0f
                try {
                    // The gesture is the drag's from here: each event is taken before the row's own tap or
                    // swipe sees it, so letting go doesn't also open the task.
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pressed.id } ?: break
                        val moved = change.positionChange()
                        change.consume()
                        if (!change.pressed) break
                        travelled += moved.getDistance()
                        state.drag(moved.y)
                    }
                } finally {
                    state.end()
                }
                if (travelled < viewConfiguration.touchSlop) hold()
            }
        }
}
