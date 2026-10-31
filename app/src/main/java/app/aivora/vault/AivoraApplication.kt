package app.aivora.vault
import android.app.Application
import app.aivora.vault.data.VaultRepository
class AivoraApplication : Application() { val repository by lazy { VaultRepository(this) } }