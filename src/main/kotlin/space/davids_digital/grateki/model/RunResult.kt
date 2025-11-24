package space.davids_digital.grateki.model

data class RunResult(
    val workerResults: List<GradleWorkerResult>
) {
    val success: Boolean get() = workerResults.all { it.success }
}