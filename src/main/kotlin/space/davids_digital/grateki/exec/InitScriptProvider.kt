package space.davids_digital.grateki.exec

import java.nio.file.Files
import java.nio.file.Path

class InitScriptProvider {
    private val includeScript: Path by lazy { extractResource("grateki-include.gradle.kts") }
    private val excludeScript: Path by lazy { extractResource("grateki-exclude.gradle.kts") }

    fun getInclude(): Path = includeScript
    fun getExclude(): Path = excludeScript

    /**
     * Gradle init scripts must be files on disk, so we extract them from resources to temp files.
     */
    private fun extractResource(name: String): Path {
        val url = requireNotNull(javaClass.classLoader.getResource(name)) {
            "Resource $name not found in classpath"
        }
        val tempFile = Files.createTempFile("grateki-$name-", ".gradle.kts")
        tempFile.toFile().outputStream().use { out ->
            url.openStream().use { it.copyTo(out) }
        }
        return tempFile
    }
}