package space.davids_digital.grateki.model

/**
 * A metric representing the result of a single test execution.
 */
data class TestRunInfo (
    /**
     * The unique key identifying the test.
     */
    val testKey: TestKey,

    /**
     * The unique identifier for the build in which the test was executed.
     */
    val buildId: String,

    /**
     * The duration of the test execution in milliseconds.
     */
    val durationMs: Long,

    /**
     * The status of the test execution.
     */
    val status: TestStatus,

    /**
     * The timestamp of when the test was finished executing (epoch milliseconds).
     */
    val finishedAt: Long,
)