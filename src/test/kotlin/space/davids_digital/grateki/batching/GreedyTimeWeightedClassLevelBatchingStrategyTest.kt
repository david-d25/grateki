package space.davids_digital.grateki.batching

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus

class GreedyTimeWeightedClassLevelBatchingStrategyTest {
    private lateinit var strategy: GreedyTimeWeightedClassLevelBatchingStrategy

    @BeforeEach
    fun init() {
        strategy = GreedyTimeWeightedClassLevelBatchingStrategy()
    }

    @Test
    fun `rejects incorrect number of workers`() {
        val testKey = TestKey("", "", "", "")
        val testRunInfo = TestRunInfo(testKey, "0", 1000, TestStatus.PASSED, 0)
        val tests = mapOf(testKey to listOf(testRunInfo))
        assertThrows<IllegalArgumentException> {
            strategy.createBatches(tests, 0)
        }
        assertThrows<IllegalArgumentException> {
            strategy.createBatches(tests, -1)
        }
    }

    @Test
    fun `returns empty list for no tests`() {
        val tests = emptyMap<TestKey, List<TestRunInfo>>()
        val batches = strategy.createBatches(tests, 3)
        assert(batches.isEmpty())
    }

    @Test
    fun `one test one worker`() {
        val testKey = TestKey("", "com.example.TestClass", "testMethod")
        val testRunInfo = TestRunInfo(testKey, "0", 1000, TestStatus.PASSED, 0)
        val tests = mapOf(testKey to listOf(testRunInfo))
        val batches = strategy.createBatches(tests, 1)
        assertEquals(1, batches.size)
        assertEquals(1, batches[0].tests.size)
        assert(batches[0].tests.contains(testKey))
    }

