package com.ssafy.animal.core.sample.adapter.`in`.web

import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.sample.application.port.`in`.SampleUseCase
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** DTO만 반환한다. 공통 봉투(ApiResponse)는 common의 ApiResponseBodyAdvice가 씌운다. */
@RestController
@RequestMapping("/api/core/v1/samples")
class SampleController(
    private val sampleUseCase: SampleUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("샘플이 생성되었습니다.")
    fun create(@Valid @RequestBody request: CreateSampleRequest) =
        SampleResponse.from(sampleUseCase.create(request.toCommand()))

    @GetMapping("/{sampleSeq}")
    fun get(@PathVariable sampleSeq: Long) = SampleResponse.from(sampleUseCase.get(sampleSeq))

    @GetMapping
    fun getPage(@PageableDefault(size = 20) pageable: Pageable) =
        PageResponse.of(sampleUseCase.getPage(pageable).map(SampleResponse::from))

    @DeleteMapping("/{sampleSeq}")
    fun delete(@PathVariable sampleSeq: Long) {
        sampleUseCase.delete(sampleSeq)
    }
}
