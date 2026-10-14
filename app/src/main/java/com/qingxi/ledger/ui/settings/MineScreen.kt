package com.qingxi.ledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.settings.AppSettings
import com.qingxi.ledger.data.settings.SettingsRepository
import com.qingxi.ledger.data.settings.ThemeMode
import com.qingxi.ledger.data.settings.ThemePalette
import com.qingxi.ledger.data.update.UpdateStatus
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Money

/** 「我的」首页：账户概览 + 主题设置 + 各功能入口。 */
@Composable
fun MineScreen(onNavigate: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val viewModel = appViewModel<SettingsViewModel>()
    val state by viewModel.mineState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val backups = rememberBackupRepository()
    val lockManager = rememberLockManager()

    val lockState by lockManager.state.collectAsStateWithLifecycle(initialValue = null)
    val lastBackupLabel by produceState(initialValue = "正在读取…", graph) {
        val newest = runCatching { backups.listBackups().maxByOrNull { it.millis } }.getOrNull()
        value = if (newest == null) "尚无备份" else "最近备份 ${formatShortDateTime(newest.millis)}"
    }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }
    // 冷启动自动检查的结果：有新版本就在「关于」入口上标出来。
    val updateStatus by graph.updateController.status.collectAsStateWithLifecycle()
    val updateBadge = (updateStatus as? UpdateStatus.Available)?.info?.versionName

    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
                // 底部留出底栏高度，避免最后一项被遮住
                .padding(top = 6.dp, bottom = 80.dp)
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            AccountCard(state = state)

            Spacer(Modifier.height(12.dp))

            ThemeCard(
                themeMode = state.themeMode,
                palette = state.palette,
                onModeChange = viewModel::setThemeMode,
                onPaletteChange = viewModel::setPalette
            )

            Spacer(Modifier.height(12.dp))

            PrefsCard(
                settings = settings,
                onWeekStartChange = viewModel::setWeekStartsOnSunday,
                onMonthStartChange = viewModel::setMonthStartDay
            )

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "功能") {
                SummaryRow(
                    emoji = "🏷",
                    title = "分类管理",
                    summary = "${state.categoryCount} 个分类",
                    onClick = { onNavigate("category_manage") }
                )
                ThinDivider()
                SummaryRow(
                    emoji = "🔁",
                    title = "周期记账",
                    summary = if (state.recurringRuleCount == 0) "未设置" else "${state.recurringRuleCount} 条规则",
                    onClick = { onNavigate("recurring") }
                )
                ThinDivider()
                SummaryRow(
                    emoji = "💾",
                    title = "备份与恢复",
                    summary = lastBackupLabel,
                    onClick = { onNavigate("backup") }
                )
                ThinDivider()
                SummaryRow(
                    emoji = "🔒",
                    title = "应用锁",
                    summary = if (lockState?.enabled == true) "已开启" else "未开启",
                    onClick = { onNavigate("lock_settings") }
                )
                ThinDivider()
                SummaryRow(
                    emoji = "ℹ️",
                    title = "关于",
                    summary = updateBadge?.let { "发现新版本 $it" } ?: "版本 $versionName",
                    onClick = { onNavigate("about") }
                )
            }

            Spacer(Modifier.height(10.dp))
            NoteText(
                text = "所有账单数据只保存在本机，不上传服务器。卸载应用会删除数据，请定期备份。",
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AccountCard(state: MineUiState) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📒", fontSize = 21.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "轻账",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = "本地记账 · 数据在你手里",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (state.isLoading) "记录 —" else "共 ${state.transactionCount} 笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        MonthCell(
            label = "${state.monthLabel}支出",
            valueMinor = state.monthExpenseMinor,
            valueColor = ExpenseRed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MonthCell(
    label: String,
    valueMinor: Long,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(
            text = "¥${Money.format(valueMinor)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun ThemeCard(
    themeMode: ThemeMode,
    palette: ThemePalette,
    onModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SectionCard(title = "外观") {
        Text(
            text = "主题模式",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                ChoiceBox(
                    label = modeLabel(mode),
                    selected = mode == themeMode,
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "主题色",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemePalette.entries.forEach { item ->
                PaletteCell(
                    palette = item,
                    selected = item == palette,
                    onClick = { onPaletteChange(item) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PaletteCell(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = palette.label) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(palette.argb))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) colors.onSurface else colors.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(text = "✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}

private fun modeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
private fun PrefsCard(
    settings: AppSettings,
    onWeekStartChange: (Boolean) -> Unit,
    onMonthStartChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SectionCard(title = "记账偏好", subtitle = "日历与年度报告始终按自然月显示") {
        PrefLabel("周起始日")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceBox(
                label = "周一",
                selected = !settings.weekStartsOnSunday,
                onClick = { onWeekStartChange(false) },
                modifier = Modifier.weight(1f)
            )
            ChoiceBox(
                label = "周日",
                selected = settings.weekStartsOnSunday,
                onClick = { onWeekStartChange(true) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))
        PrefLabel("账单月起始日")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..SettingsRepository.MAX_MONTH_START_DAY).forEach { day ->
                ChoiceBox(
                    label = "${day}号",
                    selected = day == settings.monthStartDay,
                    onClick = { onMonthStartChange(day) }
                )
            }
        }
        NoteText(
            text = if (settings.monthStartDay == 1) "当前按自然月统计（每月 1 号起）。"
            else "每月 ${settings.monthStartDay} 号起为一个账单月，统计、流水与概览按月数字随之平移。",
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun PrefLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
