package space.davids_digital.grateki.batching

import space.davids_digital.grateki.model.TestBatch
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus

/**
 * A batching strategy that groups tests by their gradle module path and class and distributes them into batches
 * using a greedy algorithm based on estimated execution time.
 */
class GreedyTimeWeightedClassLevelBatchingStrategy : BatchingStrategy {
    /**
     * Creates test batches by grouping tests by their Gradle module path and class name,
     * then distributing these groups into batches using a greedy algorithm as follows:
     *
     * 1. Group tests by their Gradle module path and class name. Different test keys may belong to the same group
     *    if they share the same module path and class name, regardless of test method names.
     * 2. Estimate the total execution time for each group based on historical test run data.
     *    Only successful test runs are considered for time estimation; failed or skipped runs are ignored.
     *    If no historical data is available for a test, the average duration of all tests with historical data is used
     *    as a fallback.
     * 3. Sort the groups in descending order based on their estimated execution times.
     * 4. Distribute the groups into the specified number of batches using a greedy algorithm:
     *    - Initialize empty batches.
     *    - Iteratively assign each group to the batch with the currently lowest total estimated execution time.
     *
     * The resulting batches are guaranteed to have whole classes assigned to the same batch.
     * In other words, all tests from the same class will be executed together in the same batch.
     */
    override fun createBatches(tests: Map<TestKey, List<TestRunInfo>>, workers: Int): List<TestBatch> {
        require(workers >= 1) { "Number of workers must be at least 1" }

        if (tests.isEmpty()) return emptyList()
        val finalWorkerCount = workers.coerceAtMost(tests.size)

        // Group tests by (gradlePath, className)
        data class TestGroupKey(val gradlePath: String, val className: String)

        // Mapping group to its test keys
        val groupedTests = tests.keys.groupBy { TestGroupKey(it.gradlePath, it.className) }

        // Calculate average duration of all successful test runs for fallback
        val allSuccessfulDurations = tests.flatMap { (_, runs) ->
            runs.filter { it.status == TestStatus.PASSED }.map { it.durationMs }
        }
        val averageDuration = if (allSuccessfulDurations.isNotEmpty()) {
            allSuccessfulDurations.average().toLong()
        } else {
            0L
        }

        // Estimate execution time for each group. One group has many test keys, so we sum their estimates.
        val groupEstimates = groupedTests.mapValues { (_, testKeys) ->
            testKeys.sumOf { testKey ->
                val successfulRuns = tests[testKey]?.filter { it.status == TestStatus.PASSED } ?: emptyList()
                if (successfulRuns.isNotEmpty()) {
                    successfulRuns.map { it.durationMs }.average().toLong()
                } else {
                    averageDuration
                }
            }
        }

        // Sort groups by estimated duration descending
        val sortedGroups = groupEstimates.entries.sortedByDescending { it.value }

        // Initialize empty batches
        val batches = List(finalWorkerCount) { mutableListOf<TestKey>() }
        val batchDurations = LongArray(finalWorkerCount) { 0L }

        // Distribute groups into batches using a greedy algorithm
        for ((groupKey, estimatedDuration) in sortedGroups) {
            // Find the batch with the minimum total estimated duration
            val minBatchIndex = batchDurations.indices.minByOrNull { batchDurations[it] }!!
            // Add all tests in the group to this batch
            batches[minBatchIndex].addAll(groupedTests[groupKey]!!)
            // Update the batch's total estimated duration
            batchDurations[minBatchIndex] += estimatedDuration
        }

        // Create TestBatch objects
        return batches.mapIndexed { index, testKeys ->
            TestBatch(
                tests = testKeys,
                totalEstimatedDurationMillis = batchDurations[index]
            )
        }
    }
}