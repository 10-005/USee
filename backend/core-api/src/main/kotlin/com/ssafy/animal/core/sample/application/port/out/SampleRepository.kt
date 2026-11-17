package com.ssafy.animal.core.sample.application.port.out

import com.ssafy.animal.core.sample.domain.Sample
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface SampleRepository {
    fun save(sample: Sample): Sample
    fun findById(sampleSeq: Long): Sample?
    fun findPage(pageable: Pageable): Page<Sample>
    fun delete(sample: Sample)
}
