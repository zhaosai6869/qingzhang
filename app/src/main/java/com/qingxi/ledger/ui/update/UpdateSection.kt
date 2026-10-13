package com.qingxi.ledger.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qingxi.ledger.data.update.UpdateStatus
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.settings.NoteText
import com.qingxi.ledger.ui.settings.SectionCard
import com.qingxi.ledger.ui.settings.formatBytes

/**
 * 关于页的「版本更新」区块：默认用内置更新源，冷启动自动检查一次，这里只做手动复查与下载安装。
 * 自定义地址是高级项，默认收起，填了就走自己的源。
 */
@Composable
fun UpdateSection(
    savedUrl: String,
    autoCheck: Boolean,
    status: UpdateStatus,
    installedVersion: String
) {
    val viewModel = appViewModel<UpdateViewModel>()
    val controller = viewModel.controller
    var customUrl by rememberSaveable { mutableStateOf(savedUrl) }
    var showCustom by rememberSaveable { mutableStateOf(savedUrl.isNotBlank()) }
    var installFailed by remember { mutableStateOf(false) }

    SectionCard(title = "版本更新") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "打开应用时自动检查",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "只读版本清单，不下载任何东西；失败也不提示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoCheck,
                onCheckedChange = viewModel::setAutoCheck
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "更新源：${viewModel.sourceLabel(savedUrl)} · ${if (showCustom) "收起" else "自定义"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { showCustom = !showCustom }
                .padding(vertical = 6.dp)
        )
        if (showCustom) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it.trim() },
                label = { Text("自定义更新源地址（留空用内置）") },
                placeholder = { Text("https://example.com/qingji/update.json") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.saveSourceUrl(customUrl) }
            ) { Text("保存自定义地址") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = status !is UpdateStatus.Checking && status !is UpdateStatus.Downloading,
                onClick = {
                    installFailed = false
                    controller.check(savedUrl)
                }
            ) {
                Text(
                    when (status) {
                        is UpdateStatus.Downloading -> "下载中…"
                        is UpdateStatus.Checking -> "检查中…"
                        else -> if (status is UpdateStatus.Idle) "检查更新" else "重新检查"
                    }
                )
            }
            if (status is UpdateStatus.Checking) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = controller::cancelCheck) {
                    Text("取消")
                }
            }
            if (status is UpdateStatus.Downloading) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = controller::cancelDownload) {
                    Text("取消")
                }
            }
        }

        when (val current = status) {
            UpdateStatus.Idle, UpdateStatus.Checking -> Unit
            is UpdateStatus.UpToDate -> StatusText("已是最新版本（${installedVersion}）")
            is UpdateStatus.Failed -> StatusText(current.reason, error = true)
            is UpdateStatus.Available -> AvailableBlock(current) {
                installFailed = false
                controller.download(current.info)
            }
            is UpdateStatus.Downloading -> DownloadingBlock(current)
            is UpdateStatus.ReadyToInstall -> ReadyBlock(
                current = current,
                installFailed = installFailed,
                canInstall = viewModel.canInstallUnknownApps(),
                onInstall = { installFailed = !viewModel.install(current.file) },
                onOpenSettings = viewModel::openUnknownAppSettings
            )
        }
    }
}

@Composable
private fun AvailableBlock(current: UpdateStatus.Available, onDownload: () -> Unit) {
    val info = current.info
    Spacer(Modifier.height(12.dp))
    Text(
        text = "发现新版本 ${info.versionName}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    StatusText("当前 ${current.installedVersion} · 安装包 ${if (info.sizeBytes > 0L) formatBytes(info.sizeBytes) else "大小未知"}")
    if (info.notes.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        NoteText(text = info.notes)
    }
    Spacer(Modifier.height(10.dp))
    Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) { Text("下载新版本") }
}

@Composable
private fun DownloadingBlock(current: UpdateStatus.Downloading) {
    Spacer(Modifier.height(12.dp))
    val done = formatBytes(current.downloadedBytes)
    StatusText(
        if (current.totalBytes > 0L) "下载中 $done / ${formatBytes(current.totalBytes)}"
        else "下载中 $done"
    )
    if (current.totalBytes > 0L) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (current.downloadedBytes.toFloat() / current.totalBytes.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReadyBlock(
    current: UpdateStatus.ReadyToInstall,
    installFailed: Boolean,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Spacer(Modifier.height(12.dp))
    StatusText("已下载 ${current.info.versionName}，SHA-256 校验通过")
    Spacer(Modifier.height(10.dp))
    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("安装") }
    if (!canInstall) {
        Spacer(Modifier.height(8.dp))
        NoteText(text = "首次安装需要允许「安装未知应用」，点下面按钮去系统设置里打开。")
        Spacer(Modifier.height(6.dp))
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenSettings) {
            Text("去系统设置授权")
        }
    }
    if (installFailed) {
        Spacer(Modifier.height(6.dp))
        StatusText("没能调起安装界面，请先完成系统授权后再点「安装」。", error = true)
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}
