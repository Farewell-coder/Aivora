package app.aivora.vault.data

@kotlinx.serialization.Serializable
data class ModelResult(
    val name: String,
    val status: String = "未检测",
    val httpStatus: Int? = null,
    val latencyMs: Long? = null,
    val detail: String = ""
)

data class ProviderTemplate(val name: String, val endpoint: String)

val providerTemplates = listOf(
    ProviderTemplate("空白模板", ""),
    ProviderTemplate("OpenAI", "https://api.openai.com/v1"),
    ProviderTemplate("DeepSeek", "https://api.deepseek.com/v1"),
    ProviderTemplate("Moonshot", "https://api.moonshot.cn/v1"),
    ProviderTemplate("智谱 AI", "https://open.bigmodel.cn/api/paas/v4"),
    ProviderTemplate("阿里百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ProviderTemplate("SiliconFlow", "https://api.siliconflow.cn/v1"),
    ProviderTemplate("OpenRouter", "https://openrouter.ai/api/v1"),
    ProviderTemplate("Anthropic", "https://api.anthropic.com/v1"),
    ProviderTemplate("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    ProviderTemplate("Ollama", "http://127.0.0.1:11434/v1"),
    ProviderTemplate("自定义", "")
)
