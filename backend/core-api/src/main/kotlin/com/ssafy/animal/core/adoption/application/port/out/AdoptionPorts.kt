package com.ssafy.animal.core.adoption.application.port.out

import com.ssafy.animal.core.adoption.domain.AdoptionApplication
import com.ssafy.animal.core.adoption.domain.AdoptionFormQuestion
import com.ssafy.animal.core.adoption.domain.Applicant
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

interface AdoptionApplicationRepository {
    /** INSERT·UPDATE를 바로 반영. 활성 1건 부분 UNIQUE에 걸리면(경합) 409 ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS */
    fun saveAndFlush(application: AdoptionApplication): AdoptionApplication

    /** 비삭제만 */
    fun find(applicationSeq: Long): AdoptionApplication?

    /** 비삭제만, FOR UPDATE */
    fun findForUpdate(applicationSeq: Long): AdoptionApplication?

    /** 잠금 순서(동물 → 신청서)를 지키려고 신청서를 잠그기 전에 동물 번호만 읽는다 */
    fun findAnimalSeq(applicationSeq: Long): Int?

    /** 회원·동물의 활성 신청(최대 1건), FOR UPDATE */
    fun findActiveForUpdate(userSeq: Int, animalSeq: Int): AdoptionApplication?

    /** 동물의 활성 신청 전부, FOR UPDATE */
    fun findActiveByAnimalForUpdate(animalSeq: Int): List<AdoptionApplication>

    fun existsActiveByAnimal(animalSeq: Int): Boolean

    /** 12.7 물리 삭제 */
    fun delete(application: AdoptionApplication)

    /** 12.2: displayDttm desc 고정 */
    fun findMine(userSeq: Int, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<AdoptionApplication>

    /** 12.9: 보호소에 보이는 신청만(제출된 적 있는 SUBMITTED/APPROVED/ADOPTED/REJECTED) */
    fun findReceived(shelterSeq: Int, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<AdoptionApplication>

    /** 12.16·11.10: findReceived와 같은 범위의 상태별 건수. shelterSeq·animalSeq는 null이면 조건 없음 */
    fun countReceived(shelterSeq: Int?, animalSeq: Int?): Map<ApplicationStatus, Long>

    /** 2.6 탈퇴: 활성 신청 일괄 CANCELED. 처리 건수 */
    fun cancelActiveOfUser(userSeq: Int, now: OffsetDateTime): Int
}

interface AdoptionQuestionRepository {
    /** displayOrder 순 */
    fun findAll(): List<AdoptionFormQuestion>
}

/** 다른 도메인 테이블 읽기(native) */
interface AdoptionReferenceReader {
    /** 현재 users의 실명·생년월일·전화(DRAFT 상세, 제출 스냅샷) */
    fun findApplicant(userSeq: Int): Applicant?

    /** 보호소 담당 SHELTER 계정(ADOPTION_SUBMITTED 수신자). 보호소당 1개 */
    fun findShelterUserSeqs(shelterSeq: Int): List<Int>
}
