package app.aivora.vault.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Aivora-private backup container. It is deliberately not an AKCP exchange file. */
object Backup {
    const val EXTENSION = ".aivora-backup"
    private val magic = "AIVORA-BACKUP".toByteArray()
    private const val headerSize = 87
    private val json = Json { encodeDefaults = true }
    @Serializable data class BackupConfig(val name: String, val provider: String, val apiKey: String, val endpoint: String, val models: String = "", val defaultModel: String = "", val notes: String = "", val enabled: Boolean = true, val createdAt: Long = 0, val lastCheckedAt: Long = 0, val checkStatus: String = "未检测", val httpStatus: Int? = null, val extensionsJson: String = "{}", val modelResultsJson: String = "[]")
    @Serializable data class BackupExport(val fileName: String, val credential: String, val configCount: Int, val exportedAt: Long)
    @Serializable data class Payload(val schema: String = "aivora.full-backup/1", val theme: String, val configs: List<BackupConfig>, val exports: List<BackupExport>)
    data class Result(val bytes: ByteArray, val credential: String)

    fun export(theme: String, configs: List<BackupConfig>, exports: List<BackupExport>): Result {
        val credential = Akcp.newCredential()
        val plain = json.encodeToString(Payload(theme = theme, configs = configs, exports = exports)).toByteArray()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN).apply {
            put(magic); put(byteArrayOf(0, 0, 0)); putShort(1); putShort(1); putInt(plain.size + 16)
            put(salt); put(nonce); put(ByteArray(34))
        }.array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(Akcp.deriveForBackup(credential, salt), "AES"), GCMParameterSpec(128, nonce))
            updateAAD(header)
        }
        return Result(header + cipher.doFinal(plain), credential)
    }

    fun import(bytes: ByteArray, credential: String): Payload {
        require(bytes.size in headerSize + 16..16 * 1024 * 1024) { "备份文件无效或过大" }
        val header = bytes.copyOf(headerSize)
        require(header.copyOf(magic.size).contentEquals(magic)) { "这不是 Aivora 全量备份文件" }
        val b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        b.position(16)
        require(b.short.toInt() == 1 && b.short.toInt() == 1) { "不支持的备份版本" }
        val length = b.int
        require(length >= 16 && length + headerSize == bytes.size) { "备份文件不完整" }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(Akcp.deriveForBackup(credential, header.copyOfRange(24, 40)), "AES"), GCMParameterSpec(128, header.copyOfRange(40, 52)))
                updateAAD(header)
            }
            val payload = json.decodeFromString<Payload>(cipher.doFinal(bytes, headerSize, length).toString(Charsets.UTF_8))
            require(payload.schema == "aivora.full-backup/1") { "备份类型不受支持" }
            payload
        } catch (_: javax.crypto.AEADBadTagException) { throw IllegalArgumentException("凭证错误或备份文件已被修改") }
        catch (e: IllegalArgumentException) { throw e }
        catch (_: Exception) { throw IllegalArgumentException("备份内容无效或版本不受支持") }
    }
}
