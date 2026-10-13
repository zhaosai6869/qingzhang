package com.qingxi.ledger.ui.update

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.update.UpdateStatus
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.settings.NoteText
import com.qingxi.ledger.ui.settings.SectionCard
import com.qingxi.ledger.ui.settings.formatBytes

/**
 * 关于页的「版本更新」区块：填更新源、手动检查、下载校验、调起系统安装器。
 * 不点按钮就不联网，因此不需要后台服务，也不会每次打开应用都发请求。
 */
@Composable
fun UpdateSection(savedUrl: String) {
    val viewModel = appViewModel<UpdateViewModel>()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var installFailed by remember { mutableStateOf(false) }

    // 偏好是异步读出来的，首次拿到已保存地址时填入输入框，但不覆盖用户正在输入的内容。
    LaunchedEffect(savedUrl) {
        if (savedUrl.isNotBlank() && url.isBlank()) url = savedUrl
    }

    SectionCard(title = "版本更新") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim() },
            label = { Text("更新源地址") },
            placeholder = { Text("https://example.com/qingji/update.json") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        NoteText(
            text = "更新源是一份 JSON 清单，需要包含 versionCode、versionName、apkUrl、sha256，" +
                "可选 sizeBytes 与 notes。只接受 https 地址，安装包必须带 SHA-256 校验值。",
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = status !is UpdateStatus.Checking && status !is UpdateStatus.Downloading,
                onClick = {
                    installFailed = false
                    viewModel.saveSourceUrl(url)
                    viewModel.check(url)
                }
            ) { Text(if (status is UpdateStatus.Downloading) "下载中…" else "检查更新") }
            if (status is UpdateStatus.Downloading) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = viewModel::cancelDownload) {
                    Text("取消")
                }
            }
        }

        when (val current = status) {
            UpdateStatus.Idle, UpdateStatus.Checking -> Unit
            is UpdateStatus.UpToDate -> StatusText("已是最新版本（${current.installedVersion}）")
            is UpdateStatus.Failed -> StatusText(current.reason, error = true)
            is UpdateStatus.Available -> AvailableBlock(current) {
                installFailed = false
                viewModel.download(current.info)
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
