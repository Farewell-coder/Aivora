package app.aivora.vault.data

import android.content.Context
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.aivora.vault.protocol.Akcp
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Entity(tableName = "service_configs")
data class ServiceConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val provider: String, val encryptedKey: String,
    val endpoint: String, val models: String, val defaultModel: String,
    val notes: String = "", val enabled: Boolean = true, val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val lastCheckedAt: Long = 0,
    val checkStatus: String = "未检测", val httpStatus: Int? = null,
    val extensionsJson: String = "{}", val modelResultsJson: String = "[]"
)
@Entity(tableName = "export_records")
data class ExportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String, val encryptedCredential: String,
    val configCount: Int, val exportedAt: Long = System.currentTimeMillis()
)
@Dao interface VaultDao {
    @Query("SELECT * FROM service_configs ORDER BY createdAt DESC") fun configs(): Flow<List<ServiceConfig>>
    @Query("SELECT * FROM service_configs WHERE id=:id") suspend fun byId(id: Long): ServiceConfig?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(config: ServiceConfig): Long
    @Delete suspend fun delete(config: ServiceConfig)
    @Query("UPDATE service_configs SET isDefault=0") suspend fun clearDefaults()
    @Query("UPDATE service_configs SET isDefault=:value WHERE id=:id") suspend fun setDefault(id: Long, value: Boolean)
    @Insert suspend fun recordExport(record: ExportRecord): Long
    @Query("SELECT * FROM export_records ORDER BY exportedAt DESC") fun exports(): Flow<List<ExportRecord>>
}
@Database(entities = [ServiceConfig::class, ExportRecord::class], version = 2, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() { abstract fun dao(): VaultDao }

class LocalCrypto(context: Context) {
    private val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(context, "vault_keys", master,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val key: SecretKeySpec
    init {
        val saved = prefs.getString("data_key", null)
        val bytes = if (saved == null) ByteArray(32).also(SecureRandom()::nextBytes).also {
            prefs.edit().putString("data_key", Base64.getEncoder().encodeToString(it)).apply()
        } else Base64.getDecoder().decode(saved)
        key = SecretKeySpec(bytes, "AES")
    }
    fun encrypt(plain: String): String {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOf(12))) }
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }
}
class VaultRepository(context: Context) {
    private val db = Room.databaseBuilder(context, VaultDatabase::class.java, "aivora.db")
        .addMigrations(object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE service_configs ADD COLUMN modelResultsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }).build()
    val dao = db.dao(); val crypto = LocalCrypto(context)
    fun toAkcp(config: ServiceConfig) = Akcp.Config(config.name, config.provider,
        if (config.encryptedKey.isEmpty()) "" else crypto.decrypt(config.encryptedKey),
        config.endpoint, emptyList(), "", config.notes, config.enabled,
        kotlinx.serialization.json.Json.parseToJsonElement(config.extensionsJson).jsonObject)
    fun fromAkcp(config: Akcp.Config) = ServiceConfig(name = config.config_name, provider = config.provider,
        encryptedKey = crypto.encrypt(config.api_key), endpoint = config.endpoint, models = config.models.joinToString(","),
        defaultModel = config.default_model, notes = config.notes, enabled = config.enabled, extensionsJson = config.extensions.toString())
}