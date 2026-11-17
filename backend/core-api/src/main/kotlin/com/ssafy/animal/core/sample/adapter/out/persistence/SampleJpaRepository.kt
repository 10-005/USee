package com.ssafy.animal.core.sample.adapter.out.persistence

import com.ssafy.animal.core.sample.domain.Sample
import org.springframework.data.jpa.repository.JpaRepository

interface SampleJpaRepository : JpaRepository<Sample, Long>
