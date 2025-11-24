package space.davids_digital.grateki.model

/**
 * Represents the result of running Gradle tasks in a worker.
 */
data class GradleWorkerResult(
    /**
     * ID of the worker.
     * It is used to identify the worker in logs, reports, and events.
     * The ID corresponds to the ID in [GradleWorkerRequest].
     */
    val workerId: Int,

    /**
     * All the tests that were run by this worker.
     */
    val tests: List<TestRunInfo>,

    /**
     * Indicates whether the Gradle tasks executed successfully.
     */
    val success: Boolean,

    /**
     * If the execution failed, this contains the throwable that caused the failure.
     */
    val throwable: Throwable? = null,
)
