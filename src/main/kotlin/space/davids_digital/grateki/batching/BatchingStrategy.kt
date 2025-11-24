package space.davids_digital.grateki.batching

import space.davids_digital.grateki.model.TestBatch
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo

/**
 * Strategy for batching tests into groups for parallel execution.
 */
interface BatchingStrategy {
    /**
     * Creates batches of tests to be executed in parallel.
     *
     * @param tests tests to be batched, mapped by their TestKey to a list of TestRunInfo
     * @param workers number of parallel workers available
     * @return list of test batches. The resulting list size is guaranteed to be less than or equal to [workers].
     */
    fun createBatches(tests: Map<TestKey, List<TestRunInfo>>, workers: Int): List<TestBatch>
}