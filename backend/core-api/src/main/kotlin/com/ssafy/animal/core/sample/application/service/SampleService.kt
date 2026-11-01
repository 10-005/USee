package com.ssafy.animal.core.sample.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.sample.application.port.`in`.CreateSampleCommand
import com.ssafy.animal.core.sample.application.port.`in`.SampleUseCase
import com.ssafy.animal.core.sample.application.port.out.SampleRepository
import com.ssafy.animal.core.sample.domain.Sample
import com.ssafy.animal.core.sample.domain.SampleCreatedEvent
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SampleService(
    private val sampleRepository: SampleRepository,
    private val eventPublisher: EventPublisher,
) : SampleUseCase {

    @Transactional
    override fun create(command: CreateSampleCommand): Sample {
        val sample = sampleRepository.save(Sample(command.name, command.description))
        eventPublisher.publish(SampleCreatedEvent(sample.sampleSeq!!, sample.name))
        return sample
    }

    override fun get(sampleSeq: Long) =
        sampleRepository.findById(sampleSeq) ?: throw BusinessException(ErrorCode.COMMON_NOT_FOUND)

    override fun getPage(pageable: Pageable) = sampleRepository.findPage(pageable)

    @Transactional
    override fun delete(sampleSeq: Long) {
        sampleRepository.delete(get(sampleSeq))
    }
}
