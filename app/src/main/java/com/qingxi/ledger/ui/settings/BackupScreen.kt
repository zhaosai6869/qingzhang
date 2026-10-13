package com.qingxi.ledger.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingxi.ledger.data.backup.ImportMode
import java.io.File
import kotlinx.coroutines.launch

/**
 * 备份与恢复：导出 JSON / 加密 .qjbak / CSV，从任意位置选择文件恢复。
 * 全程使用系统文件选择器（SAF），不申请传统存储权限。
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val backups = rememberBackupRepository()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf(ImportMode.MERGE) }

    val backupList by produceState(initialValue = emptyList(), backups) {
        value = runCatching { backups.listBackups() }.getOrDefault(emptyList())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val plain = password.isBlank()
        scope.launch {
            val temp = File(backups.backupDir, backups.suggestedFileName(if (plain) "json" else "enc", ""))
            val count = backups.writeJsonToFile(temp, password.trim().ifBlank { null })
            val copied = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> temp.copyTo(out) } != null
            }.getOrDefault(false)
            if (!copied) temp.delete()
            snackbar.showSnackbar(
                if (copied) "已导出 $count 笔（${if (plain) "JSON" else "加密备份"}）" else "导出失败，请重试"
            )
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val temp = File(backups.backupDir, backups.suggestedFileName("csv", ""))
            val count = backups.writeCsvToFile(temp)
            val copied = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> temp.copyTo(out) } != null
            }.getOrDefault(false)
            if (!copied) temp.delete()
            snackbar.showSnackbar(if (copied) "已导出 $count 笔 CSV" else "导出失败，请重试")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = backups.readFromUri(uri, password.trim().ifBlank { null })
            if (raw == null) {
                snackbar.showSnackbar("无法读取该文件，加密备份需要先填口令")
                return@launch
            }
            val info = backups.parseBackupInfo(raw)
            if (info == null) {
                snackbar.showSnackbar("这不是轻账的备份文件（CSV 请用表格软件查看）")
                return@launch
            }
            val result = backups.importJson(raw, importMode)
            snackbar.showSnackbar("已导入 ${result.imported} 笔，跳过 ${result.skipped} 笔重复")
        }
    }

    SubPageShell(title = "备份与恢复", onBack = onBack, snackbarHostState = snackbar) {
        SectionCard(
            title = "导出",
            subtitle = backupList.size.let { if (it == 0) "尚无备份" else "$it 个文件" }
        ) {
            NoteText(text = "先写一个口令可以生成加密备份（.qjbak）；留空则是可读的 JSON。")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("备份口令（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                EyeIcon(visible = reveal, modifier = Modifier.clickable { reveal = !reveal })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        exportLauncher.launch(
                            backups.suggestedFileName(if (password.isBlank()) "json" else "enc", "")
                        )
                    }
                ) { Text(if (password.isBlank()) "导出 JSON" else "导出加密备份") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { csvLauncher.launch(backups.suggestedFileName("csv", "")) }
                ) { Text("导出 CSV") }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "恢复", subtitle = if (importMode == ImportMode.MERGE) "合并" else "覆盖") {
            NoteText(
                text = "合并：按时间+金额+分类去重，只补进缺失的记录。覆盖：先清空现有账单再导入，附件文件不会被删除。"
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceBox(
                    label = "合并导入",
                    selected = importMode == ImportMode.MERGE,
                    onClick = { importMode = ImportMode.MERGE },
                    modifier = Modifier.weight(1f)
                )
                ChoiceBox(
                    label = "覆盖导入",
                    selected = importMode == ImportMode.REPLACE,
                    onClick = { importMode = ImportMode.REPLACE },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "text/csv", "application/octet-stream"))
                }
            ) { Text("选择备份文件恢复") }
            if (importMode == ImportMode.REPLACE) {
                NoteText(
                    text = "覆盖导入会先删除现有全部账单，建议先导出一份加密备份。",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "应用内备份目录") {
            if (backupList.isEmpty()) {
                CenteredHint("这里还没有生成过备份文件")
            } else {
                Column {
                    backupList.forEach { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = if (file.name.endsWith(".qjbak")) "🔐" else if (file.name.endsWith(".csv")) "📄" else "🗂",
                                fontSize = 16.sp,
                                modifier = Modifier.width(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${formatShortDateTime(file.millis)} · ${formatBytes(file.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            NoteText(
                text = "该目录位于应用私有外部存储，卸载应用会被系统一并清除，重要数据请导出到手机其他位置。",
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun File.copyTo(out: java.io.OutputStream) {
    inputStream().use { input -> input.copyTo(out) }
}
