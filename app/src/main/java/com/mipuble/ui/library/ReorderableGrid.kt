package com.mipuble.ui.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Hand-rolled drag-and-drop reordering for a LazyVerticalGrid (no library).
 *
 * Mechanics: a long-press on an item starts the drag; as the pointer moves we
 * hit-test its accumulated position against the grid's visible items
 * (via [LazyGridState.layoutInfo]) and emit [onMove] whenever it crosses into
 * another item's slot. The dragged item itself is rendered with a
 * graphicsLayer translation + elevated zIndex, so no recomposition storm —
 * just one translated cell. [onDragEnd] fires once with the drop, which is
 * when the new order is persisted.
 */
class GridDragState(
    private val gridState: LazyGridState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    fun startDrag(pointer: Offset) {
        draggingIndex = itemAt(pointer)?.index
        dragOffset = Offset.Zero
    }

    fun drag(delta: Offset) {
        val from = draggingIndex ?: return
        dragOffset += delta

        val current = visibleItem(from) ?: return
        val center = Offset(
            current.offset.x + dragOffset.x + current.size.width / 2f,
            current.offset.y + dragOffset.y + current.size.height / 2f,
        )
        val target = itemAt(center) ?: return
        if (target.index == from) return

        onMove(from, target.index)
        // The item now occupies the target slot; re-anchor the visual offset so
        // the cell doesn't jump under the finger.
        dragOffset += Offset(
            (current.offset.x - target.offset.x).toFloat(),
            (current.offset.y - target.offset.y).toFloat(),
        )
        draggingIndex = target.index
    }

    fun endDrag() {
        if (draggingIndex != null) onDragEnd()
        draggingIndex = null
        dragOffset = Offset.Zero
    }

    fun cancelDrag() {
        draggingIndex = null
        dragOffset = Offset.Zero
    }

    private fun visibleItem(index: Int): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    private fun itemAt(position: Offset): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            position.x >= item.offset.x &&
                position.x <= item.offset.x + item.size.width &&
                position.y >= item.offset.y &&
                position.y <= item.offset.y + item.size.height
        }
}

@Composable
fun rememberGridDragState(
    gridState: LazyGridState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
): GridDragState {
    val moveState = rememberUpdatedState(onMove)
    val endState = rememberUpdatedState(onDragEnd)
    return remember(gridState) {
        GridDragState(
            gridState = gridState,
            onMove = { from, to -> moveState.value(from, to) },
            onDragEnd = { endState.value() },
        )
    }
}

/** Attach to the grid itself to capture long-press drags. */
fun Modifier.reorderableGrid(dragState: GridDragState): Modifier =
    pointerInput(dragState) {
        detectDragGesturesAfterLongPress(
            onDragStart = dragState::startDrag,
            onDrag = { change, delta ->
                change.consume()
                dragState.drag(delta)
            },
            onDragEnd = dragState::endDrag,
            onDragCancel = dragState::cancelDrag,
        )
    }

/** Attach to each item; lifts and translates the one being dragged. */
fun Modifier.reorderableItem(dragState: GridDragState, index: Int): Modifier =
    if (dragState.draggingIndex == index) {
        this
            .zIndex(1f)
            .graphicsLayer {
                translationX = dragState.dragOffset.x
                translationY = dragState.dragOffset.y
            }
    } else {
        this
    }
