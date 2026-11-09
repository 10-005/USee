package com.ssafy.animal.core.pet.application.port.out

import com.ssafy.animal.core.pet.domain.Pet
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PetRepository {
    /** 바로 반영한다(INSERT면 petSeq, UPDATE면 modDttm이 채워진다) */
    fun saveAndFlush(pet: Pet): Pet

    /** 비삭제·본인 소유만 */
    fun findOwned(userSeq: Int, petSeq: Int): Pet?

    /** 6.1 정렬 화이트리스트 regDttm·name, 그 밖은 400 COMMON_INVALID_INPUT */
    fun findPage(userSeq: Int, pageable: Pageable): Page<Pet>

    /** display_order 순 imageSeq */
    fun findImageSeqs(petSeq: Int): List<Long>

    /** petSeq → 대표(display_order 1) imageSeq */
    fun findRepresentativeImageSeqs(petSeqs: Collection<Int>): Map<Int, Long>

    /** 연결을 지우고 주어진 순서대로 display_order 1..N으로 다시 넣는다(빈 목록이면 지우기만). DB에 바로 반영된다 */
    fun replaceImages(petSeq: Int, imageSeqs: List<Long>)
}
