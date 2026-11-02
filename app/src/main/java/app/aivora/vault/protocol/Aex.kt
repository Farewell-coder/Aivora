package app.aivora.vault.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AEX 1.0 (Aivora Exchange Protocol) codec.
 *
 * Container: Magic(8) | uint32be headerLength | header JSON (UTF-8) | AES-256-GCM ciphertext+tag
 * Cipher  : AES-256-GCM, 128-bit tag, header is the AAD
 * KDF     : PBKDF2-HMAC-SHA256, 600000 iterations, 16-byte salt
 * Payload : JSON object with top-level profiles[]
 *
 * See protocol/AEX-SPECIFICATION.md for the normative description.
 */
object Aex {
    const val EXTENSION = ".akcp"
    const val MIME_TYPE = "application/vnd.aivora.akcp"
    const val MAX_FILE_SIZE = 16 * 1024 * 1024

    private const val FORMAT = "aivora.exchange/1"
    private const val PROTOCOL_VERSION = "1.0"
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val CIPHER = "AES-256-GCM"
    private const val ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val TAG_SIZE = 16
    private const val MIN_HEADER = 64
    private const val MAX_HEADER = 8192
    private const val PRIVATE_MARKER = "aivora.exchange/"
    private const val LEGACY_FORMAT = "akcp.config-set/1"

    private val MAGIC = byteArrayOf(0x41, 0x45, 0x58, 0x31, 0x0d, 0x0a, 0x1a, 0x0a)
    private val LEGACY_MAGIC = byteArrayOf(0x41, 0x4b, 0x43, 0x50, 0x0d, 0x0a, 0x1a, 0x0a)
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** One configuration inside the encrypted payload. */
    data class Profile(
        val id: String,
        val name: String,
        val provider: String = "",
        val baseUrl: String = "",
        val apiKey: String = "",
        val models: List<String> = emptyList(),
        val defaultModel: String = "",
        val description: String = "",
        val extra: JsonObject = JsonObject(emptyMap())
    )

    data class Export(val bytes: ByteArray, val credential: String, val profileCount: Int)

    /** What kind of file we are looking at, decided by structure and magic, never by suffix. */
    enum class Detected { AEX, LEGACY, UNKNOWN }

    sealed class Failure(message: String) : Exception(message) {
        class InvalidExtension : Failure("仅接受 .akcp 文件")
        class FileTooLarge : Failure("文件超过 16 MiB")
        class InvalidMagic : Failure("无法识别的文件格式")
        class InvalidHeader : Failure("文件头部损坏")
        class UnsupportedVersion : Failure("该文件由更新版本生成，请升级应用后再导入")
        class UnsupportedParameter : Failure("不支持的加密参数")
        class Authentication : Failure("凭证错误或文件已损坏")
        class InvalidPayload : Failure("文件内容格式异常")
    }

    // ---------------------------------------------------------------- credential

