package com.ssafy.animal.core.image.adapter.out.persistence

import com.ssafy.animal.core.image.domain.Image
import org.springframework.data.jpa.repository.JpaRepository

interface ImageJpaRepository : JpaRepository<Image, Long>
