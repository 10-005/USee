package com.ssafy.animal.core.sample.adapter.`in`.web

import com.ssafy.animal.core.sample.application.port.`in`.CreateSampleCommand
import com.ssafy.animal.core.sample.domain.Sample
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class CreateSampleRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String?,
    @field:Size(max = 500)
    val description: String?,
) {
    fun toCommand() = CreateSampleCommand(name!!, description)
}

data class SampleResponse(
    val sampleSeq: Long,
    val name: String,
    val description: String?,
    val regDttm: OffsetDateTime?,
    val modDttm: OffsetDateTime?,
) {
    companion object {
        fun from(sample: Sample) = SampleResponse(
            sampleSeq = sample.sampleSeq!!,
            name = sample.name,
            description = sample.description,
            regDttm = sample.regDttm,
            modDttm = sample.modDttm,
        )
    }
}
