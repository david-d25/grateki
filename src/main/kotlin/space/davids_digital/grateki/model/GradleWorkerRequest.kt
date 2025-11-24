package space.davids_digital.grateki.model

import java.nio.file.Path

/**
 * Represents a request to run Gradle tasks in a worker.
 */
data class GradleWorkerRequest(
    /**
     * ID of the worker.
     * It is used to identify the worker in logs, reports, and events.
     */
    val id: Int,

    /**
     * Path to the Gradle project to run the tasks in.
     */
    val projectPath: Path,

    /**
     * List of Gradle tasks to execute.
     */
    val tasks: List<String>,

    /**
     * Path to the init script to use for the Gradle build.
     */
    val initScriptPath: Path? = null,

    /**
     * Map of system properties to set for the Gradle build.
     * A more convenient way for passing configuration to init scripts.
     * Each entry will be converted to a JVM argument of the form `-Dkey=value`.
     */
    val systemProperties: Map<String, String> = emptyMap(),

    /**
     * List of JVM arguments to set for the Gradle build.
     */
    val jvmArgs: List<String> = emptyList(),

    /**
     * Additional Gradle command-line arguments to pass to the build.
     */
    val gradleArgs: List<String> = emptyList(),

    /**
     * Global timeout for the worker execution in milliseconds.
     * A value of 0 means no timeout.
     */
    val timeoutMillis: Long = 0L,

    /**
     * Path to the log file for this worker.
     * Both stdout and stderr will be redirected to this file if provided.
     * If null, output will be disabled.
     */
    val logPath: Path? = null
)
