package com.qingxi.ledger.data.update

import java.io.File

/**
 * 更新源清单里描述的一个可用版本。
 * sha256 必填：没有校验值的安装包一律拒绝，避免下载被截断或替换后照样进安装器。
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String
)

sealed class UpdateStatus {
    /** 还没点过检查。 */
    data object Idle : UpdateStatus()

    data object Checking : UpdateStatus()

    data class UpToDate(val installedVersion: String) : UpdateStatus()

    data class Available(val info: UpdateInfo, val installedVersion: String) : UpdateStatus()

    /** totalBytes <= 0 表示服务端没给 Content-Length，只能显示已下载量。 */
    data class Downloading(val info: UpdateInfo, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()

    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateStatus()

    data class Failed(val reason: String) : UpdateStatus()
}
