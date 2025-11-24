package space.davids_digital.grateki.dto

import kotlinx.serialization.Serializable

@Serializable
data class TestRunInfoDto (
    val buildId: String,
    val durationMs: Long,
    val status: Status,
    val finishedAt: Long
) {
    enum class Status {
        PASSED,
        FAILED,
        SKIPPED
    }
}