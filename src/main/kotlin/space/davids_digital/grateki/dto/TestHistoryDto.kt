package space.davids_digital.grateki.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TestHistoryDto (
    @EncodeDefault
    val version: Int = 1, // for future compatibility
    val tests: List<Entry>
) {
    @Serializable
    data class Entry (
        val key: TestKeyDto,
        val runs: List<TestRunInfoDto>
    )
}