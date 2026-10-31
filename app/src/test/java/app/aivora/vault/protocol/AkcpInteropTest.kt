package app.aivora.vault.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Run with -Dakcp.interop=/absolute/output/directory to generate public synthetic vectors. */
class AkcpInteropTest {
    @Test fun decryptPythonVectorAndGenerateKotlinVector() {
        val directory = System.getProperty("akcp.interop") ?: return
        val dir = File(directory)
        val pythonFile = File(dir, "python-export.aivora")
        if (pythonFile.exists()) {
            val imported = Akcp.import(pythonFile.readBytes(), File(dir, "credential.txt").readText().trim()).single()
            assertEquals("Python interoperability", imported.config_name)
        }
        val config = Akcp.Config("Kotlin interoperability", "Synthetic", "sk-not-a-real-key", "https://example.invalid/v1", listOf("model-a"), "model-a")
        val result = Akcp.export(listOf(config), "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        File(dir, "kotlin-export.aivora").writeBytes(result.bytes)
        File(dir, "kotlin-credential.txt").writeText(result.credential)
    }
}