    fun newCredential(random: SecureRandom = SecureRandom()): String {
        val raw = ByteArray(32).also(random::nextBytes)
        val out = StringBuilder(56)
        var buffer = 0
        var bits = 0
        raw.forEach { b ->
            buffer = (buffer shl 8) or (b.toInt() and 255)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer ushr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString().chunked(8).joinToString("-")
    }

    /** Strips whitespace and hyphens, uppercases, and requires exactly 52 Base32 characters. */
    fun normalizeCredential(input: String): String {
        val normalized = input.filterNot { it.isWhitespace() || it == '-' }.uppercase()
        if (normalized.length != 52 || normalized.any { it !in ALPHABET }) {
            throw Failure.Authentication()
        }
        return normalized
    }

    // ---------------------------------------------------------------- export

    fun export(
        profiles: List<Profile>,
        exporterVersion: String,
        credential: String = newCredential(),
        random: SecureRandom = SecureRandom()
    ): Export {
        require(profiles.isNotEmpty()) { "No configurations selected" }
        val normalized = normalizeCredential(credential)
        val payload = buildPayload(profiles, exporterVersion)
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val header = buildHeader(salt, nonce, payload.size + TAG_SIZE)
        val magicLength = MAGIC.size + 4
        val aad = ByteArray(magicLength + header.size)
        System.arraycopy(MAGIC, 0, aad, 0, MAGIC.size)
        ByteBuffer.wrap(aad, MAGIC.size, 4).order(ByteOrder.BIG_ENDIAN).putInt(header.size)
        System.arraycopy(header, 0, aad, magicLength, header.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive(normalized, salt), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return Export(aad + cipher.doFinal(payload), credential.chunked(8).joinToString("-"), profiles.size)
    }

    // ---------------------------------------------------------------- detect / import

    fun detect(bytes: ByteArray): Detected {
        if (bytes.size < 8) return Detected.UNKNOWN
        val head = bytes.copyOf(8)
        return when {
            head.contentEquals(MAGIC) -> Detected.AEX
            head.contentEquals(LEGACY_MAGIC) -> Detected.LEGACY
            else -> Detected.UNKNOWN
        }
    }

    fun import(bytes: ByteArray, credential: String): List<Profile> {
        if (bytes.size > MAX_FILE_SIZE) throw Failure.FileTooLarge()
        if (bytes.size < MAGIC.size + 4 + MIN_HEADER + TAG_SIZE) throw Failure.InvalidHeader()
        if (!bytes.copyOf(8).contentEquals(MAGIC)) throw Failure.InvalidMagic()

        val headerLength = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.BIG_ENDIAN).int
        if (headerLength < MIN_HEADER || headerLength > MAX_HEADER) throw Failure.InvalidHeader()
        val headerEnd = 12 + headerLength
        if (headerEnd + TAG_SIZE > bytes.size) throw Failure.InvalidHeader()

        val headerBytes = bytes.copyOfRange(12, headerEnd)
        val header = parseHeader(headerBytes)

        val declared = header["ciphertextLength"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw Failure.InvalidHeader()
        if (declared != bytes.size - headerEnd) throw Failure.InvalidHeader()

        val salt = decodeBase64(header["salt"]?.jsonPrimitive?.content ?: throw Failure.InvalidHeader())
        if (salt.size != SALT_SIZE) throw Failure.InvalidHeader()
        val nonce = decodeBase64(header["nonce"]?.jsonPrimitive?.content ?: throw Failure.InvalidHeader())
        if (nonce.size != NONCE_SIZE) throw Failure.InvalidHeader()

        // AAD is the exact bytes from the file: never reserialize the header.
        val aad = bytes.copyOfRange(0, headerEnd)
        val cipherText = bytes.copyOfRange(headerEnd, bytes.size)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derive(normalizeCredential(credential), salt), "AES"),
                GCMParameterSpec(TAG_BITS, nonce)
            )
            cipher.updateAAD(aad)
            cipher.doFinal(cipherText)
        } catch (_: javax.crypto.AEADBadTagException) {
            throw Failure.Authentication()
        } catch (e: javax.crypto.BadPaddingException) {
            throw Failure.Authentication()
        }

