package com.ssafy.animal.core.adoption.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.adoption.application.port.`in`.FormInput
import com.ssafy.animal.core.adoption.application.port.out.SimulationReportReader
import com.ssafy.animal.core.adoption.application.port.out.SimulationReportRow
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.animal.AnimalTestWorld
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicantSimulationReportServiceTest {

    private val world = AnimalTestWorld()
    private val start = OffsetDateTime.parse("2026-11-01T14:00:00+09:00")

    /** 회원별 리포트 행을 돌려주는 가짜(실제 어댑터는 simulation 스키마를 최근 종료 순으로 JOIN) */
    private val rowsByUser = mutableMapOf<Int, List<SimulationReportRow>>()
    private val reader = object : SimulationReportReader {
        override fun findEndedReports(userSeq: Int) = rowsByUser[userSeq].orEmpty()
    }
    private val service = ApplicantSimulationReportService(world.applicationRepository, reader, world.imageUseCase)

    private fun row(sessionSeq: Long, imageSeq: Long?, species: String = "DOG") = SimulationReportRow(
        sessionSeq = sessionSeq, animalName = "초코", breedSeq = 21, breedName = "말티즈", speciesCode = species,
        representativeImageSeq = imageSeq, startedDttm = start, endedDttm = start.plusDays(7), totalSpent = 182_000,
    )

    @Test
    fun `소속 보호소의 제출된 신청서면 신청자의 종료 리포트를 동물 정보·기간·총지출로 준다`() {
        val applicationSeq = world.submitted(42, world.animal().animalSeq!!)
        world.imageOwners[1701] = 77
        rowsByUser[42] = listOf(row(3002, 1701), row(3001, null, species = "CAT"))

        val reports = service.getApplicantReports(world.shelterA, applicationSeq)

        assertEquals(listOf(3002L, 3001L), reports.map { it.sessionSeq })
        val first = reports.first()
        assertEquals("초코", first.animalName)
        assertEquals(21, first.breed.breedSeq)
        assertEquals("말티즈", first.breed.breedName)
        assertEquals(SpeciesCode.DOG, first.breed.speciesType)
        assertEquals("https://cdn/1701.jpg", first.breed.representativeImageUrl)
        assertEquals(start, first.startedDttm)
        assertEquals(start.plusDays(7), first.endedDttm)
        assertEquals(182_000, first.totalSpent)
        assertEquals(SpeciesCode.CAT, reports.last().breed.speciesType)
        assertNull(reports.last().breed.representativeImageUrl)
    }

    @Test
    fun `리포트가 없으면 빈 목록`() {
        val applicationSeq = world.submitted(42, world.animal().animalSeq!!)

        assertTrue(service.getApplicantReports(world.shelterA, applicationSeq).isEmpty())
    }

    @Test
    fun `다른 보호소·DRAFT·없는 신청서는 404`() {
        val submitted = world.submitted(42, world.animal().animalSeq!!)
        val draft = world.adoptionService.createDraft(43, world.animal().animalSeq!!, FormInput()).applicationSeq
        val otherShelter = AuthUser(88, Role.SHELTER, 8)

        listOf(otherShelter to submitted, world.shelterA to draft, world.shelterA to 99_999L).forEach { (shelter, seq) ->
            val error = assertFailsWith<BusinessException> { service.getApplicantReports(shelter, seq) }
            assertEquals(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND, error.errorCode)
        }
    }
}
