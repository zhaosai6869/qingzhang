package com.qingxi.ledger.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.update.UpdateController
import com.qingxi.ledger.di.AppGraph
import java.io.File
import kotlinx.coroutines.launch

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * 关于页的在线更新入口。检查与下载的状态都在 [UpdateController]（进程级），
 * 这里只负责安装相关的 Intent 和设置写入。
 */
class UpdateViewModel(private val graph: AppGraph) : ViewModel() {

    private val context: Context = graph.appContext
    val controller: UpdateController = graph.updateController

    fun installedVersionName(): String = controller.installedVersionName()

    fun sourceLabel(savedUrl: String): String =
        if (savedUrl.isBlank()) "内置（GitHub 主源，连不上时依次试镜像）" else savedUrl

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setAutoUpdateCheck(enabled) }
    }

    fun saveSourceUrl(url: String) {
        viewModelScope.launch { graph.settings.setUpdateSourceUrl(url) }
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
}
