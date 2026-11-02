package app.aivora.vault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 第三方格式自动识别与字段映射。 */
class ThirdPartyImportTest {

    private fun ok(bytes: ByteArray) = ThirdParty.parse(bytes) as ThirdParty.Parsed.Ok

    @Test
    fun `Chatbox 单配置`() {
        val json = """
            {"settings":{"apiHost":"https://api.example.com/v1","apiKey":"sk-chatbox",
            "models":[{"modelId":"gpt-4o"},{"modelId":"gpt-4o-mini"}]}}
        """.trimIndent()
        val list = ok(json.toByteArray()).candidates
        assertEquals(1, list.size)
        assertEquals("sk-chatbox", list[0].apiKey)
        assertEquals("https://api.example.com/v1", list[0].endpoint)
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), list[0].models)
        assertEquals("Chatbox", list[0].source)
    }

    @Test
    fun `Continue 多模型`() {
        val json = """
            {"models":[
              {"title":"DeepSeek","provider":"openai","model":"deepseek-chat","apiKey":"sk-a","apiBase":"https://api.deepseek.com/v1"},
              {"title":"Moonshot","provider":"openai","model":"moonshot-v1-8k","apiKey":"sk-b","apiBase":"https://api.moonshot.cn/v1"}
            ]}
        """.trimIndent()
        val list = ok(json.toByteArray()).candidates
        assertEquals(2, list.size)
        assertEquals("sk-a", list[0].apiKey)
        assertEquals("https://api.moonshot.cn/v1", list[1].endpoint)
        assertTrue(list.all { it.source == "Continue" })
    }

    @Test
    fun `LobeChat keyVaults`() {
        val json = """{"keyVaults":{"openai":{"apiKey":"sk-lobe","baseURL":"https://api.example.com/v1"}}}"""
        val list = ok(json.toByteArray()).candidates
        assertEquals(1, list.size)
        assertEquals("sk-lobe", list[0].apiKey)
        assertEquals("LobeChat", list[0].source)
    }

    @Test
    fun `CC Switch env`() {
        val json = """{"name":"主站","env":{"ANTHROPIC_API_KEY":"sk-cc","ANTHROPIC_BASE_URL":"https://api.example.com","ANTHROPIC_MODEL":"claude-sonnet-4"}}"""
        val list = ok(json.toByteArray()).candidates
        assertEquals(1, list.size)
        assertEquals("sk-cc", list[0].apiKey)
        assertEquals("claude-sonnet-4", list[0].defaultModel)
        assertEquals("CC Switch", list[0].source)
    }

    @Test
    fun `New API 渠道数组`() {
        val json = """{"data":[{"name":"渠道A","type":"1","key":"sk-na","base_url":"https://api.example.com","models":"gpt-4o,gpt-4o-mini","group":"default"}]}"""
        val list = ok(json.toByteArray()).candidates
        assertEquals(1, list.size)
        assertEquals("sk-na", list[0].apiKey)
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), list[0].models)
    }

    @Test
    fun `env 文本格式`() {
        val text = """
            # 注释
            OPENAI_API_KEY=sk-env
            OPENAI_API_BASE=https://api.example.com/v1
            OPENAI_MODEL=gpt-4o
        """.trimIndent()
        val list = ok(text.toByteArray()).candidates
        assertEquals(1, list.size)
        assertEquals("sk-env", list[0].apiKey)
        assertEquals("https://api.example.com/v1", list[0].endpoint)
        assertEquals(".env", list[0].source)
    }

    @Test
    fun `Cherry Studio ZIP 内 data json`() {
        // 模拟 Cherry 备份：data.json 内层是 redux 切片再 stringify 一次
        val inner = """{"apiKey":"sk-cherry","baseUrl":"https://api.example.com/v1","models":[{"id":"gpt-4o"}]}"""
        // 用 JSON 的转义规则把内层对象塞成字符串值
        val innerEscaped = inner.replace("\\", "\\\\").replace("\"", "\\\"")
        val dataJson = """{"providers":"$innerEscaped"}"""
        val zip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("data.json"))
                zos.write(dataJson.toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        val result = ThirdParty.parse(zip)
        if (result is ThirdParty.Parsed.Failed) throw AssertionError("解析失败原因: ${result.reason}")
        val list = (result as ThirdParty.Parsed.Ok).candidates
        assertTrue(list.isNotEmpty())
        assertEquals("sk-cherry", list[0].apiKey)
        assertEquals("Cherry Studio", list[0].source)
    }

    @Test
    fun `无法识别时报错`() {
        val result = ThirdParty.parse("这不是配置".toByteArray())
        assertTrue(result is ThirdParty.Parsed.Failed)
    }

    @Test
    fun `空文件报错`() {
        assertTrue(ThirdParty.parse(ByteArray(0)) is ThirdParty.Parsed.Failed)
    }

    @Test
    fun `去重保留首个`() {
        val json = """{"models":[
            {"model":"a","apiKey":"sk-same","apiBase":"https://x.com/v1"},
            {"model":"b","apiKey":"sk-same","apiBase":"https://x.com/v1"}
        ]}"""
        val list = ok(json.toByteArray()).candidates
        assertEquals(1, list.size)
    }
}