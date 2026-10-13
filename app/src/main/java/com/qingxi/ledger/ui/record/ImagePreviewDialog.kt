package com.qingxi.ledger.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun ImagePreviewDialog(
    paths: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    if (paths.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, paths.lastIndex)) { paths.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 点击空白处关闭；左右滑动仍可翻页（子级只吃 tap，drag 交给 pager）
                        .clickable(onClickLabel = "关闭预览", onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(paths[page]),
                        contentDescription = "票据图片预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .clickable(onClickLabel = "关闭预览", onClick = onDismiss)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "×", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }

            if (paths.size > 1) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${paths.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
