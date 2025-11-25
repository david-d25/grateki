package space.davids_digital.grateki.exec

import space.davids_digital.grateki.exec.event.TestEventHandler
import space.davids_digital.grateki.model.GradleWorkerRequest
import space.davids_digital.grateki.model.GradleWorkerResult

/**
 * Executes Gradle test tasks based on the provided [GradleWorkerRequest].
 * Returns a [GradleWorkerResult] containing the outcome of the execution.
 */
interface GradleTestExecutor {
    fun run(request: GradleWorkerRequest, eventHandler: TestEventHandler? = null): GradleWorkerResult
}