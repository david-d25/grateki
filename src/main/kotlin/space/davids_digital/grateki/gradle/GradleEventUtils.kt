package space.davids_digital.grateki.gradle

import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.task.TaskOperationDescriptor

object GradleEventUtils {
    fun resolveGradleModulePath(descriptor: OperationDescriptor): String {
        var current: OperationDescriptor? = descriptor
        while (current != null) {
            if (current is TaskOperationDescriptor) {
                val taskPath = current.taskPath // ":core:moduleA:test"
                val lastColon = taskPath.lastIndexOf(':')
                return if (lastColon > 0) {
                    taskPath.take(lastColon) // ":core:moduleA"
                } else {
                    taskPath
                }
            }
            current = current.parent
        }
        return ""
    }
}