package space.davids_digital.grateki.history

import kotlinx.serialization.json.Json
import org.mapstruct.factory.Mappers
import space.davids_digital.grateki.dto.TestHistoryDto
import space.davids_digital.grateki.dto.mapping.TestKeyDtoMapper
import space.davids_digital.grateki.dto.mapping.TestRunInfoDtoMapper
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonFileHistoryStore(private val path: Path) : HistoryStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val keyMapper = Mappers.getMapper(TestKeyDtoMapper::class.java)
    private val runInfoMapper = Mappers.getMapper(TestRunInfoDtoMapper::class.java)

    private var data: Map<TestKey, List<TestRunInfo>> = emptyMap()

    /**
     * @return The number of test entries loaded from the history file.
     */
    fun load(): Int {
        if (!path.exists()) {
            data = emptyMap()
            return 0
        }
        val fileText = path.readText()
        val dto = json.decodeFromString<TestHistoryDto>(fileText)
        data = dto.tests.associate { (keyDto, runInfoDto) ->
            val key = keyMapper.toModel(keyDto)
            val runInfo = runInfoDto.map { runInfoMapper.toModel(it, key) }
            key to runInfo
        }
        return data.size
    }

    override fun getAll(): Map<TestKey, List<TestRunInfo>> {
        return data
    }

    override fun replace(entries: Map<TestKey, List<TestRunInfo>>) {
        val entryDtos = entries.map { (key, runInfo) -> TestHistoryDto.Entry(
            key = keyMapper.toDto(key),
            runs = runInfo.map(runInfoMapper::toDto)
        ) }
        val historyDto = TestHistoryDto(tests = entryDtos)
        val text = json.encodeToString(historyDto)
        path.parent.createDirectories()
        path.writeText(text)
        data = entries
    }
}