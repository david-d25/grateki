package space.davids_digital.grateki.model

/**
 * A batch of tests to be executed together.
 */
data class TestBatch (
    val tests: List<TestKey>,
    val totalEstimatedDurationMillis: Long
)