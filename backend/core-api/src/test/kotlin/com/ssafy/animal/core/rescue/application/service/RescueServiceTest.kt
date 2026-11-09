package com.ssafy.animal.core.rescue.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.rescue.application.port.`in`.CreateRescueCommand
import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.application.port.`in`.UpdateRescueCommand
import com.ssafy.animal.core.rescue.application.port.out.AnimalRow
import com.ssafy.animal.core.rescue.application.port.out.FoundReportRow
import com.ssafy.animal.core.rescue.application.port.out.RescueRepository
import com.ssafy.animal.core.rescue.domain.RescueContent
import com.ssafy.animal.core.rescue.domain.RescueErrorCode
import com.ssafy.animal.core.rescue.domain.RescueReasonCode
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RescueServiceTest {

    private val repository = mock(RescueRepository::class.java)
    private val catalog = mock(CatalogUseCase::class.java)
    private val images = mock(ImageUseCase::class.java)
    private val service = RescueService(repository, catalog, images)

    private val shelterUser = AuthUser(77, Role.SHELTER, 7)
    private val content = RescueContent(RescueReasonCode.WANDERING, "구미시 인동동", null, OffsetDateTime.parse("2026-10-23T17:47:00+09:00"), null)

    init {
        given(repository.saveAndFlush(anyArg(RescueRecord::class.java))).willAnswer { it.arguments[0] }
    }

    private fun rescue(seq: Int = 301, shelterSeq: Int = 7, foundReportSeq: Int? = 210, status: RescueStatus = RescueStatus.PROCESSING) =
        RescueRecord(shelterSeq, foundReportSeq, content, 77).also {
            ReflectionTestUtils.setField(it, "rescueSeq", seq)
            if (status != RescueStatus.PROCESSING) it.complete(status, "사유", OffsetDateTime.now(), 77)
            given(repository.findActive(seq)).willReturn(it)
        }

    @Test
    fun `10_3 토큰 보호소로 PROCESSING 생성, 삭제된 신고는 404, 진행 중 구조가 있으면 409`() {
        given(repository.existsActiveFoundReport(210)).willReturn(true)
        val created = service.create(shelterUser, CreateRescueCommand(210, content))
        assertEquals(7, created.shelterSeq)
        assertEquals(RescueStatus.PROCESSING, created.status)
        assertEquals(77, created.regUsrSeq)

        given(repository.existsOngoingRescue(210)).willReturn(true)
        assertEquals(
            RescueErrorCode.RESCUE_ACTIVE_ALREADY_EXISTS,
            assertFailsWith<BusinessException> { service.create(shelterUser, CreateRescueCommand(210, content)) }.errorCode,
        )
        assertEquals(
            FoundErrorCode.FOUND_REPORT_NOT_FOUND,
            assertFailsWith<BusinessException> { service.create(shelterUser, CreateRescueCommand(211, content)) }.errorCode,
        )
        // 직접 접수는 신고 검사 없이 만든다
        assertNull(service.create(shelterUser, CreateRescueCommand(null, content)).foundReportSeq)
    }

    @Test
    fun `10_2 다른 보호소의 구조는 404, 발견 신고 요약의 탈퇴 신고자는 표시 규칙을 따른다`() {
        rescue(seq = 302, shelterSeq = 8)
        assertEquals(RescueErrorCode.RESCUE_NOT_FOUND, assertFailsWith<BusinessException> { service.get(7, 302) }.errorCode)

        rescue()
        given(repository.findFoundReportSummary(210)).willReturn(
            FoundReportRow(
                210, AnimalRow(88, "UNKNOWN", "0살", null, "약 3kg", null), "구미시 인동동",
                OffsetDateTime.parse("2026-10-19T18:30:00+09:00"), "홍길동", true, "01012341234", "CLOSED",
            ),
        )
        val detail = service.get(7, 301)
        assertEquals("(탈퇴한 사용자)", detail.foundReportSummary!!.reporter.name)
        assertEquals("01012341234", detail.foundReportSummary.reporter.phone)
        assertEquals("CLOSED", detail.foundReportSummary.status)
    }

    @Test
    fun `10_5 PROCESSING에서만 종료, FAILED·CANCELED는 사유 필수, DONE은 사유를 비운다`() {
        rescue(seq = 303, status = RescueStatus.DONE)
        assertEquals(
            RescueErrorCode.RESCUE_INVALID_TRANSITION,
            assertFailsWith<BusinessException> { service.changeStatus(shelterUser, 303, RescueStatus.FAILED, "x") }.errorCode,
        )
        rescue()
        assertEquals(
            RescueErrorCode.RESCUE_INVALID_TRANSITION,
            assertFailsWith<BusinessException> { service.changeStatus(shelterUser, 301, RescueStatus.PROCESSING, null) }.errorCode,
        )
        assertEquals(
            RescueErrorCode.RESCUE_RESULT_REASON_REQUIRED,
            assertFailsWith<BusinessException> { service.changeStatus(shelterUser, 301, RescueStatus.CANCELED, "  ") }.errorCode,
        )
        val done = service.changeStatus(shelterUser, 301, RescueStatus.DONE, "무시됨")
        assertEquals(RescueStatus.DONE, done.status)
        assertNull(done.resultReason)
        assertNotNull(done.completedDttm)
    }

    @Test
    fun `10_4 종료 상태는 메모만 고칠 수 있고 필수 필드는 비울 수 없다`() {
        rescue(seq = 303, status = RescueStatus.FAILED)
        val e = assertFailsWith<BusinessException> {
            service.update(shelterUser, 303, UpdateRescueCommand(rescueAddress = Optional.of("다른 곳"), rescueMemo = Optional.of("메모")))
        }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, e.errorCode)
        assertEquals("메모", service.update(shelterUser, 303, UpdateRescueCommand(rescueMemo = Optional.of("메모"))).rescue.rescueMemo)

        rescue()
        val cleared = assertFailsWith<BusinessException> { service.update(shelterUser, 301, UpdateRescueCommand(rescueAddress = Optional.of(" "))) }
        assertEquals("rescueAddress", cleared.errors.single().field)
        val updated = service.update(
            shelterUser, 301,
            UpdateRescueCommand(rescueReasonCode = Optional.of(RescueReasonCode.INJURY_ILLNESS), rescueAddressDetail = Optional.of("도서관 옆")),
        ).rescue
        assertEquals(RescueReasonCode.INJURY_ILLNESS, updated.rescueReasonCode)
        assertEquals("도서관 옆", updated.rescueAddressDetail)
        assertEquals(77, updated.modUsrSeq)
    }

    @Test
    fun `10_6 연결 보호동물이 있으면 409, 없으면 논리 삭제`() {
        val target = rescue()
        given(repository.countLinkedAnimals(301)).willReturn(2)
        assertEquals(RescueErrorCode.RESCUE_HAS_ANIMALS, assertFailsWith<BusinessException> { service.delete(shelterUser, 301) }.errorCode)

        given(repository.countLinkedAnimals(301)).willReturn(0)
        service.delete(shelterUser, 301)
        assert(target.isDeleted)
    }

    @Test
    fun `10_1 동물 정보는 REPORT는 발견 신고, DIRECT는 첫 보호동물에서, 건수는 없는 상태도 0으로 채운다`() {
        val fromReport = rescue()
        val direct = rescue(seq = 302, foundReportSeq = null, status = RescueStatus.DONE)
        val empty = rescue(seq = 303, foundReportSeq = null)
        val pageable = PageRequest.of(0, 8)
        given(repository.findPage(7, RescueFilter(), pageable)).willReturn(PageImpl(listOf(fromReport, direct, empty), pageable, 3))
        given(repository.findFoundReportAnimals(listOf(210))).willReturn(mapOf(210 to AnimalRow(88, "UNKNOWN", "0살", null, "약 3kg", null)))
        given(repository.findFirstShelterAnimals(listOf(302, 303))).willReturn(mapOf(302 to AnimalRow(89, "MALE", "2살", true, "5kg", null)))
        given(catalog.findClassifications(listOf(88, 89))).willReturn(
            mapOf(
                88 to AnimalClassification(88, 2, SpeciesCode.CAT, "고양이", 31, "코리안숏헤어", 3, "흰색"),
                89 to AnimalClassification(89, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색"),
            ),
        )
        given(repository.countByStatus(7, RescueFilter())).willReturn(mapOf(RescueStatus.PROCESSING to 2L, RescueStatus.DONE to 1L))

        val result = service.getPage(7, RescueFilter(), pageable)

        assertEquals(listOf("코리안숏헤어", "말티즈", null), result.page.content.map { it.animal?.breedName })
        assertEquals(mapOf(RescueStatus.PROCESSING to 2L, RescueStatus.DONE to 1L, RescueStatus.FAILED to 0L, RescueStatus.CANCELED to 0L), result.statusCounts)
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> anyArg(type: Class<T>): T {
            ArgumentMatchers.any(type)
            return null as T
        }
    }
}
