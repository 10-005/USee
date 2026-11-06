package com.ssafy.animal.core.image.application.port.out

import com.ssafy.animal.core.image.domain.Image

interface ImageRepository {
    /** 그 자리에서 INSERT한다. image_key UNIQUE 위반이면 DataIntegrityViolationException */
    fun saveAndFlush(image: Image): Image
    fun findById(imageSeq: Long): Image?
    fun delete(image: Image)

    /** 어느 도메인이든 이 이미지를 아직 참조하는지(Q383) */
    fun isReferenced(imageSeq: Long): Boolean
}
