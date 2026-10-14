package com.qingxi.ledger.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qingxi.ledger.ui.theme.ExpenseRed
import kotlinx.coroutines.launch

/** 左滑露出的按钮宽度。 */
private val REVEAL_WIDTH = 84.dp

/**
 * 左滑露出「删除」的一行：滑过一半就停住；露出状态下点行内只收回，
 * 要删必须点露出来的按钮，避免误触。
 *
 * 行内容由调用方画：拿到 `rowModifier`（平移）和 `onRowClick`（已含收回语义）直接接进行上。
 *
 * @param resetKey 行标识；变化时收回，列表复用同一槽位不会残留滑开状态。
 * @param swipeEnabled 多选等场景禁用滑动，避免和勾选打架。
 */
@Composable
fun SwipeableDeleteRow(
    resetKey: Any,
    swipeEnabled: Boolean,
    deleteLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (rowModifier: Modifier, onRowClick: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val revealPx = with(density) { REVEAL_WIDTH.toPx() }
    val scope = rememberCoroutineScope()
    val shift = remember { Animatable(0f) }
    var revealed by remember { mutableStateOf(false) }
    var dragAmount by remember { mutableFloatStateOf(0f) }

    fun settle() {
        val open = dragAmount <= -revealPx * 0.55f
        dragAmount = if (open) -revealPx else 0f
        revealed = open
        scope.launch { shift.animateTo(dragAmount) }
    }

    fun close() {
        revealed = false
        dragAmount = 0f
        scope.launch { shift.animateTo(0f) }
    }

    LaunchedEffect(resetKey, swipeEnabled) {
        shift.snapTo(0f)
        dragAmount = 0f
        revealed = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clipToBounds()
            .pointerInput(swipeEnabled) {
                if (!swipeEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = shift.value },
                    onHorizontalDrag = { _, delta ->
                        dragAmount = (dragAmount + delta).coerceIn(-revealPx, 0f)
                        scope.launch { shift.snapTo(dragAmount) }
                    },
                    onDragEnd = { settle() },
                    onDragCancel = { settle() }
                )
            }
    ) {
        // 收回状态下不画面板：它的圆角和行内容差一两个像素，一直画就会在右上角透出一线红。
        // 用 graphicsLayer 的 alpha 而不是 if 包一层，滑动动画每帧只重绘不重组。
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ExpenseRed,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(REVEAL_WIDTH)
                .graphicsLayer { alpha = if (shift.value < 0f) 1f else 0f }
                .clickable(enabled = revealed, onClickLabel = deleteLabel, onClick = onDelete)
        ) {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = deleteLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        content(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shift.value }
        ) {
            if (revealed) close() else onClick()
        }
    }
}