        val root = try {
            Json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonObject
        } catch (_: Exception) {
            throw Failure.InvalidPayload()
        }
        val format = root["format"]?.jsonPrimitive?.content ?: throw Failure.InvalidPayload()
        if (format != FORMAT) throw Failure.InvalidPayload()
        val array = root["profiles"]?.jsonArray ?: throw Failure.InvalidPayload()
        if (array.isEmpty()) throw Failure.InvalidPayload()
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
            if (name.isBlank()) return@mapNotNull null
            Profile(
                id = id.ifBlank { name },
                name = name,
                provider = obj["provider"]?.jsonPrimitive?.content.orEmpty(),
                baseUrl = obj["baseUrl"]?.jsonPrimitive?.content.orEmpty(),
                apiKey = obj["apiKey"]?.jsonPrimitive?.content.orEmpty(),
                models = obj["models"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }.orEmpty(),
                defaultModel = obj["defaultModel"]?.jsonPrimitive?.content.orEmpty(),
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                extra = obj["extra"] as? JsonObject ?: JsonObject(emptyMap())
            )
        }.ifEmpty { throw Failure.InvalidPayload() }
    }

    // ---------------------------------------------------------------- internals

    private fun parseHeader(headerBytes: ByteArray): JsonObject {
        val header = try {
            Json.parseToJsonElement(headerBytes.toString(Charsets.UTF_8)).jsonObject
        } catch (_: Exception) {
            throw Failure.InvalidHeader()
        }
        val format = header["format"]?.jsonPrimitive?.content ?: throw Failure.InvalidHeader()
        if (format != FORMAT) throw Failure.InvalidHeader()
        val version = header["protocolVersion"]?.jsonPrimitive?.content ?: throw Failure.InvalidHeader()
        // 精确匹配：1.x 若将来有不兼容改动，旧版本不得误判为兼容。
        if (version != PROTOCOL_VERSION) throw Failure.UnsupportedVersion()
        if (header["kdf"]?.jsonPrimitive?.content != KDF) throw Failure.UnsupportedParameter()
        if (header["cipher"]?.jsonPrimitive?.content != CIPHER) throw Failure.UnsupportedParameter()
        if (header["tagBits"]?.jsonPrimitive?.content?.toIntOrNull() != TAG_BITS) throw Failure.UnsupportedParameter()
        val iterations = header["iterations"]?.jsonPrimitive?.content?.toIntOrNull() ?: throw Failure.InvalidHeader()
        // Bound-check before the expensive derivation so a hostile file cannot exhaust resources.
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) throw Failure.UnsupportedParameter()
        if (iterations != ITERATIONS) throw Failure.UnsupportedParameter()
        return header
    }

    private fun buildHeader(salt: ByteArray, nonce: ByteArray, cipherTextLength: Int): ByteArray {
        val header = buildJsonObject {
            put("format", FORMAT)
            put("protocolVersion", PROTOCOL_VERSION)
            put("kdf", KDF)
            put("iterations", ITERATIONS)
            put("salt", encodeBase64(salt))
            put("cipher", CIPHER)
            put("nonce", encodeBase64(nonce))
            put("tagBits", TAG_BITS)
            put("ciphertextLength", cipherTextLength)
            put("payloadEncoding", "json")
        }
        return header.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildPayload(profiles: List<Profile>, exporterVersion: String): ByteArray {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("protocolVersion", PROTOCOL_VERSION)
            put("exportedAt", Instant.now().toString())
            putJsonObject("exporter") {
                put("name", "Aivora")
                put("version", exporterVersion)
                put("platform", "android")
            }
            putJsonArray("profiles") {
                profiles.forEach { p ->
                    addJsonObject {
                        put("id", p.id)
                        put("name", p.name)
                        if (p.provider.isNotEmpty()) put("provider", p.provider)
                        if (p.baseUrl.isNotEmpty()) put("baseUrl", p.baseUrl)
                        if (p.apiKey.isNotEmpty()) put("apiKey", p.apiKey)
                        if (p.models.isNotEmpty()) putJsonArray("models") { p.models.forEach { add(it) } }
                        if (p.defaultModel.isNotEmpty()) put("defaultModel", p.defaultModel)
                        if (p.description.isNotEmpty()) put("description", p.description)
                        if (p.extra.isNotEmpty()) put("extra", p.extra)
                    }
                }
            }
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun derive(normalizedCredential: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(normalizedCredential.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeBase64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(text: String): ByteArray = try {
        java.util.Base64.getDecoder().decode(text)
    } catch (_: Exception) {
        throw Failure.InvalidHeader()
    }

    /** True when the buffer carries a recognizable AEX or legacy AKCP marker. */
    fun looksLikeConfigFile(bytes: ByteArray): Boolean = detect(bytes) != Detected.UNKNOWN

    internal fun isLegacyPayloadFormat(format: String): Boolean = format == LEGACY_FORMAT

    internal fun isAexFormat(format: String): Boolean = format.startsWith(PRIVATE_MARKER)

    /** Ensures a share file name keeps the .akcp suffix and drops unsafe characters. */
    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").trim('_')
        val base = cleaned.ifBlank { "Aivora" }
        return if (base.endsWith(EXTENSION)) base else base + EXTENSION
    }

    // ---------------------------------------------------------------- snapshot

    /**
     * 把导出时的 profile 列表序列化为快照 JSON，供「导出记录」保存与重新导出使用。
     * 内容含明文 API Key，调用方必须先加密再落库。
     */
    fun snapshot(profiles: List<Profile>): String = buildJsonObject {
        putJsonArray("profiles") {
            profiles.forEach { p ->
                addJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    if (p.provider.isNotEmpty()) put("provider", p.provider)
                    if (p.baseUrl.isNotEmpty()) put("baseUrl", p.baseUrl)
                    if (p.apiKey.isNotEmpty()) put("apiKey", p.apiKey)
                    if (p.models.isNotEmpty()) putJsonArray("models") { p.models.forEach { add(it) } }
                    if (p.defaultModel.isNotEmpty()) put("defaultModel", p.defaultModel)
                    if (p.description.isNotEmpty()) put("description", p.description)
                    if (p.extra.isNotEmpty()) put("extra", p.extra)
                }
            }
        }
    }.toString()

    /** 从快照 JSON 还原 profile 列表。结构异常时返回空列表。 */
    fun fromSnapshot(json: String): List<Profile> = runCatching {
        val array = Json.parseToJsonElement(json).jsonObject["profiles"]?.jsonArray ?: return emptyList()
        array.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
            if (name.isBlank()) return@mapNotNull null
            Profile(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty().ifBlank { name },
                name = name,
                provider = obj["provider"]?.jsonPrimitive?.content.orEmpty(),
                baseUrl = obj["baseUrl"]?.jsonPrimitive?.content.orEmpty(),
                apiKey = obj["apiKey"]?.jsonPrimitive?.content.orEmpty(),
                models = obj["models"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }.orEmpty(),
                defaultModel = obj["defaultModel"]?.jsonPrimitive?.content.orEmpty(),
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                extra = obj["extra"] as? JsonObject ?: JsonObject(emptyMap())
            )
        }
    }.getOrDefault(emptyList())
}
