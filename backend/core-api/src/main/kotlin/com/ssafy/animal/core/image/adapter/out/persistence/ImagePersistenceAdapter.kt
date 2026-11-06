package com.ssafy.animal.core.image.adapter.out.persistence

import com.ssafy.animal.core.image.application.port.out.ImageRepository
import com.ssafy.animal.core.image.domain.Image
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ImagePersistenceAdapter(
    private val imageJpaRepository: ImageJpaRepository,
) : ImageRepository {

    override fun saveAndFlush(image: Image): Image = imageJpaRepository.saveAndFlush(image)

    override fun findById(imageSeq: Long): Image? = imageJpaRepository.findByIdOrNull(imageSeq)

    override fun delete(image: Image) = imageJpaRepository.delete(image)

    override fun isReferenced(imageSeq: Long) = imageJpaRepository.isReferenced(imageSeq)
}
