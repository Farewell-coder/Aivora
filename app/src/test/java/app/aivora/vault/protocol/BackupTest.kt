package app.aivora.vault.protocol

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTripAndAuthentication() {
        val config = Backup.BackupConfig("test", "provider", "secret-key", "http://127.0.0.1:8080/v1", models = "a,b", modelResultsJson = "[]")
        val exported = Backup.export("DARK", listOf(config), listOf(Backup.BackupExport("test.aivora", "credential", 1, 123)))
        val decoded = Backup.import(exported.bytes, exported.credential)
        assertEquals("secret-key", decoded.configs.single().apiKey)
        assertEquals("DARK", decoded.theme)
        assertEquals("credential", decoded.exports.single().credential)
        assertTrue(Backup.import(exported.bytes, exported.credential).configs.single().endpoint.endsWith("/v1"))
        try { Backup.import(exported.bytes, Akcp.newCredential()); fail("wrong credential accepted") } catch (_: IllegalArgumentException) {}
        val damaged = exported.bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        try { Backup.import(damaged, exported.credential); fail("damaged backup accepted") } catch (_: IllegalArgumentException) {}
    }
}