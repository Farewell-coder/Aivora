package app.aivora.vault.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom

class AexTest {
    private val credential = "A".repeat(52)
    private fun profile(name: String, key: String = "sk-EXAMPLE") = Aex.Profile(
        id = "id-$name", name = name, provider = "custom",
        baseUrl = "https://example.invalid/v1", apiKey = key,
        models = listOf("model-a", "model-b"), defaultModel = "model-a", description = "note"
    )

    @Test fun exportThenImportRoundTripsSingleProfile() {
        val export = Aex.export(listOf(profile("单配置")), "0.0.5", credential)
        val imported = Aex.import(export.bytes, export.credential)
        assertEquals(1, imported.size)
        assertEquals("单配置", imported[0].name)
        assertEquals("sk-EXAMPLE", imported[0].apiKey)
        assertEquals(listOf("model-a", "model-b"), imported[0].models)
        assertEquals("model-a", imported[0].defaultModel)
    }

    @Test fun batchExportKeepsOrderAndCount() {
        val profiles = listOf(profile("一"), profile("二"), profile("三"))
        val export = Aex.export(profiles, "0.0.5", credential)
        assertEquals(3, export.profileCount)
        val imported = Aex.import(export.bytes, export.credential)
        assertEquals(listOf("一", "二", "三"), imported.map { it.name })
    }

    @Test fun fileHasAexMagicAndDeclaresLength() {
        val export = Aex.export(listOf(profile("magic")), "0.0.5", credential)
        val expected = byteArrayOf(0x41, 0x45, 0x58, 0x31, 0x0d, 0x0a, 0x1a, 0x0a)
        assertArrayEquals(expected, export.bytes.copyOf(8))
        assertEquals(Aex.Detected.AEX, Aex.detect(export.bytes))
    }

    @Test(expected = Aex.Failure.Authentication::class)
    fun wrongCredentialIsRejected() {
        val export = Aex.export(listOf(profile("wrong")), "0.0.5", credential)
        Aex.import(export.bytes, "B".repeat(52))
    }

    @Test(expected = Aex.Failure.Authentication::class)
    fun tamperedCiphertextIsRejected() {
        val export = Aex.export(listOf(profile("tamper")), "0.0.5", credential)
        val bytes = export.bytes.copyOf()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        Aex.import(bytes, credential)
    }

    @Test
    fun tamperedHeaderIsRejectedBeforeDerivation() {
        val export = Aex.export(listOf(profile("header")), "0.0.5", credential)
        val text = String(export.bytes, Charsets.ISO_8859_1)
        val patched = text.replace("\"iterations\":600000", "\"iterations\":600001")
        assertTrue(patched != text)
        val failure = runCatching { Aex.import(patched.toByteArray(Charsets.ISO_8859_1), credential) }.exceptionOrNull()
        // 参数校验先于密钥派生，因此这里必须被"不支持的参数"拦住，而不是先付出解密代价。
        assertTrue(failure is Aex.Failure.UnsupportedParameter)
    }

    @Test(expected = Aex.Failure.UnsupportedVersion::class)
    fun futureProtocolVersionIsRejected() {
        val export = Aex.export(listOf(profile("future")), "0.0.5", credential)
        val text = String(export.bytes, Charsets.ISO_8859_1)
        val patched = text.replace("\"protocolVersion\":\"1.0\"", "\"protocolVersion\":\"2.0\"")
        Aex.import(patched.toByteArray(Charsets.ISO_8859_1), credential)
    }

    @Test(expected = Aex.Failure.InvalidMagic::class)
    fun corruptedMagicIsRejected() {
        val export = Aex.export(listOf(profile("magic2")), "0.0.5", credential)
        val bytes = export.bytes.copyOf()
        bytes[0] = 0x00
        Aex.import(bytes, credential)
    }

    @Test(expected = Aex.Failure.InvalidHeader::class)
    fun truncatedFileIsRejected() {
        val export = Aex.export(listOf(profile("trunc")), "0.0.5", credential)
        Aex.import(export.bytes.copyOf(export.bytes.size - 32), credential)
    }

