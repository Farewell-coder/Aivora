package app.aivora.vault.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class KeyDiagnostics(private val versionName: String = "0.0.7") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val userAgent = "Aivora/$versionName (Android; OpenAI-compatible)"

    /**
     * 拉取模型列表。
     *
     * 先按规范化后的端点请求 `/models`；仅在**明确失败**（连接异常或非 2xx）时，
     * 才依次尝试 [Endpoint.candidates] 里的候选路径。判定标准是 HTTP 200 **且**
     * 响应体能解析出 `data` 数组 —— 中转站常把错误也返 200，不能只看状态码。
     */
    suspend fun fetchModels(config: ServiceConfig, key: String): List<String> = withContext(Dispatchers.IO) {
        val trimmedKey = key.trim()
        val primary = config.endpoint.trim().trimEnd('/')
        val primaryResult = runCatching { requestModels(primary + "/models", trimmedKey) }
        primaryResult.getOrNull()?.let { return@withContext it }

        // 只在 /models 明确失败后才试候选，避免无脑全试造成误判与额外计费。
        val candidates = Endpoint.candidates(primary)
            .filter { it != primary + "/models" }
            .take(3)
        for (url in candidates) {
            val r = runCatching { requestModels(url, trimmedKey) }
            r.getOrNull()?.let { return@withContext it }
        }
        // 全部失败：抛出首个错误，最贴近用户真实配置。
        throw primaryResult.exceptionOrNull() ?: DiagnosticException("模型拉取失败")
    }

    /** 单次 `/models` 请求。非 2xx 或结构不符时抛异常，供上层判断是否换候选。 */
    private fun requestModels(url: String, key: String): List<String> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", userAgent).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw DiagnosticException("模型拉取失败（HTTP ${response.code}）：${errorMessage(response.code, body)}")
            val data = JSONObject(body).optJSONArray("data")
                ?: throw DiagnosticException("模型列表格式不受支持")
            return buildList {
                for (i in 0 until data.length()) {
                    data.optJSONObject(i)?.optString("id")?.takeIf(String::isNotBlank)?.let(::add)
                }
            }.distinct().ifEmpty { throw DiagnosticException("服务商未返回模型") }
        }
    }

    suspend fun testModel(config: ServiceConfig, key: String, model: String): ModelResult = withContext(Dispatchers.IO) {
        val key = key.trim()
        val payload = JSONObject().put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Hi")))
            .put("max_tokens", 1).put("stream", false).toString()
        val request = Request.Builder().url(config.endpoint.trim().trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        val started = System.nanoTime()
        try {
            client.newCall(request).execute().use { response ->
                val latency = (System.nanoTime() - started) / 1_000_000
                val body = response.body?.string().orEmpty()
                val detail = if (response.isSuccessful) responseSummary(body) else errorMessage(response.code, body)
                ModelResult(model, if (response.isSuccessful) "可用" else "不可用", response.code, latency, detail)
            }
        } catch (e: IOException) {
            ModelResult(model, "不可用", null, (System.nanoTime() - started) / 1_000_000, "网络异常：${e.message ?: "连接失败"}")
        } catch (e: IllegalArgumentException) {
            ModelResult(model, "不可用", detail = "Endpoint 或请求格式错误")
        }
    }

    suspend fun check(config: ServiceConfig, key: String): ServiceConfig = withContext(Dispatchers.IO) {
        try {
            val models = fetchModels(config, key)
            config.copy(models = models.joinToString(","), checkStatus = "发现 ${models.size} 个模型，待逐项检测", httpStatus = 200, lastCheckedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            config.copy(checkStatus = e.message ?: "检测失败", lastCheckedAt = System.currentTimeMillis())
        }
    }

    private fun responseSummary(body: String): String = try {
        val json = JSONObject(body)
        val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        "响应正常" + (if (text.isBlank()) "" else " · ${text.take(120)}")
    } catch (_: Exception) { "响应成功，内容格式无法解析" }

    private fun errorMessage(code: Int, body: String): String {
        val server = try {
            val json = JSONObject(body); json.optJSONObject("error")?.optString("message")?.ifBlank { null } ?: json.optString("message")
        } catch (_: Exception) { "" }
        val reason = when (code) {
            401 -> if (Regex("quota|insufficient|balance", RegexOption.IGNORE_CASE).containsMatchIn(body)) "余额或配额不足" else "密钥无效或已过期"
            403 -> if (Regex("quota|insufficient|balance", RegexOption.IGNORE_CASE).containsMatchIn(body)) "余额或配额不足" else "服务端拒绝访问：请检查密钥权限、令牌分组或访问策略"
            404 -> "模型不存在或 Endpoint 不支持该请求"
            429 -> if (Regex("quota|insufficient|balance", RegexOption.IGNORE_CASE).containsMatchIn(body)) "余额或配额不足" else "请求频率受限"
            400, 422 -> "模型或请求参数不受支持"
            in 500..599 -> "服务商服务器错误"
            else -> "HTTP $code"
        }
        return reason + (if (server.isBlank()) "" else "：${server.take(180)}")
    }
}

class DiagnosticException(message: String) : IOException(message)