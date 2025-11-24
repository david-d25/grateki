package space.davids_digital.grateki.dto

import kotlinx.serialization.Serializable

@Serializable
data class TestKeyDto (
    val gradlePath: String,
    val className: String,
    val testName: String,
    val parameters: String? = null
)