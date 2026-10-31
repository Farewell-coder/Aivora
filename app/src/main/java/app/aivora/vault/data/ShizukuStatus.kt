package app.aivora.vault.data
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
object ShizukuStatus {
    fun installed(): Boolean = try { Shizuku.pingBinder(); true } catch (_: Throwable) { false }
    fun running(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    fun granted(): Boolean = running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    fun request() { if (running() && !granted()) Shizuku.requestPermission(1001) }
}