package com.ssafy.animal.core.shelter.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.shelter.application.port.`in`.UpdateShelterCommand
import com.ssafy.animal.core.shelter.application.port.out.ShelterRepository
import com.ssafy.animal.core.shelter.domain.Shelter
import com.ssafy.animal.core.shelter.domain.ShelterErrorCode
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ShelterServiceTest {

    private val repository = mock(ShelterRepository::class.java)
    private val catalog = mock(CatalogUseCase::class.java)
    private val images = mock(ImageUseCase::class.java)
    private val service = ShelterService(repository, catalog, images)

    private val shelter = Shelter("구미시 동물보호센터", "경상북도 구미시", 36.34, 128.27, "054-481-5809", "47190").also {
        ReflectionTestUtils.setField(it, "shelterSeq", 7)
        it.updateOperation("소개", "09:00~18:00", "일요일", 1701, 1702, 77)
    }

    init {
        given(repository.findActive(7)).willReturn(shelter)
        given(repository.saveAndFlush(anyArg(Shelter::class.java))).willAnswer { it.arguments[0] }
    }

    @Test
    fun `9_4 새 이미지만 소유 확인하고, 바뀐 이전 이미지는 flush 뒤 정리한다`() {
        given(images.requireOwned(77, 1800)).willReturn(ImageSummary(1800, "https://cdn/1800.jpg"))

        val view = service.updateMine(77, 7, UpdateShelterCommand(closedDays = Optional.empty(), profileImageSeq = Optional.of(1800)))

        assertNull(view.shelter.closedDays)
        assertEquals("09:00~18:00", view.shelter.operatingHours)
        assertEquals(1800, view.shelter.profileImageSeq)
        assertEquals(1702, view.shelter.backgroundImageSeq)
        assertEquals(77, view.shelter.modUsrSeq)
        then(images).should().deleteIfUnreferenced(1701)
        then(images).should(never()).deleteIfUnreferenced(1702)
    }

    @Test
    fun `9_4 이미지 null은 연결 해제, 남의 이미지는 403`() {
        service.updateMine(77, 7, UpdateShelterCommand(backgroundImageSeq = Optional.empty()))
        assertNull(shelter.backgroundImageSeq)
        then(images).should().deleteIfUnreferenced(1702)

        given(images.requireOwned(77, 999)).willThrow(BusinessException(ImageErrorCode.IMAGE_NOT_OWNED))
        val e = assertFailsWith<BusinessException> { service.updateMine(77, 7, UpdateShelterCommand(profileImageSeq = Optional.of(999))) }
        assertEquals(ImageErrorCode.IMAGE_NOT_OWNED, e.errorCode)
    }

    @Test
    fun `9_2·9_3 삭제됐거나 없는 보호소는 404`() {
        given(repository.findActive(8)).willReturn(null)
        assertEquals(ShelterErrorCode.SHELTER_NOT_FOUND, assertFailsWith<BusinessException> { service.get(8) }.errorCode)
        assertEquals(ShelterErrorCode.SHELTER_NOT_FOUND, assertFailsWith<BusinessException> { service.get(null) }.errorCode)
    }

    @Test
    fun `9_1 districtCode를 먼저 검증한다`() {
        given(catalog.requireDistrict("99999")).willThrow(BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE))

        assertFailsWith<BusinessException> { service.getPage(null, "99999", PageRequest.of(0, 20)) }
        then(repository).should(never()).findPage(null, "99999", PageRequest.of(0, 20))
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> anyArg(type: Class<T>): T {
            ArgumentMatchers.any(type)
            return null as T
        }
    }
}
