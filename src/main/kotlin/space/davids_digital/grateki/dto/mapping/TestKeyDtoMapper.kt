package space.davids_digital.grateki.dto.mapping

import org.mapstruct.Mapper
import space.davids_digital.grateki.dto.TestKeyDto
import space.davids_digital.grateki.model.TestKey

@Mapper
interface TestKeyDtoMapper {
    fun toDto(model: TestKey): TestKeyDto
    fun toModel(dto: TestKeyDto): TestKey
}