    @Test
    fun `more workers than tests`() {
        val testKey1 = TestKey("", "com.example.TestClass1", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 1000, TestStatus.PASSED, 0)
        val testKey2 = TestKey("", "com.example.TestClass2", "testMethod2")
        val testRunInfo2 = TestRunInfo(testKey2, "0", 1500, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2)
        )
        val batches = strategy.createBatches(tests, 5)
        assertEquals(2, batches.size)
        assert(batches.any { it.tests.contains(testKey1) })
        assert(batches.any { it.tests.contains(testKey2) })
    }

    @Test
    fun `tests are grouped by class`() {
        val testKey1 = TestKey("", "com.example.TestClass", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 1000, TestStatus.PASSED, 0)
        val testKey2 = TestKey("", "com.example.TestClass", "testMethod2")
        val testRunInfo2 = TestRunInfo(testKey2, "0", 1500, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2)
        )
        val batches = strategy.createBatches(tests, 1)
        assertEquals(1, batches.size)
        assertEquals(2, batches[0].tests.size)
        assertEquals(2500L, batches[0].totalEstimatedDurationMillis)
        assert(batches[0].tests.contains(testKey1))
        assert(batches[0].tests.contains(testKey2))
    }

    @Test
    fun `only passed tests considered for duration`() {
        val testKey1 = TestKey("", "com.example.TestClass", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 125, TestStatus.FAILED, 0)
        val testRunInfo2 = TestRunInfo(testKey1, "0", 333, TestStatus.PASSED, 0)
        val testRunInfo3 = TestRunInfo(testKey1, "0", 500, TestStatus.SKIPPED, 0)
        val testKey2 = TestKey("", "com.example.TestClass", "testMethod2")
        val testRunInfo4 = TestRunInfo(testKey2, "0", 1000, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1, testRunInfo2, testRunInfo3),
            testKey2 to listOf(testRunInfo4)
        )
        val batches = strategy.createBatches(tests, 1)
        assertEquals(1, batches.size)
        assertEquals(2, batches[0].tests.size)
        assertEquals(1333L, batches[0].totalEstimatedDurationMillis)
        assert(batches[0].tests.contains(testKey1))
        assert(batches[0].tests.contains(testKey2))
    }

    @Test
    fun `use fallback if test has no successful runs`() {
        // Test with no successful runs
        val testKey1 = TestKey("", "com.example.TestClass1", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 10L, TestStatus.FAILED, 0)
        // Test whose average will be used as fallback
        val testKey2 = TestKey("", "com.example.TestClass2", "testMethod2")
        val testRunInfo2 = TestRunInfo(testKey2, "0", 1500L, TestStatus.PASSED, 0)
        val testRunInfo3 = TestRunInfo(testKey2, "0", 2000L, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2, testRunInfo3)
        )
        val batches = strategy.createBatches(tests, 2)
        assertEquals(2, batches.size)
        val batchWithTestKey1 = batches.find { it.tests.contains(testKey1) }!!
        assertEquals(1750L, batchWithTestKey1.totalEstimatedDurationMillis)
    }

    @Test
    fun `use fallback if no successful runs at all`() {
        val testKey1 = TestKey("", "com.example.TestClass1", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 100L, TestStatus.FAILED, 0)
        val testKey2 = TestKey("", "com.example.TestClass2", "testMethod2")
        val testRunInfo2 = TestRunInfo(testKey2, "0", 1500L, TestStatus.SKIPPED, 0)
        val testRunInfo3 = TestRunInfo(testKey2, "0", 2000L, TestStatus.FAILED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2, testRunInfo3)
        )
        val batches = strategy.createBatches(tests, 2)
        assertEquals(2, batches.size)
        assertEquals(0L, batches[0].totalEstimatedDurationMillis)
        assertEquals(0L, batches[1].totalEstimatedDurationMillis)
    }

    @Test
    fun `group total duration is sum of test durations`() {
        val testKey1 = TestKey("", "com.example.TestClass", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 1000L, TestStatus.PASSED, 0)
        val testKey2 = TestKey("", "com.example.TestClass", "testMethod2")
        val testRunInfo2 = TestRunInfo(testKey2, "0", 2000L, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2)
        )
        val batches = strategy.createBatches(tests, 1)
        assertEquals(1, batches.size)
        assertEquals(2, batches[0].tests.size)
        assertEquals(3000L, batches[0].totalEstimatedDurationMillis)
        assert(batches[0].tests.contains(testKey1))
        assert(batches[0].tests.contains(testKey2))
    }

    @Test
    fun `batches are balanced by duration`() {
        val testKey1 = TestKey("", "com.example.TestClass1", "testMethod1")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 10L, TestStatus.PASSED, 0)
        val testKey2 = TestKey("", "com.example.TestClass2", "testMethod2")
        val testRunInfo2 = TestRunInfo(testKey2, "0", 9L, TestStatus.PASSED, 0)
        val testKey3 = TestKey("", "com.example.TestClass3", "testMethod3")
        val testRunInfo3 = TestRunInfo(testKey3, "0", 1L, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2),
            testKey3 to listOf(testRunInfo3)
        )
        val batches = strategy.createBatches(tests, 2)
        assertEquals(2, batches.size)
        assertEquals(10L, batches[0].totalEstimatedDurationMillis)
        assertEquals(10L, batches[1].totalEstimatedDurationMillis)
    }

    @Test
    fun `more workers than groups`() {
        val testKey1 = TestKey("", "c1", "m1")
        val testKey2 = TestKey("", "c1", "m2")
        val testKey3 = TestKey("", "c1", "m3")
        val testKey4 = TestKey("", "c1", "m4")
        val testKey5 = TestKey("", "c1", "m5")
        val testKey6 = TestKey("", "c2", "m1")
        val testKey7 = TestKey("", "c2", "m2")
        val testKey8 = TestKey("", "c2", "m3")
        val testKey9 = TestKey("", "c2", "m4")
        val testKey10 = TestKey("", "c2", "m5")
        val testRunInfo1 = TestRunInfo(testKey1, "0", 10L, TestStatus.PASSED, 0)
        val testRunInfo2 = TestRunInfo(testKey2, "0", 10L, TestStatus.PASSED, 0)
        val testRunInfo3 = TestRunInfo(testKey3, "0", 10L, TestStatus.PASSED, 0)
        val testRunInfo4 = TestRunInfo(testKey4, "0", 10L, TestStatus.PASSED, 0)
        val testRunInfo5 = TestRunInfo(testKey5, "0", 10L, TestStatus.PASSED, 0)
        val testRunInfo6 = TestRunInfo(testKey6, "0", 20L, TestStatus.PASSED, 0)
        val testRunInfo7 = TestRunInfo(testKey7, "0", 20L, TestStatus.PASSED, 0)
        val testRunInfo8 = TestRunInfo(testKey8, "0", 20L, TestStatus.PASSED, 0)
        val testRunInfo9 = TestRunInfo(testKey9, "0", 20L, TestStatus.PASSED, 0)
        val testRunInfo10 = TestRunInfo(testKey10, "0", 20L, TestStatus.PASSED, 0)
        val tests = mapOf(
            testKey1 to listOf(testRunInfo1),
            testKey2 to listOf(testRunInfo2),
            testKey3 to listOf(testRunInfo3),
            testKey4 to listOf(testRunInfo4),
            testKey5 to listOf(testRunInfo5),
            testKey6 to listOf(testRunInfo6),
            testKey7 to listOf(testRunInfo7),
            testKey8 to listOf(testRunInfo8),
            testKey9 to listOf(testRunInfo9),
            testKey10 to listOf(testRunInfo10)
        )
        val batches = strategy.createBatches(tests, 10)
        val nonEmptyBatches = batches.filter { it.tests.isNotEmpty() }
        assertEquals(2, nonEmptyBatches.size)
        val batch1 = nonEmptyBatches.minBy { it.totalEstimatedDurationMillis }
        val batch2 = nonEmptyBatches.maxBy { it.totalEstimatedDurationMillis }
        assertEquals(50L, batch1.totalEstimatedDurationMillis)
        assertEquals(100L, batch2.totalEstimatedDurationMillis)
    }
}