package space.davids_digital.grateki.exec.event

fun interface TestEventHandler {
    fun handle(event: TestEvent)
}