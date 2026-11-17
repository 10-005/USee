package com.ssafy.animal.core.shelter.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterUseCase
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterView
import com.ssafy.animal.core.shelter.application.port.`in`.UpdateShelterCommand
import com.ssafy.animal.core.shelter.application.port.out.ShelterRepository
import com.ssafy.animal.core.shelter.domain.Shelter
import com.ssafy.animal.core.shelter.domain.ShelterErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class ShelterService(
    private val shelterRepository: ShelterRepository,
    private val catalogUseCase: CatalogUseCase,
    private val imageUseCase: ImageUseCase,
) : ShelterUseCase {

    @Transactional(readOnly = true)
    override fun getPage(keyword: String?, districtCode: String?, pageable: Pageable): Page<ShelterView> {
        districtCode?.let(catalogUseCase::requireDistrict)
        val page = shelterRepository.findPage(keyword?.trim()?.ifEmpty { null }, districtCode, pageable)
        // 페이지 전체 프로필·배경 이미지를 한 번에(N+1 방지, S15P31D106-423)
        val images = imageUseCase.findSummaries(page.content.flatMap(::imageSeqs))
        return page.map { view(it, images) }
    }

    @Transactional(readOnly = true)
    override fun get(shelterSeq: Long?) = view(load(shelterSeq))

    @Transactional
    override fun updateMine(userSeq: Long, shelterSeq: Long?, command: UpdateShelterCommand): ShelterView {
        val shelter = load(shelterSeq)
        val oldImages = listOfNotNull(shelter.profileImageSeq, shelter.backgroundImageSeq)
        val profile = command.profileImageSeq.orKeep(shelter.profileImageSeq)
        val background = command.backgroundImageSeq.orKeep(shelter.backgroundImageSeq)
        // 새로 연결하는 이미지만 소유 확인(Q407). 그대로 다시 보낸 값은 통과
        listOfNotNull(profile, background).filter { it !in oldImages }.forEach { imageUseCase.requireOwned(userSeq, it) }

        shelter.updateOperation(
            description = command.description.orKeep(shelter.description),
            operatingHours = command.operatingHours.orKeep(shelter.operatingHours),
            closedDays = command.closedDays.orKeep(shelter.closedDays),
            profileImageSeq = profile,
            backgroundImageSeq = background,
            modUsrSeq = Math.toIntExact(userSeq),
        )
        shelterRepository.saveAndFlush(shelter)
        // flush 뒤라 이 보호소의 참조는 이미 빠져 있다. 다른 참조가 없을 때만 지운다(Q383)
        (oldImages - setOfNotNull(profile, background)).forEach(imageUseCase::deleteIfUnreferenced)
        return view(shelter)
    }

    private fun load(shelterSeq: Long?): Shelter =
        shelterSeq?.let { shelterRepository.findActive(Math.toIntExact(it)) } ?: throw BusinessException(ShelterErrorCode.SHELTER_NOT_FOUND)

    private fun imageSeqs(shelter: Shelter) = listOfNotNull(shelter.profileImageSeq, shelter.backgroundImageSeq)

    private fun view(shelter: Shelter, images: Map<Long, ImageSummary> = imageUseCase.findSummaries(imageSeqs(shelter))) =
        ShelterView(shelter, shelter.profileImageSeq?.let(images::get), shelter.backgroundImageSeq?.let(images::get))

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)
}
