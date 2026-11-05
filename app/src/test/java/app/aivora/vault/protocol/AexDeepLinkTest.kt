package app.aivora.vault.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 深链的纯逻辑测试。
 *
 * 这里刻意只测「不碰 Android 的部分」：JSON 拼装、编码、链接拼接。
 * `queryIntentActivities` 与 `startActivity` 属于设备行为，由真机验证，
 * 单测里用 Robolectric 去模拟只会得到"假通过"。
 */
class AexDeepLinkTest {

    private val receiver = AexDeepLink.Receiver("Dollhouse", "dollhouse", "aivora-import")

    private fun profile() = Aex.Profile(
        id = "aivora-1",
        name = "我的中转站",
        provider = "custom",
        baseUrl = "https://api.example.com/v1",
        apiKey = "sk-EXAMPLE",
        models = listOf("model-a", "model-b"),
        defaultModel = "model-a",
        description = "备注"
    )

    @Test fun payloadCarriesKindSchemaAndVersion() {
        val json = AexDeepLink.buildPayloadJson(profile(), "0.0.8", "2026-10-29T12:00:00Z")
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("aivora.provider", obj["kind"]!!.jsonPrimitive.content)
        assertEquals("1", obj["ver"]!!.jsonPrimitive.content)
        assertEquals("AEX", obj["schema"]!!.jsonPrimitive.content)
        assertEquals("1.1", obj["protocolVersion"]!!.jsonPrimitive.content)
    }

    /** AEX 1.1 的核心约定：深链同样不带模型列表。 */
    @Test fun payloadOmitsModelFields() {
        val json = AexDeepLink.buildPayloadJson(profile(), "0.0.8", "2026-10-29T12:00:00Z")
        val obj = Json.parseToJsonElement(json).jsonObject
        assertNull(obj["models"])
        assertNull(obj["defaultModel"])
    }

    @Test fun payloadKeepsNameEndpointAndKey() {
        val obj = Json.parseToJsonElement(
            AexDeepLink.buildPayloadJson(profile(), "0.0.8", "2026-10-29T12:00:00Z")
        ).jsonObject
        assertEquals("我的中转站", obj["name"]!!.jsonPrimitive.content)
        assertEquals("https://api.example.com/v1", obj["baseUrl"]!!.jsonPrimitive.content)
        assertEquals("sk-EXAMPLE", obj["apiKey"]!!.jsonPrimitive.content)
    }

    @Test fun base64IsUrlSafeAndUnpadded() {
        val encoded = AexDeepLink.encodePayload(AexDeepLink.buildPayloadJson(profile(), "0.0.8"))
        assertFalse("不能含 +", encoded.contains('+'))
        assertFalse("不能含 /", encoded.contains('/'))
        assertFalse("不能含 =", encoded.contains('='))
        // 解码必须还原成原 JSON
        val decoded = AexDeepLink.decodePayload(encoded)
        assertTrue(decoded.contains("aivora.provider"))
    }

    @Test fun linkUsesReceiverSchemeHostAndEnvelopeVersion() {
        val encoded = AexDeepLink.encodePayload(AexDeepLink.buildPayloadJson(profile(), "0.0.8"))
        val link = AexDeepLink.buildLinkString(receiver, encoded)
        assertTrue(link.startsWith("dollhouse://aivora-import?d="))
        assertTrue(link.endsWith("&v=1"))
    }

    @Test fun registryHasDollhouse() {
        assertEquals(1, AexDeepLink.RECEIVERS.size)
        assertEquals("Dollhouse", AexDeepLink.RECEIVERS.first().displayName)
        assertEquals("dollhouse", AexDeepLink.RECEIVERS.first().scheme)
    }
}