package space.davids_digital.grateki.dto.mapping

import org.mapstruct.Mapper
import space.davids_digital.grateki.dto.TestRunInfoDto
import space.davids_digital.grateki.model.TestKey
import space.davids_digital.grateki.model.TestRunInfo
import space.davids_digital.grateki.model.TestStatus

@Mapper
interface TestRunInfoDtoMapper {
    fun toDto(model: TestRunInfo): TestRunInfoDto
    fun toModel(dto: TestRunInfoDto, testKey: TestKey): TestRunInfo

    fun toDto(model: TestStatus): TestRunInfoDto.Status
    fun toModel(dto: TestRunInfoDto.Status): TestStatus
}