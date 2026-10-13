package com.qingxi.ledger.data.update

import android.content.Context
import android.util.Log
import com.qingxi.ledger.di.AppGraph
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 进程级的更新状态机：冷启动自动检查一次，检查结果跨页面共享（我的页角标 + 关于页）。
 * 下载跟着进程走，离开关于页不会中断已经开始的下载。
 */
class UpdateController(private val app: AppGraph) {

    private val context: Context get() = app.appContext
    private val repository: UpdateRepository get() = app.updates
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** 每个进程只自动检查一次，应用锁解锁或组合重建都不会再发请求。 */
    private var coldStartChecked = false

    init {
        // 上个版本遗留的安装包先清掉，缓存里只保留当前这一份。
        runCatching { repository.clearDownloads() }
    }

    fun resolveSourceUrl(savedUrl: String): String = savedUrl.ifBlank { UpdateRepository.BUILT_IN_SOURCE_URL }

    fun installedVersionName(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    /** 冷启动后台检查：地址与开关现读 DataStore，失败静默回到 Idle，不留下转圈的状态。 */
    fun autoCheckOnColdStart() {
        if (coldStartChecked) return
        coldStartChecked = true
        scope.launch {
            val settings = runCatching { app.settings.settings.first() }.getOrNull() ?: return@launch
            if (!settings.autoUpdateCheck) return@launch
            runCheck(resolveSourceUrl(settings.updateSourceUrl), silent = true)
        }
    }

    fun check(savedUrl: String) {
        checkJob?.cancel()
        checkJob = scope.launch { runCheck(resolveSourceUrl(savedUrl), silent = false) }
    }

    fun cancelCheck() {
        checkJob?.cancel()
        checkJob = null
        if (_status.value is UpdateStatus.Checking) _status.value = UpdateStatus.Idle
    }

    fun download(info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                val file = repository.download(info) { downloaded, total ->
                    _status.value = UpdateStatus.Downloading(info, downloaded, total)
                }
                repository.clearDownloads(keep = file)
                _status.value = UpdateStatus.ReadyToInstall(info, file)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
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

    /** 正在下载/已下载好的时候不能被新一轮检查覆盖状态。 */
    private fun statusLockedForTransfer(): Boolean =
        _status.value is UpdateStatus.Downloading || _status.value is UpdateStatus.ReadyToInstall

    private suspend fun runCheck(sourceUrl: String, silent: Boolean) {
        if (statusLockedForTransfer()) return
        _status.value = UpdateStatus.Checking
        val result = runCatching { repository.fetchInfo(sourceUrl) }
        val info = result.getOrNull()
        if (info == null) {
            val reason = result.exceptionOrNull().readableMessage()
            if (silent) {
                // 自动检查失败只在界面上表现为「什么都没发生」，原因留在 logcat 里便于排查。
                Log.i(TAG, "自动检查更新未成功：$reason（$sourceUrl）")
                _status.value = UpdateStatus.Idle
            } else {
                _status.value = UpdateStatus.Failed(reason)
            }
            return
        }
        _status.value = if (info.versionCode <= installedVersionCode()) {
            UpdateStatus.UpToDate(installedVersionName())
        } else {
            UpdateStatus.Available(info, installedVersionName())
        }
    }

    private fun installedVersionCode(): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.longVersionCode.toInt()
    }.getOrDefault(0)

    private companion object {
        const val TAG = "QingJiUpdate"
    }
}

internal fun Throwable?.readableMessage(): String = when {
    this == null -> "检查失败"
    this is java.net.UnknownHostException -> "无法连接更新源，请检查地址或网络"
    this is SocketTimeoutException -> "请求超时，请稍后再试"
    this is SSLException -> "更新源证书校验失败，请确认地址正确"
    else -> message?.takeIf { it.isNotBlank() } ?: "检查失败"
}
