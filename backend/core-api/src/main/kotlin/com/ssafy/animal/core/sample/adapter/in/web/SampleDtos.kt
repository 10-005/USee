package com.ssafy.animal.core.sample.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.sample.application.port.`in`.CreateSampleCommand
import com.ssafy.animal.core.sample.domain.Sample
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class CreateSampleRequest(
    @field:Schema(description = "이름. 필수, 공백 불가, 최대 50자", example = "첫 샘플")
    @field:NotBlank
    @field:Size(max = 50)
    val name: String?,
    @field:Schema(description = "설명. 선택, 최대 500자", nullable = true, example = "예시 설명")
    @field:Size(max = 500)
    val description: String?,
) {
    fun toCommand() = CreateSampleCommand(name!!, description)
}

data class SampleResponse(
    @field:Schema(description = "샘플 번호", example = "1")
    val sampleSeq: Long,
    @field:Schema(description = "이름", example = "첫 샘플")
    val name: String,
    @field:Schema(description = "설명", nullable = true, example = "예시 설명")
    val description: String?,
    @field:Schema(description = "생성 시각", example = "2026-11-09T10:00:00+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "수정 시각. 수정한 적 없으면 null(샘플은 수정 API가 없어 항상 null)", nullable = true, example = "null")
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

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class SampleSuccessSchema : SuccessResponseSchema<SampleResponse>()
abstract class SamplePageSuccessSchema : SuccessResponseSchema<PageResponse<SampleResponse>>()
abstract class SampleNoDataSuccessSchema : SuccessResponseSchema<Any?>()
