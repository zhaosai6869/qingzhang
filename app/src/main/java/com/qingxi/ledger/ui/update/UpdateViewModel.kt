package com.qingxi.ledger.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.update.UpdateInfo
import com.qingxi.ledger.data.update.UpdateRepository
import com.qingxi.ledger.data.update.UpdateStatus
import com.qingxi.ledger.di.AppGraph
import java.io.File
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * 关于页的在线更新状态机：检查清单 -> 下载并校验 -> 调起系统安装器。
 * 只在用户点「检查更新」时联网，不做任何自动轮询。
 */
class UpdateViewModel(private val graph: AppGraph) : ViewModel() {

    private val context: Context = graph.appContext
    private val repository: UpdateRepository = graph.updates

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status

    private var downloadJob: Job? = null

    init {
        // 缓存目录里可能留着上一个版本的安装包，进页面先清干净。
        repository.clearDownloads()
    }

    fun installedVersionName(): String = packageInfo?.versionName ?: ""

    fun saveSourceUrl(url: String) {
        viewModelScope.launch { graph.settings.setUpdateSourceUrl(url) }
    }

    fun check(sourceUrl: String) {
        viewModelScope.launch {
            _status.value = UpdateStatus.Checking
            val result = runCatching { repository.fetchInfo(sourceUrl) }
            val info = result.getOrNull()
            if (info == null) {
                _status.value = UpdateStatus.Failed(result.exceptionOrNull().readableMessage())
                return@launch
            }
            _status.value = if (info.versionCode <= installedVersionCode()) {
                UpdateStatus.UpToDate(installedVersionName())
            } else {
                UpdateStatus.Available(info, installedVersionName())
            }
        }
    }

    fun download(info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            try {
                val file = repository.download(info) { downloaded, total ->
                    _status.value = UpdateStatus.Downloading(info, downloaded, total)
                }
                repository.clearDownloads(keep = file)
                _status.value = UpdateStatus.ReadyToInstall(info, file)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _status.value = UpdateStatus.Failed(t.readableMessage())
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        repository.clearDownloads()
        _status.value = UpdateStatus.Idle
    }

    /** 「安装未知应用」授权，未授权时界面改为引导去系统设置。 */
    fun canInstallUnknownApps(): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** 返回 false 表示没能拉起安装界面，由界面给出提示。 */
    fun install(file: File): Boolean {
        if (!canInstallUnknownApps()) return false
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return false
        // ACTION_INSTALL_PACKAGE 在 API 30 起废弃，ACTION_VIEW 也是各家 ROM 安装器最稳的入口。
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openUnknownAppSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private val packageInfo get() = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    private fun installedVersionCode(): Int =
        packageInfo?.let { it.longVersionCode.toInt() } ?: 0
}

private fun Throwable?.readableMessage(): String = when {
    this == null -> "检查失败"
    this is java.net.UnknownHostException -> "无法连接更新源，请检查地址或网络"
    this is SocketTimeoutException -> "请求超时，请稍后再试"
    this is SSLException -> "更新源证书校验失败，请确认地址正确"
    else -> message?.takeIf { it.isNotBlank() } ?: "检查失败"
}
