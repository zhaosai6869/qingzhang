package com.qingxi.ledger.data.update

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qingxi.ledger.di.AppGraph
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 在线更新：拉取更新源清单（默认内置的 GitHub update.json，也允许用户换地址），下载 APK 并校验 SHA-256。
 * 只接受 https 地址，明文 http 一律拒绝（Android 默认也禁止明文流量）。
 */
class UpdateRepository(private val app: AppGraph) {

    private val context: Context get() = app.appContext

    /** 安装包落在缓存目录，装完或取消后由 clearDownloads 清掉。 */
    val downloadDir: File
        get() = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    suspend fun fetchInfo(sourceUrl: String): UpdateInfo = withContext(Dispatchers.IO) {
        val json = readText(requireHttps(sourceUrl))
        parse(json)
    }

    /** 下载到 update_<versionCode>.apk，边写边算摘要；摘要不匹配则删文件并抛错。 */
    suspend fun download(info: UpdateInfo, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val url = requireHttps(info.apkUrl)
        val target = File(downloadDir, "update_${info.versionCode}.apk")
        runCatching { target.delete() }
        val connection = open(url)
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in HTTP_OK_RANGE) error("下载失败：服务器返回 $code")
            val total = connection.contentLengthLong
            var downloaded = 0L
            var lastReport = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val chunk = ByteArray(8 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(chunk)
                        if (read <= 0) break
                        digest.update(chunk, 0, read)
                        output.write(chunk, 0, read)
                        downloaded += read
                        if (downloaded - lastReport >= PROGRESS_STEP_BYTES || downloaded == total) {
                            lastReport = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            val actual = digest.digest().toHexLower()
            if (actual != info.sha256.lowercase()) {
                runCatching { target.delete() }
                error("校验不通过：SHA-256 与清单不一致，已丢弃下载文件")
            }
            onProgress(downloaded, if (total > 0L) total else downloaded)
            target
        } finally {
            connection.disconnect()
        }
    }

    /** 只保留当前这一份安装包，避免旧版本 APK 堆在缓存里。 */
    fun clearDownloads(keep: File? = null) {
        downloadDir.listFiles()?.forEach { file ->
            if (keep == null || file.absolutePath != keep.absolutePath) runCatching { file.delete() }
        }
    }

    private suspend fun readText(url: URL): String {
        val connection = open(url)
        return try {
            connection.connect()
            val code = connection.responseCode
            if (code !in HTTP_OK_RANGE) error("读取失败：服务器返回 $code")
            val length = connection.contentLengthLong
            if (length > MANIFEST_MAX_BYTES) error("更新清单过大（上限 ${MANIFEST_MAX_BYTES / 1024} KB）")
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun open(url: URL): HttpURLConnection {
        coroutineContext.ensureActive()
        val connection = url.openConnection() as? HttpURLConnection
            ?: error("无法建立连接：仅支持 http(s) 更新源")
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        return connection
    }

    private fun requireHttps(text: String): URL {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) error("请先填写更新源地址")
        val uri = runCatching { URI.create(trimmed) }.getOrElse { error("地址格式不正确") }
        if (uri.scheme !in ALLOWED_SCHEMES || uri.host.isNullOrEmpty()) error("更新源必须是 https 开头的完整地址")
        return runCatching { uri.toURL() }.getOrElse { error("地址格式不正确") }
    }

    private fun parse(raw: String): UpdateInfo {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }
            .getOrElse { error("更新清单不是合法 JSON") }
        val versionCode = root.optInt("versionCode")
        if (versionCode <= 0) error("更新清单缺少 versionCode")
        val versionName = root.optString("versionName").ifEmpty { "v$versionCode" }
        val apkUrl = root.optString("apkUrl")
        if (apkUrl.isEmpty()) error("更新清单缺少 apkUrl")
        val sha256 = root.optString("sha256")
        if (sha256.length != SHA256_HEX_LENGTH || !sha256.all { it in HEX_DIGITS }) {
            error("更新清单缺少 64 位十六进制 SHA-256，出于安全不安装无校验值的安装包")
        }
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            sizeBytes = root.optLong("sizeBytes"),
            notes = root.optString("notes")
        )
    }

    companion object {
        /**
         * 内置更新源：仓库 main 分支的 update.json。
         * 每次发版都要同步这个文件里的 versionCode / apkUrl / sha256 / sizeBytes，否则自动检查发现不了新版本。
         */
        const val BUILT_IN_SOURCE_URL =
            "https://raw.githubusercontent.com/zhaosai6869/qingzhang/main/update.json"

        private const val DIR_NAME = "update"
        const val DIGEST_ALGORITHM = "SHA-256"
        const val SHA256_HEX_LENGTH = 64
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MANIFEST_MAX_BYTES = 256 * 1024L
        const val PROGRESS_STEP_BYTES = 32 * 1024L
        val ALLOWED_SCHEMES = setOf("https")
        val HEX_DIGITS = ('0'..'9').toSet() + ('a'..'f').toSet() + ('A'..'F').toSet()
        val HTTP_OK_RANGE = 200..299
    }
}

private fun ByteArray.toHexLower(): String =
    joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun JsonObject.optInt(name: String, fallback: Int = 0): Int =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: fallback

private fun JsonObject.optLong(name: String, fallback: Long = 0L): Long =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull() ?: fallback

private fun JsonObject.optString(name: String): String =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString }.getOrNull() ?: ""
