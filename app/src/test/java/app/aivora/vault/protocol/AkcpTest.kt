package app.aivora.vault.protocol
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
class AkcpTest {
    private val config = Akcp.Config("测试 🌐", "Example", "sk-synthetic-only", "https://example.invalid/v1", listOf("a","b"), "a", "备注", true, buildJsonObject { put("extensions.example.color", JsonPrimitive("purple")) }, mutableMapOf("future.field" to JsonPrimitive(42)))
    @Test fun roundtripUnicodeAndUnknownFields() { val e=Akcp.export(listOf(config)); val got=Akcp.import(e.bytes,e.credential).single(); assertEquals(config.config_name,got.config_name); assertEquals("purple",got.extensions["extensions.example.color"]?.jsonPrimitive?.content); assertEquals(42,got.unknown["future.field"]?.jsonPrimitive?.int) }
    @Test(expected=Akcp.Failure.Authentication::class) fun wrongCredentialFails() { val e=Akcp.export(listOf(config)); Akcp.import(e.bytes,Akcp.newCredential()) }
    @Test(expected=Akcp.Failure.Authentication::class) fun tamperFails() { val e=Akcp.export(listOf(config)); e.bytes[e.bytes.size-1]=(e.bytes.last().toInt() xor 1).toByte(); Akcp.import(e.bytes,e.credential) }
    @Test(expected=Akcp.Failure.InvalidContainer::class) fun truncationFails() { val e=Akcp.export(listOf(config)); Akcp.import(e.bytes.copyOf(e.bytes.size-1),e.credential) }
    @Test(expected=Akcp.Failure.UnsupportedVersion::class) fun versionRejected() { val e=Akcp.export(listOf(config)); e.bytes[8]=2; Akcp.import(e.bytes,e.credential) }
    @Test(expected=Akcp.Failure.InvalidExtension::class) fun suffixIsMandatory() { Akcp.requireFilename("config.akcp") }
    @Test fun suffixAccepted() { Akcp.requireFilename("config.aivora") }
    @Test fun uniqueSaltNonceAndCredentials() { val a=Akcp.export(listOf(config)); val b=Akcp.export(listOf(config)); assertNotEquals(a.credential,b.credential); assertFalse(a.bytes.copyOfRange(35,63).contentEquals(b.bytes.copyOfRange(35,63))) }
    @Test fun deterministicVector() { val random=SecureRandom(byteArrayOf(1)); val e=Akcp.export(listOf(config),"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",random); assertEquals(config.api_key,Akcp.import(e.bytes,e.credential).single().api_key) }
}