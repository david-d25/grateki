package space.davids_digital.grateki.exec.event

import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo

sealed class TestEvent {
    abstract val testKey: TestKey

    class TestStartEvent(
        override val testKey: TestKey
    ) : TestEvent()

    class TestFinishEvent(
        override val testKey: TestKey,
        val runInfo: TestRunInfo
    ) : TestEvent()
}