package space.davids_digital.grateki.history

import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo

/**
 * A storage interface for test run history.
 * Implementations can store and retrieve test run information based on test keys.
 */
interface HistoryStore {
    /**
     * Retrieves all stored test run information.
     */
    fun getAll(): Map<TestKey, List<TestRunInfo>>

    /**
     * Replaces the existing test run information with the provided entries.
     * The store will only contain the entries given in the parameter after this operation.
     */
    fun replace(entries: Map<TestKey, List<TestRunInfo>>)
}