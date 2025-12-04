package space.davids_digital.grateki.gradle

import io.mockk.every
import io.mockk.mockk
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GradleEventUtilsTest {
    @Test
    fun `returns module path for task in module`() {
        val taskDescriptor = mockk<TaskOperationDescriptor>()
        every { taskDescriptor.taskPath } returns ":core:moduleA:test"
        every { taskDescriptor.parent } returns null

        val result = GradleEventUtils.resolveGradleModulePath(taskDescriptor)

        assertEquals(":core:moduleA", result)
    }

    @Test
    fun `returns full path when only root test task`() {
        val taskDescriptor = mockk<TaskOperationDescriptor>()
        every { taskDescriptor.taskPath } returns ":test"
        every { taskDescriptor.parent } returns null

        val result = GradleEventUtils.resolveGradleModulePath(taskDescriptor)

        assertEquals(":test", result)
    }

    @Test
    fun `returns full path when no colon in task path`() {
        val taskDescriptor = mockk<TaskOperationDescriptor>()
        every { taskDescriptor.taskPath } returns "test"
        every { taskDescriptor.parent } returns null

        val result = GradleEventUtils.resolveGradleModulePath(taskDescriptor)

        assertEquals("test", result)
    }

    @Test
    fun `walks up parents to find TaskOperationDescriptor`() {
        val rootDescriptor = mockk<OperationDescriptor>()
        val taskDescriptor = mockk<TaskOperationDescriptor>()

        every { rootDescriptor.parent } returns taskDescriptor
        every { taskDescriptor.parent } returns null
        every { taskDescriptor.taskPath } returns ":core:moduleA:test"

        val result = GradleEventUtils.resolveGradleModulePath(rootDescriptor)

        assertEquals(":core:moduleA", result)
    }

    @Test
    fun `returns empty string when no task descriptor in chain`() {
        val rootDescriptor = mockk<OperationDescriptor>()
        val childDescriptor = mockk<OperationDescriptor>()

        every { rootDescriptor.parent } returns childDescriptor
        every { childDescriptor.parent } returns null

        val result = GradleEventUtils.resolveGradleModulePath(rootDescriptor)

        assertEquals("", result)
    }
}
