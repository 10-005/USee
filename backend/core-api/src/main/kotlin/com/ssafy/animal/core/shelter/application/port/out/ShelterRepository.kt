package com.ssafy.animal.core.shelter.application.port.out

import com.ssafy.animal.core.shelter.domain.Shelter
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ShelterRepository {
    /** 삭제되지 않은 보호소. 없으면 null */
    fun findActive(shelterSeq: Int): Shelter?

    /** 9.1. keyword는 이름·주소 ILIKE, 정렬 화이트리스트 shelterName·regDttm(기본 shelterName asc) */
    fun findPage(keyword: String?, districtCode: String?, pageable: Pageable): Page<Shelter>

    fun saveAndFlush(shelter: Shelter): Shelter
}
