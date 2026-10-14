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
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

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

    fun sourceCandidates(savedUrl: String): List<UpdateSource> =
        if (savedUrl.isBlank()) UpdateRepository.BUILT_IN_SOURCES
        else listOf(UpdateSource(savedUrl.trim()))

    fun installedVersionName(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    /** 冷启动后台检查：地址与开关现读 DataStore，失败静默回到 Idle，不留下转圈的状态。 */
    fun autoCheckOnColdStart() {
        if (coldStartChecked) return
        coldStartChecked = true
        checkJob?.cancel()
        // 挂在 checkJob 上，界面上的「取消」才停得掉这次自动检查，否则会等它跑完再覆盖状态。
        checkJob = scope.launch {
            val settings = runCatching { app.settings.settings.first() }.getOrNull() ?: return@launch
            if (!settings.autoUpdateCheck) return@launch
            runCheck(settings.updateSourceUrl, silent = true)
        }
    }

    fun check(savedUrl: String) {
        checkJob?.cancel()
        checkJob = scope.launch { runCheck(savedUrl, silent = false) }
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

    private suspend fun runCheck(savedUrl: String, silent: Boolean) {
        if (statusLockedForTransfer()) return
        _status.value = UpdateStatus.Checking
        var lastReason = "检查失败"
        for (source in sourceCandidates(savedUrl)) {
            // 中途点了取消就到此为止，别让「本来不该再跑的这一次」被当成这个源失败了。
            coroutineContext.ensureActive()
            val outcome = fetchSource(source)
            val info = outcome.info
            if (info == null) {
                // 单个源失败不算失败，接着试下一个；原因逐个进 logcat，界面上只留最后一条。
                lastReason = outcome.reason
                Log.i(TAG, "更新源没读通：${source.manifestUrl} → $lastReason")
                continue
            }
            val result = UpdateRepository.viaSource(info, source)
            _status.value = if (result.versionCode <= installedVersionCode()) {
                UpdateStatus.UpToDate(installedVersionName())
            } else {
                UpdateStatus.Available(result, installedVersionName())
            }
            return
        }
        if (silent) {
            // 自动检查全部没读通时界面上表现为「什么都没发生」。
            _status.value = UpdateStatus.Idle
        } else {
            _status.value = UpdateStatus.Failed(lastReason)
        }
    }

    private fun installedVersionCode(): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.longVersionCode.toInt()
    }.getOrDefault(0)

    /**
     * 试一个更新源，最多等 [UpdateRepository.SOURCE_ATTEMPT_TIMEOUT_MILLIS]。
     *
     * connectTimeout 给不了这个保证：Android 的 HttpURLConnection 是 OkHttp 实现的，它会对解析到的
     * 每个地址各试一遍 connectTimeout，DNS 本身也没有时限——一个被黑洞的 raw.githubusercontent.com
     * 能卡住两分半，排在后面的镜像永远走不到。阻塞中的调用打断不了，就不去打断它：请求交给独立协程，
     * 等待方到点自己松手（await 被取消不会取消 deferred），卡住的那次留在 IO 线程上等内核超时。
     * 一轮检查最多滞留 4 个线程，够不到 Dispatchers.IO 的 64 上限。
     * 尝试挂在当前检查任务下，所以 cancelCheck() 会把它一并取消，不必另存引用。
     */
    private suspend fun fetchSource(source: UpdateSource): SourceOutcome {
        val attempt = CoroutineScope(coroutineContext).async(Dispatchers.IO) {
            try {
                Result.success(repository.fetchInfo(source.manifestUrl))
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                // 异常必须留在这里：一旦冒到 checkJob，整轮检查就死掉，界面会一直停在「检查中…」。
                Result.failure(t)
            }
        }
        val settled = try {
            withTimeoutOrNull(UpdateRepository.SOURCE_ATTEMPT_TIMEOUT_MILLIS) { attempt.await() }
        } finally {
            attempt.cancel()
        }
        if (settled == null) return SourceOutcome(null, TIMEOUT_REASON)
        return settled.fold(
            onSuccess = { SourceOutcome(it) },
            onFailure = { SourceOutcome(null, it.readableMessage()) }
        )
    }

    private companion object {
        const val TAG = "QingJiUpdate"

        /** 超时的说法得自己写：kotlinx 的 TimeoutCancellationException 文案不能透给用户。 */
        const val TIMEOUT_REASON = "更新源没有响应，请稍后再试"
    }
}

/** 一个源一次尝试的结果：读到清单就带 info，没读到就带一条能直接进界面的中文原因。 */
private data class SourceOutcome(val info: UpdateInfo?, val reason: String = "")

internal fun Throwable?.readableMessage(): String = when {
    this == null -> "检查失败"
    this is java.net.UnknownHostException -> "无法连接更新源，请检查地址或网络"
    this is SocketTimeoutException -> "请求超时，请稍后再试"
    this is SSLException -> "更新源证书校验失败，请确认地址正确"
    else -> message?.takeIf { it.isNotBlank() } ?: "检查失败"
}
