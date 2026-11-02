package app.aivora.vault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 端点规范化的 10 步规则与三级判别。 */
class EndpointTest {

    private fun norm(input: String) = Endpoint.normalize(input).endpoint

    @Test
    fun `裸 IP 端口补 v1`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604"))
    }

    @Test
    fun `缺协议时 IP 补 http`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("103.236.89.43:54604"))
    }

    @Test
    fun `末尾斜杠被剥掉`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604/"))
    }

    @Test
    fun `版本段大写转小写`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604/V1"))
    }

    @Test
    fun `重复斜杠被折叠`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604/v1///"))
    }

    @Test
    fun `剥掉 chat completions`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604/v1/chat/completions"))
    }

    @Test
    fun `剥掉 chat completions 后带斜杠`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604/v1/chat/completions/"))
    }

    @Test
    fun `剥掉 models`() {
        assertEquals("http://103.236.89.43:54604/v1", norm("http://103.236.89.43:54604/v1/models"))
    }

    @Test
    fun `官方模板原样返回`() {
        assertEquals("https://api.deepseek.com/v1", norm("https://api.deepseek.com/v1"))
        assertEquals("https://api.openai.com/v1", norm("https://api.openai.com/v1"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", norm("https://generativelanguage.googleapis.com/v1beta/openai"))
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", norm("https://dashscope.aliyuncs.com/compatible-mode/v1"))
        assertEquals("http://127.0.0.1:11434/v1", norm("http://127.0.0.1:11434/v1"))
    }

    @Test
    fun `已有版本段时保留不补`() {
        assertEquals("https://x.com/api/v1", norm("https://x.com/api/v1"))
    }

    @Test
    fun `接口层名只补 v1 不剥`() {
        assertEquals("https://x.com/api/openai/v1", norm("https://x.com/api/openai"))
    }

    @Test
    fun `中转前缀含 proxy 时按第三方补 v1`() {
        // 含 /proxy/ 中转标识：判为第三方，安全补 /v1。
        val r = Endpoint.normalize("https://x.com/proxy/anthropic")
        assertEquals(Endpoint.Kind.THIRD_PARTY, r.kind)
        assertTrue(r.changed)
        assertEquals("https://x.com/proxy/anthropic/v1", r.endpoint)
    }

    @Test
    fun `无中转标识且末尾是接口层名时不猜`() {
        // 既无 /proxy/ 类标识、又无版本段、末尾是接口层名 -> 保留原样并提示。
        val r = Endpoint.normalize("https://x.com/whatever/anthropic")
        assertEquals(Endpoint.Kind.UNKNOWN, r.kind)
        assertFalse(r.changed)
        assertEquals("https://x.com/whatever/anthropic", r.endpoint)
        assertTrue(r.note.isNotEmpty())
    }

    @Test
    fun `关闭开关时不改写`() {
        val r = Endpoint.normalize("http://103.236.89.43:54604", enabled = false)
        assertEquals("http://103.236.89.43:54604", r.endpoint)
        assertFalse(r.changed)
    }

    @Test
    fun `内网地址补 v1`() {
        assertEquals("http://192.168.1.5:8080/v1", norm("http://192.168.1.5:8080"))
    }

    @Test
    fun `候选路径包含裸 base 与 v1`() {
        val c = Endpoint.candidates("http://x:54604/v1")
        assertTrue(c.contains("http://x:54604/models"))
        assertTrue(c.contains("http://x:54604/v1/models"))
        assertTrue(c.contains("http://x:54604/api/v1/models"))
    }

    @Test
    fun `模板被标记为 TEMPLATE`() {
        assertEquals(Endpoint.Kind.TEMPLATE, Endpoint.normalize("https://api.moonshot.cn/v1").kind)
    }

    @Test
    fun `第三方 IP 被标记为 THIRD_PARTY`() {
        assertEquals(Endpoint.Kind.THIRD_PARTY, Endpoint.normalize("http://103.236.89.43:54604").kind)
    }

    @Test
    fun `空输入不报错`() {
        assertEquals("", norm(""))
    }
}