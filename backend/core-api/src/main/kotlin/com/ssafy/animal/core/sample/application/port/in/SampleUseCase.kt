package com.ssafy.animal.core.sample.application.port.`in`

import com.ssafy.animal.core.sample.domain.Sample
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface SampleUseCase {
    fun create(command: CreateSampleCommand): Sample
    fun get(sampleSeq: Long): Sample
    fun getPage(pageable: Pageable): Page<Sample>
    fun delete(sampleSeq: Long)
}

data class CreateSampleCommand(
    val name: String,
    val description: String?,
)
