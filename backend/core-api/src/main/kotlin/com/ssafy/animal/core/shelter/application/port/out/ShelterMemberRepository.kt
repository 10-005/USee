package com.ssafy.animal.core.shelter.application.port.out

interface ShelterMemberRepository {
    /** SHELTER 계정의 소속 보호소. 행이 없으면 null */
    fun findShelterSeq(userSeq: Int): Int?
}
