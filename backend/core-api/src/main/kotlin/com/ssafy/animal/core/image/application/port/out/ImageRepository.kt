package com.ssafy.animal.core.image.application.port.out

import com.ssafy.animal.core.image.domain.Image

interface ImageRepository {
    /** 그 자리에서 INSERT한다. image_key UNIQUE 위반이면 DataIntegrityViolationException */
    fun saveAndFlush(image: Image): Image
    fun findById(imageSeq: Long): Image?
    fun delete(image: Image)
}
