package com.ssafy.animal.core.shelter.application.port.`in`

import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.shelter.domain.Shelter
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.Optional

interface ShelterUseCase {
    /** 9.1 목록. districtCode가 districts에 없으면 400 CATALOG_INVALID_DISTRICT_CODE */
    fun getPage(keyword: String?, districtCode: String?, pageable: Pageable): Page<ShelterView>

    /** 9.2·9.3 상세. 없거나 삭제면 404 SHELTER_NOT_FOUND */
    fun get(shelterSeq: Long?): ShelterView

    /** 9.4 소속 보호소 운영정보·이미지 부분 수정 */
    fun updateMine(userSeq: Long, shelterSeq: Long?, command: UpdateShelterCommand): ShelterView
}

/** 필드별 null = 미전달(유지), Optional.empty() = 명시적 null(비움·이미지 연결 해제) */
data class UpdateShelterCommand(
    val description: Optional<String>? = null,
    val operatingHours: Optional<String>? = null,
    val closedDays: Optional<String>? = null,
    val profileImageSeq: Optional<Long>? = null,
    val backgroundImageSeq: Optional<Long>? = null,
)

data class ShelterView(
    val shelter: Shelter,
    val profileImage: ImageSummary?,
    val backgroundImage: ImageSummary?,
)
