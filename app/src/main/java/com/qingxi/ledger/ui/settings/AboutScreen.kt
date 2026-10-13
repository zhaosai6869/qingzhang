package com.qingxi.ledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.settings.AppSettings
import com.qingxi.ledger.data.settings.ThemeMode
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.ui.update.UpdateSection
import java.io.File

private const val DATABASE_NAME = "qingji.db"

/** 关于页：版本、在线更新、数据存放位置、隐私说明与开源许可。 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = rememberAppGraph()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = packageInfo?.versionName ?: "0.1.0"
    val databaseFile: File = context.getDatabasePath(DATABASE_NAME)
    val databaseSize = if (databaseFile.exists()) databaseFile.length() else 0L
    val attachmentDir = graph.attachmentDir
    val attachmentSize = attachmentDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()

    SubPageShell(title = "关于", onBack = onBack) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📒", fontSize = 26.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "轻账 · QingJi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "版本 $versionName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            NoteText(text = "本地优先的记账工具：记账、看流水、日历、统计，不要求注册账号，不含广告。")
        }

        Spacer(Modifier.height(12.dp))

        UpdateSection(savedUrl = settings.updateSourceUrl)

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "数据存放") {
            InfoRow(label = "账单数据库", value = databaseFile.absolutePath)
            InfoRow(label = "数据库大小", value = formatBytes(databaseSize))
            InfoRow(label = "附件目录", value = attachmentDir.absolutePath)
            InfoRow(label = "附件大小", value = formatBytes(attachmentSize))
            InfoRow(label = "备份目录", value = graph.backups.backupDir.absolutePath)
            InfoRow(label = "更新包缓存", value = graph.updates.downloadDir.absolutePath)
            NoteText(
                text = "以上路径都在应用私有目录内，其他应用读不到；卸载即清除。换机或防丢请用「备份与恢复」导出。",
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "隐私") {
            BulletRow("账单、分类、备注、附件全部保存在本机，不上传服务器。")
            BulletRow("唯一的联网动作是「检查更新」：只访问你自己填写的更新源地址，不点按钮就不发任何请求。")
            BulletRow("应用不申请传统存储权限，导出与恢复都通过系统文件选择器完成。")
            BulletRow("没有任何统计埋点、广告 SDK 或第三方登录。")
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "开源许可") {
            NoteText(
                text = "本项目为独立实现，未使用参考项目的代码。技术栈均以 Apache License 2.0 或 BSD 许可分发：" +
                    "Jetpack Compose、Room、DataStore、Navigation、Lifecycle、Biometric、Gson、Coil。" +
                    "界面图标使用系统 emoji 字体渲染，不额外打包图标资源。"
            )
        }

        Spacer(Modifier.height(12.dp))
        NoteText(
            text = "当前外观：${settings.palette.label} · ${modeName(settings.themeMode)}",
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

private fun modeName(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BulletRow(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.width(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