    @Test fun detectDistinguishesLegacyFromAex() {
        val legacyMagic = byteArrayOf(0x41, 0x4b, 0x43, 0x50, 0x0d, 0x0a, 0x1a, 0x0a) + ByteArray(200)
        assertEquals(Aex.Detected.LEGACY, Aex.detect(legacyMagic))
        assertEquals(Aex.Detected.UNKNOWN, Aex.detect(ByteArray(100)))
    }

    @Test fun eachExportUsesFreshSaltAndNonce() {
        val first = Aex.export(listOf(profile("fresh")), "0.0.5", credential)
        val second = Aex.export(listOf(profile("fresh")), "0.0.5", credential)
        assertFalse(first.bytes.contentEquals(second.bytes))
        assertEquals(52, Aex.normalizeCredential(first.credential).length)
    }

    @Test fun credentialNormalizationIsLenientAboutFormatting() {
        // 52 个字符按 8 字符分组，共 7 组（最后一组 4 个字符）。
        val formatted = List(6) { "AAAAAAAA" }.joinToString("-") + "-AAAA"
        assertEquals("A".repeat(52), Aex.normalizeCredential(formatted))
        assertEquals("A".repeat(52), Aex.normalizeCredential(" " + formatted.lowercase() + "\n"))
    }

    @Test(expected = Aex.Failure.Authentication::class)
    fun malformedCredentialIsRejected() {
        Aex.normalizeCredential("too-short")
    }

    @Test fun newCredentialIsRandomAndWellFormed() {
        val a = Aex.newCredential(SecureRandom())
        val b = Aex.newCredential(SecureRandom())
        assertEquals(52, a.replace("-", "").length)
        assertFalse(a == b)
    }

    @Test fun emptyProfileListIsRejected() {
        val failed = runCatching { Aex.export(emptyList(), "0.0.5", credential) }.isFailure
        assertTrue(failed)
    }

    @Test fun fileNameSanitizingAlwaysEndsWithExtension() {
        assertEquals("Aivora.aicp".replace(".aicp", Aex.EXTENSION), Aex.sanitizeFileName("Aivora"))
        assertEquals("a_b.akcp", Aex.sanitizeFileName("a b"))
        assertTrue(Aex.sanitizeFileName("配置 1").endsWith(Aex.EXTENSION))
    }

    @Test fun unknownFieldsArePreservedInExtra() {
        val extra = kotlinx.serialization.json.buildJsonObject {
            put("com.example.flag", kotlinx.serialization.json.JsonPrimitive(true))
        }
        val export = Aex.export(listOf(profile("extra").copy(extra = extra)), "0.0.5", credential)
        val imported = Aex.import(export.bytes, export.credential)
        assertEquals(extra, imported[0].extra)
    }

    /** Loads a vector shipped in protocol/test-vectors and decrypts it with the Kotlin implementation.
     *  This is the cross-language interoperability check: the vectors are produced by an
     *  independent Python implementation (scripts/gen_vectors.py), not by Aivora itself. */
    @Test fun decryptsPythonGeneratedVectors() {
        val dir = File("../protocol/test-vectors")
        if (!dir.isDirectory) return
        val single = File(dir, "single.akcp").readBytes()
        val multiple = File(dir, "multiple.akcp").readBytes()
        assertEquals(Aex.Detected.AEX, Aex.detect(single))
        val one = Aex.import(single, "A".repeat(52))
        assertEquals(1, one.size)
        assertEquals("Synthetic Vector", one[0].name)
        assertEquals("sk-EXAMPLE-NOT-REAL", one[0].apiKey)
        val many = Aex.import(multiple, "A".repeat(52))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), many.map { it.name })
        assertEquals(listOf("m2", "m3"), many[1].models)
    }

    @Test(expected = Aex.Failure.Authentication::class)
    fun pythonVectorRejectedWithWrongCredential() {
        val file = File("../protocol/test-vectors/single.akcp")
        if (!file.isFile) return
        Aex.import(file.readBytes(), "B".repeat(52))
    }
}
