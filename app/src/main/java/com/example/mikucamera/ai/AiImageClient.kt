package com.example.mikucamera.ai

import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class AiImageException(
    message: String,
    val debugLog: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class AiImageClient {
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    fun createEdit(
        baseUrl: String,
        apiKey: String,
        model: String,
        visualStyle: AiVisualStyle,
        outfitStyle: AiOutfitStyle,
        source: File,
        prompt: String,
        destination: File,
        onProgress: (String) -> Unit
    ): File {
        val log = StringBuilder()
        fun record(message: String) {
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            log.append(stamp).append("  ").append(message).append('\n')
        }

        var connection: HttpURLConnection? = null
        try {
            check(apiKey.isNotBlank()) { "请先在 AI 设置中填写 OpenAI API Key" }
            check(model.isNotBlank()) { "请先在 AI 设置中填写图像模型" }
            check(source.isFile && source.length() > 0L) { "干净照片不存在" }
            val endpoint = editEndpoint(baseUrl)
            val sourceSize = readImageSize(source)
            record("miku camera AI request log (sensitive values removed)")
            record("endpoint=${redactUrl(endpoint)}")
            record("model=${model.trim()}")
            record("visualStyle=${visualStyle.name} (${visualStyle.displayName})")
            record("outfitStyle=${outfitStyle.name} (${outfitStyle.displayName})")
            record("apiKey=configured (not logged)")
            record("source=${sourceSize.first}x${sourceSize.second}, bytes=${source.length()}")
            record("promptChars=${prompt.length} (content not logged)")
            record("requestFields=model,prompt,quality,output_format,image[]")

            val boundary = "----MikuCamera${UUID.randomUUID()}"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 300_000
                doOutput = true
                useCaches = false
                setChunkedStreamingMode(65_536)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json, image/*")
            }
            activeConnection.set(connection)

            onProgress("正在上传干净照片")
            connection.outputStream.buffered().use { output ->
                val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
                fun field(name: String, value: String) {
                    writer.append("--$boundary\r\n")
                    writer.append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    writer.append(value).append("\r\n")
                    writer.flush()
                }
                field("model", model.trim())
                field("prompt", prompt)
                field("quality", "medium")
                field("output_format", "jpeg")
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"image[]\"; filename=\"photo.jpg\"\r\n")
                writer.append("Content-Type: image/jpeg\r\n\r\n")
                writer.flush()
                source.inputStream().buffered().use { input -> input.copyTo(output) }
                output.flush()
                writer.append("\r\n--$boundary--\r\n")
                writer.flush()
            }

            onProgress("AI 正在生成 Miku 与时间地点水印")
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            val contentLength = connection.contentLengthLong
            record("responseStatus=$status")
            record("responseContentType=${contentType.ifBlank { "unknown" }}")
            record("responseContentLength=$contentLength")

            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                record(summarizeResponse(body))
                throw IllegalStateException(readApiError(status, body))
            }

            onProgress("正在读取 AI 生成结果")
            if (contentType.substringBefore(';').trim().startsWith("image/", ignoreCase = true)) {
                connection.inputStream.buffered().use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                record("imageSource=direct HTTP image body")
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                record(summarizeResponse(body))
                writeImageFromJson(
                    body = body,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    destination = destination,
                    record = ::record
                )
            }

            val outputSize = readImageSize(destination)
            check(destination.length() > 0L && outputSize.first > 0 && outputSize.second > 0) {
                "服务返回的内容不是可识别的图片"
            }
            record("result=${outputSize.first}x${outputSize.second}, bytes=${destination.length()}, mime=${detectImageMime(destination)}")
            record("completed=true")
            return destination
        } catch (error: Throwable) {
            destination.delete()
            if (error is AiImageException) throw error
            record("errorType=${error.javaClass.simpleName}")
            record("errorMessage=${sanitizeText(error.message.orEmpty()).ifBlank { "unknown" }}")
            record("completed=false")
            throw AiImageException(error.message ?: "AI 图片生成失败", log.toString(), error)
        } finally {
            connection?.let { activeConnection.compareAndSet(it, null) }
            connection?.disconnect()
        }
    }

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    private fun writeImageFromJson(
        body: String,
        baseUrl: String,
        apiKey: String,
        destination: File,
        record: (String) -> Unit
    ) {
        val root = runCatching { JSONObject(body) }
            .getOrElse { throw IllegalStateException("服务返回的不是有效 JSON，也不是图片数据") }
        val first = root.optJSONArray("data")?.optJSONObject(0)
            ?: root.optJSONArray("images")?.optJSONObject(0)
            ?: root.optJSONObject("data")
            ?: root.optJSONObject("image")

        val encoded = sequenceOf(
            first?.optString("b64_json"),
            first?.optString("base64"),
            first?.optString("b64"),
            root.optString("b64_json"),
            root.optString("base64")
        ).filterNotNull().firstOrNull { it.isNotBlank() }
        if (!encoded.isNullOrBlank()) {
            record("imageSource=JSON base64 field, chars=${encoded.length}")
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                .getOrElse { throw IllegalStateException("服务返回的 Base64 图片无法解码") }
            destination.outputStream().buffered().use { it.write(bytes) }
            return
        }

        val imageUrl = sequenceOf(
            first?.optString("url"),
            first?.optString("image_url"),
            first?.optString("output_url"),
            root.optString("url"),
            root.optString("image_url")
        ).filterNotNull().firstOrNull { it.isNotBlank() }
            ?: throw IllegalStateException(
                "服务响应中没有可识别的图片字段（支持 b64_json 或 url），请查看日志确认返回结构"
            )

        if (imageUrl.startsWith("data:image/", ignoreCase = true)) {
            val comma = imageUrl.indexOf(',')
            check(comma > 0 && imageUrl.substring(0, comma).contains(";base64", ignoreCase = true)) {
                "服务返回的 data URL 图片格式无效"
            }
            record("imageSource=JSON data URL, chars=${imageUrl.length}")
            val bytes = runCatching { Base64.decode(imageUrl.substring(comma + 1), Base64.DEFAULT) }
                .getOrElse { throw IllegalStateException("服务返回的 data URL 图片无法解码") }
            destination.outputStream().buffered().use { it.write(bytes) }
            return
        }

        downloadImage(imageUrl, baseUrl, apiKey, destination, record)
    }

    private fun downloadImage(
        value: String,
        baseUrl: String,
        apiKey: String,
        destination: File,
        record: (String) -> Unit
    ) {
        val endpointUri = URI(editEndpoint(baseUrl))
        val resolved = endpointUri.resolve(value)
        require(resolved.scheme?.lowercase() == "https" && !resolved.host.isNullOrBlank()) {
            "服务返回的图片 URL 必须是完整的 HTTPS 地址"
        }
        require(resolved.userInfo == null) { "服务返回的图片 URL 不能包含账号密码" }
        val sameOrigin = resolved.scheme.equals(endpointUri.scheme, true) &&
            resolved.host.equals(endpointUri.host, true) && effectivePort(resolved) == effectivePort(endpointUri)
        record("imageSource=JSON url")
        record("imageUrl=${redactUrl(resolved.toString())}")
        record("imageUrlAuthorization=${if (sameOrigin) "same-origin bearer" else "not sent"}")

        val download = (resolved.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 180_000
            useCaches = false
            setRequestProperty("Accept", "image/*")
            if (sameOrigin) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        activeConnection.set(download)
        try {
            val status = download.responseCode
            record("imageDownloadStatus=$status")
            record("imageDownloadContentType=${download.contentType.orEmpty().ifBlank { "unknown" }}")
            if (status !in 200..299) {
                val error = download.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("生成图片下载失败（$status）：${sanitizeText(error).take(300)}")
            }
            download.inputStream.buffered().use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        } finally {
            activeConnection.compareAndSet(download, null)
            download.disconnect()
        }
    }

    private fun readApiError(status: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return when (status) {
            401 -> "OpenAI API Key 无效或已失效"
            429 -> "OpenAI 请求过多、余额不足或额度已用完"
            else -> "AI 服务请求失败（$status）${message.takeIf { it.isNotBlank() }?.let { ": ${sanitizeText(it)}" }.orEmpty()}"
        }
    }

    private fun summarizeResponse(body: String): String {
        if (body.isBlank()) return "responseBody=empty"
        return runCatching {
            val root = JSONObject(body)
            val rootKeys = root.keys().asSequence().toList().sorted()
            val data = root.optJSONArray("data")
            val first = data?.optJSONObject(0)
            val firstKeys = first?.keys()?.asSequence()?.toList()?.sorted().orEmpty()
            val parts = mutableListOf("responseJsonKeys=${rootKeys.joinToString(",")}")
            if (data != null) parts += "dataItems=${data.length()}"
            if (firstKeys.isNotEmpty()) parts += "data[0]Keys=${firstKeys.joinToString(",")}"
            first?.optString("b64_json")?.takeIf { it.isNotBlank() }?.let {
                parts += "data[0].b64_json=<base64 ${it.length} chars>"
            }
            first?.optString("url")?.takeIf { it.isNotBlank() }?.let {
                parts += "data[0].url=${if (it.startsWith("data:")) "<data URL ${it.length} chars>" else redactUrl(it)}"
            }
            root.optJSONObject("error")?.let { error ->
                parts += "error.type=${sanitizeText(error.optString("type"))}"
                parts += "error.code=${sanitizeText(error.optString("code"))}"
                parts += "error.message=${sanitizeText(error.optString("message")).take(500)}"
            }
            parts.joinToString("\n")
        }.getOrElse {
            "responseBody=non-JSON, chars=${body.length}, preview=${sanitizeText(body).take(500)}"
        }
    }

    private fun readImageSize(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    companion object {
        fun normalizeBaseUrl(value: String): String {
            val candidate = value.trim().ifBlank { AiSettingsStore.DEFAULT_BASE_URL }
            val uri = runCatching { URI(candidate) }
                .getOrElse { throw IllegalArgumentException("Base URL 格式无效") }
            require(uri.scheme?.lowercase() == "https" && !uri.host.isNullOrBlank()) {
                "Base URL 必须是完整的 https:// 地址"
            }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Base URL 不能包含账号密码、查询参数或锚点"
            }
            val normalized = candidate.trimEnd('/')
            require(!normalized.endsWith("/images/edits", ignoreCase = true)) {
                "请填写 Base URL（例如 https://api.openai.com/v1），不要填写完整的 /images/edits 接口"
            }
            return normalized
        }

        fun editEndpoint(baseUrl: String): String = normalizeBaseUrl(baseUrl) + "/images/edits"

        fun detectImageMime(file: File): String {
            val header = ByteArray(12)
            val count = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
            if (count >= 8 && header[0] == 0x89.toByte() && String(header, 1, 3, Charsets.US_ASCII) == "PNG") {
                return "image/png"
            }
            if (count >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                return "image/jpeg"
            }
            if (count >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WEBP") {
                return "image/webp"
            }
            return "image/jpeg"
        }

        private fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else when (uri.scheme.lowercase()) {
            "https" -> 443
            else -> -1
        }

        private fun redactUrl(value: String): String = runCatching {
            val uri = URI(value)
            URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString() +
                if (uri.query != null) "?<redacted>" else ""
        }.getOrElse { "<invalid URL>" }

        private fun sanitizeText(value: String): String = value
            .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "sk-<redacted>")
            .replace(Regex("""(?i)(authorization|api[_-]?key)\s*[:=]\s*[^\s,}]+"""), "$1=<redacted>")
            .replace(Regex("[A-Za-z0-9+/=_-]{256,}"), "<long-data-redacted>")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}
