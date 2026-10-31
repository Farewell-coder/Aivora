package app.aivora.vault.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Public AKCP 1.0 codec. No Aivora services or Android dependencies are required. */
object Akcp {
    const val EXTENSION = ".aivora"
    const val MAX_FILE_SIZE = 16 * 1024 * 1024
    private val MAGIC = byteArrayOf(0x41, 0x4b, 0x43, 0x50, 0x0d, 0x0a, 0x1a, 0x0a)
    private const val HEADER_SIZE = 64
    private const val TAG_SIZE = 16
    private const val M_KIB = 65536
    private const val ITERATIONS = 3
    private const val PARALLELISM = 1
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val strictJson = Json { ignoreUnknownKeys = false }

    @Serializable
    data class Config(
        val config_name: String,
        val provider: String,
        val api_key: String,
        val endpoint: String,
        val models: List<String> = emptyList(),
        val default_model: String = "",
        val notes: String = "",
        val enabled: Boolean = true,
        val extensions: JsonObject = JsonObject(emptyMap()),
        @kotlinx.serialization.Transient val unknown: MutableMap<String, JsonElement> = mutableMapOf()
    )
    @Serializable
    private data class Payload(val schema: String = "akcp.config-set/1", val configs: List<Config>)
    data class Export(val bytes: ByteArray, val credential: String)
    sealed class Failure(message: String) : Exception(message) {
        class InvalidExtension : Failure("Only .aivora files are accepted")
        class InvalidContainer : Failure("Invalid or truncated AKCP container")
        class UnsupportedVersion : Failure("Unsupported AKCP or cryptography version")
        class Authentication : Failure("Wrong credential or modified file")
        class InvalidPayload : Failure("Invalid AKCP payload")
    }

    fun requireFilename(name: String) {
        if (!name.endsWith(EXTENSION, ignoreCase = false) || name.length <= EXTENSION.length)
            throw Failure.InvalidExtension()
    }
    fun newCredential(random: SecureRandom = SecureRandom()): String {
        val raw = ByteArray(32).also(random::nextBytes)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder(56)
        var buffer = 0; var bits = 0
        raw.forEach { b ->
            buffer = (buffer shl 8) or (b.toInt() and 255); bits += 8
            while (bits >= 5) { out.append(alphabet[(buffer ushr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString().chunked(8).joinToString("-")
    }
    fun export(configs: List<Config>, credential: String = newCredential(), random: SecureRandom = SecureRandom()): Export {
        require(configs.isNotEmpty()) { "No configurations selected" }
        val normalized = credential.replace("-", "").uppercase()
        require(normalized.length == 52) { "Credential must contain 256 bits" }
        val plaintext = JsonObject(mapOf("schema" to JsonPrimitive("akcp.config-set/1"),
            "configs" to JsonArray(configs.map { JsonObject(json.encodeToJsonElement(it).jsonObject + it.unknown) }))
        ).toString().toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = header(salt, nonce, plaintext.size + TAG_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive(normalized, salt), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return Export(header + cipher.doFinal(plaintext), credential.chunked(8).joinToString("-"))
    }
    fun import(bytes: ByteArray, credential: String): List<Config> {
        if (bytes.size < HEADER_SIZE + TAG_SIZE || bytes.size > MAX_FILE_SIZE) throw Failure.InvalidContainer()
        val header = bytes.copyOf(HEADER_SIZE)
        if (!header.copyOf(8).contentEquals(MAGIC)) throw Failure.InvalidContainer()
        val b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        b.position(8)
        if (b.short.toInt() != 1 || b.short.toInt() != 1 || b.short.toInt() != 1 || b.get().toInt() != 1 || b.get().toInt() != 1) throw Failure.UnsupportedVersion()
        b.position(18)
        if (b.int != M_KIB || b.int != ITERATIONS || b.int != PARALLELISM) throw Failure.UnsupportedVersion()
        val length = b.int
        if (length < TAG_SIZE || HEADER_SIZE + length != bytes.size) throw Failure.InvalidContainer()
        val salt = header.copyOfRange(34, 50); val nonce = header.copyOfRange(50, 62)
        if (header[17].toInt() != HEADER_SIZE || header[16].toInt() != 0 || header[62].toInt() != 0 || header[63].toInt() != 0) throw Failure.InvalidContainer()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derive(credential.replace("-", "").uppercase(), salt), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val plain = cipher.doFinal(bytes, HEADER_SIZE, length)
            val root = strictJson.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonObject
            if (root["schema"]?.jsonPrimitive?.content != "akcp.config-set/1") throw Failure.InvalidPayload()
            return root.getValue("configs").jsonArray.map { element -> decodeConfig(element.jsonObject) }
        } catch (e: Failure) { throw e }
        catch (_: javax.crypto.AEADBadTagException) { throw Failure.Authentication() }
        catch (_: Exception) { throw Failure.InvalidPayload() }
    }
    fun findEmbedded(bytes: ByteArray): ByteArray {
        if (bytes.size <= MAX_FILE_SIZE) return bytes
        for (i in 0..bytes.size - HEADER_SIZE) {
            if (bytes.copyOfRange(i, i + 8).contentEquals(MAGIC)) {
                val len = ByteBuffer.wrap(bytes, i + 30, 4).order(ByteOrder.BIG_ENDIAN).int
                if (len >= TAG_SIZE && i + HEADER_SIZE + len <= bytes.size) return bytes.copyOfRange(i, i + HEADER_SIZE + len)
            }
        }
        throw Failure.InvalidContainer()
    }
    private fun decodeConfig(obj: JsonObject): Config {
        val known = setOf("config_name", "provider", "api_key", "endpoint", "models", "default_model", "notes", "enabled", "extensions")
        val config = strictJson.decodeFromJsonElement<Config>(JsonObject(obj.filterKeys { it in known }))
        config.unknown.putAll(obj.filterKeys { it !in known })
        if (config.config_name.isBlank() || config.provider.isBlank() || config.api_key.isBlank() || config.endpoint.isBlank()) throw Failure.InvalidPayload()
        return config
    }
    private fun header(salt: ByteArray, nonce: ByteArray, payloadLength: Int): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC); putShort(1); putShort(1); putShort(1); put(1); put(1); put(0); put(HEADER_SIZE.toByte())
            putInt(M_KIB); putInt(ITERATIONS); putInt(PARALLELISM); putInt(payloadLength)
            put(salt); put(nonce); putShort(0)
        }.array()
    private fun derive(credential: String, salt: ByteArray): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withSalt(salt).withMemoryAsKB(M_KIB).withIterations(ITERATIONS).withParallelism(PARALLELISM).build())
        return ByteArray(32).also { generator.generateBytes(credential.toByteArray(Charsets.UTF_8), it) }
    }
}