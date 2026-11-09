package com.ssafy.animal.core.found.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.AssignmentMethod
import com.ssafy.animal.core.found.application.port.`in`.RegisterFoundReportCommand
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.application.port.`in`.UpdateFoundReportCommand
import com.ssafy.animal.core.found.application.port.out.FoundReportRepository
import com.ssafy.animal.core.found.application.port.out.ReporterRow
import com.ssafy.animal.core.found.application.port.out.RescueRow
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.found.domain.FoundImageErrorCode
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundReportContent
import com.ssafy.animal.core.found.domain.FoundReportImage
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.found.domain.Gender
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.domain.ImageErrorCode
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionOperations
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FoundReportServiceTest {

    private val repository = mock(FoundReportRepository::class.java)
    private val catalog = mock(CatalogUseCase::class.java)
    private val geocode = mock(GeocodeUseCase::class.java)
    private val images = mock(ImageUseCase::class.java)
    private val events = mutableListOf<Any>()
    private val publisher = object : EventPublisher {
        override fun publish(event: Any) {
            events += event
        }
    }
    // Mockito는 Int? 반환에 0을 돌려주므로 "없음"을 확인하는 테스트는 null을 직접 지정한다
    private val service = FoundReportService(repository, catalog, geocode, images, publisher, TransactionOperations.withoutTransaction())

    private val author = AuthUser(42, Role.USER, null)
    private val assignedShelter = AuthUser(77, Role.SHELTER, 7)
    private val content = FoundReportContent(
        88, Gender.UNKNOWN, null, null, null, null, "빨간 목줄", "01012345678", false,
        null, "경상북도 구미시 인동동", 36.1, 128.4, OffsetDateTime.parse("2026-10-29T10:00:00+09:00"),
    )
    private val classification = AnimalClassification(88, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색")

    init {
        given(catalog.requireClassification(88)).willReturn(classification)
        given(images.requireOwned(42, 1201)).willReturn(ImageSummary(1201, "https://cdn/1201.jpg"))
        given(repository.saveAndFlush(anyArg(FoundReport::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as FoundReport).also { if (it.foundReportSeq == null) ReflectionTestUtils.setField(it, "foundReportSeq", 210) }
        }
        given(repository.findImageKeys(listOf(1201))).willReturn(listOf("images/2026/10/a.jpg"))
    }

    private fun report(status: FoundStatus = FoundStatus.ACTIVE) = FoundReport(42, 7, content, "47190").also {
        ReflectionTestUtils.setField(it, "foundReportSeq", 210)
        if (status == FoundStatus.CLOSED) it.changeStatus(FoundStatus.CLOSED, null, OffsetDateTime.now(), 42)
    }

    @Test
    fun `8_4 시군구 배정표가 있으면 DISTRICT로 배정하고 커밋 후 알림·매칭 이벤트를 낸다`() {
        given(geocode.resolveDistrictCode(36.1, 128.4)).willReturn("47190")
        given(repository.findDistrictShelterSeq("47190")).willReturn(1)
        given(repository.findShelterUserSeq(1)).willReturn(501)

        val result = service.register(42, RegisterFoundReportCommand(content, listOf(1201)))

        assertEquals(AssignmentMethod.DISTRICT, result.assignmentMethod)
        assertEquals(1, result.assignedShelterSeq)
        assertEquals("47190", result.districtCode)
        then(repository).should().replaceImages(210, listOf(1201))
        then(repository).should(never()).findNearestShelterSeq(36.1, 128.4)
        assertEquals(
            listOf(
                NotificationRequested(501, NotificationType.SHELTER, "담당 지역에 새 발견 신고가 등록되었습니다.", TargetType.FOUND, 210),
                MatchRequested(MatchEventType.FOUND_REPORT_REGISTERED, TargetType.FOUND, 210, listOf("images/2026/10/a.jpg"), "DOG", content.occurredDttm),
            ),
            events,
        )
    }

    @Test
    fun `8_4 역지오코딩 실패면 districtCode null로 최근접 보호소에 배정한다(Q406-2)`() {
        given(geocode.resolveDistrictCode(36.1, 128.4)).willReturn(null)
        given(repository.findNearestShelterSeq(36.1, 128.4)).willReturn(3)
        given(repository.findShelterUserSeq(3)).willReturn(null)

        val result = service.register(42, RegisterFoundReportCommand(content, listOf(1201)))

        assertEquals(AssignmentMethod.NEAREST, result.assignmentMethod)
        assertEquals(3, result.assignedShelterSeq)
        assertNull(result.districtCode)
        // 담당 계정이 없으면 알림은 건너뛰고 매칭 이벤트만
        assertEquals(listOf(MatchRequested::class), events.map { it::class })
    }

    @Test
    fun `8_4 배정표에 없는 시군구도 최근접, 보호소가 하나도 없으면 409`() {
        given(geocode.resolveDistrictCode(36.1, 128.4)).willReturn("11110")
        given(repository.findDistrictShelterSeq("11110")).willReturn(null)
        given(repository.findNearestShelterSeq(36.1, 128.4)).willReturn(null)

        val e = assertFailsWith<BusinessException> { service.register(42, RegisterFoundReportCommand(content, listOf(1201))) }

        assertEquals(FoundErrorCode.FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE, e.errorCode)
        then(repository).should().findNearestShelterSeq(36.1, 128.4)
        assert(events.isEmpty())
    }

    @Test
    fun `8_4 사진은 1~3장, 남의 사진은 403`() {
        listOf(emptyList(), listOf(1L, 2L, 3L, 4L)).forEach {
            val e = assertFailsWith<BusinessException> { service.register(42, RegisterFoundReportCommand(content, it)) }
            assertEquals(FoundImageErrorCode.IMAGE_INVALID_COUNT, e.errorCode)
        }
        given(images.requireOwned(42, 999)).willThrow(BusinessException(ImageErrorCode.IMAGE_NOT_OWNED))
        val e = assertFailsWith<BusinessException> { service.register(42, RegisterFoundReportCommand(content, listOf(999))) }
        assertEquals(ImageErrorCode.IMAGE_NOT_OWNED, e.errorCode)
        then(geocode).shouldHaveNoInteractions()
    }

    @Test
    fun `8_5 CLOSED는 409, 남의 신고는 404`() {
        given(repository.findActive(210)).willReturn(report(FoundStatus.CLOSED))
        val closed = assertFailsWith<BusinessException> { service.update(author, 210, UpdateFoundReportCommand(age = Optional.of("2살"))) }
        assertEquals(FoundErrorCode.FOUND_REPORT_CLOSED, closed.errorCode)

        given(repository.findActive(210)).willReturn(report())
        val other = assertFailsWith<BusinessException> { service.update(AuthUser(43, Role.USER, null), 210, UpdateFoundReportCommand()) }
        assertEquals(FoundErrorCode.FOUND_REPORT_NOT_FOUND, other.errorCode)
    }

    @Test
    fun `8_5 필수 필드를 비우면 400, 사진이 바뀌면 빠진 사진을 정리하고 재매칭 이벤트를 낸다`() {
        val current = report()
        given(repository.findActive(210)).willReturn(current)
        val cleared = assertFailsWith<BusinessException> { service.update(author, 210, UpdateFoundReportCommand(reporterPhone = Optional.empty())) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, cleared.errorCode)

        given(repository.findImages(listOf(210))).willReturn(mapOf(210 to listOf(FoundReportImage(210, 1100, 0))))
        given(repository.findReporters(listOf(42))).willReturn(mapOf(42 to ReporterRow(42, "발견도우미", "김가람", null, false)))

        val detail = service.update(author, 210, UpdateFoundReportCommand(weight = Optional.of("약 3kg"), imageSeqs = Optional.of(listOf(1201L))))

        assertEquals("약 3kg", detail.item.report.weight)
        then(repository).should().replaceImages(210, listOf(1201))
        then(images).should().deleteIfUnreferenced(1100)
        // 위치를 안 바꿨으니 역지오코딩도 하지 않는다
        then(geocode).shouldHaveNoInteractions()
        assertEquals(MatchEventType.IMAGES_CHANGED, (events.single() as MatchRequested).eventType)
    }

    @Test
    fun `8_5 좌표를 바꾸면 districtCode를 다시 구하지만 담당 보호소는 그대로다`() {
        given(repository.findActive(210)).willReturn(report())
        given(geocode.resolveDistrictCode(36.2, 128.4)).willReturn("47850")

        val detail = service.update(author, 210, UpdateFoundReportCommand(latitude = Optional.of(36.2)))

        assertEquals("47850", detail.item.report.districtCode)
        assertEquals(7, detail.item.report.shelterSeq)
    }

    @Test
    fun `8_6 작성자는 종료·재개, 담당 보호소는 종료만, 그 외는 404, 같은 상태는 400`() {
        given(repository.findActive(210)).willReturn(report())
        val closed = service.changeStatus(assignedShelter, 210, FoundStatus.CLOSED, " 보호자가 직접 찾아감 ")
        assertEquals(FoundStatus.CLOSED, closed.status)
        assertEquals("보호자가 직접 찾아감", closed.closedReason)

        given(repository.findActive(210)).willReturn(report(FoundStatus.CLOSED))
        val forbidden = assertFailsWith<BusinessException> { service.changeStatus(assignedShelter, 210, FoundStatus.ACTIVE, null) }
        assertEquals(CommonErrorCode.AUTH_FORBIDDEN, forbidden.errorCode)

        val reopened = service.changeStatus(author, 210, FoundStatus.ACTIVE, null)
        assertEquals(FoundStatus.ACTIVE, reopened.status)
        assertNull(reopened.closedDttm)

        val otherShelter = assertFailsWith<BusinessException> { service.changeStatus(AuthUser(78, Role.SHELTER, 8), 210, FoundStatus.CLOSED, null) }
        assertEquals(FoundErrorCode.FOUND_REPORT_NOT_FOUND, otherShelter.errorCode)

        given(repository.findActive(210)).willReturn(report())
        val same = assertFailsWith<BusinessException> { service.changeStatus(author, 210, FoundStatus.ACTIVE, null) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, same.errorCode)
    }

    @Test
    fun `8_7 작성자만 논리 삭제한다`() {
        val target = report()
        given(repository.findActive(210)).willReturn(target)

        assertFailsWith<BusinessException> { service.delete(43, 210) }
        service.delete(42, 210)

        assert(target.isDeleted)
    }

    @Test
    fun `8_2 비공개 연락처는 작성자·담당 보호소에만 보인다`() {
        given(repository.findVisible(210)).willReturn(report())

        assertEquals("01012345678", service.get(author, 210).reporterPhone)
        assertEquals("01012345678", service.get(assignedShelter, 210).reporterPhone)
        assertNull(service.get(AuthUser(43, Role.USER, null), 210).reporterPhone)
        assertNull(service.get(AuthUser(78, Role.SHELTER, 8), 210).reporterPhone)
    }

    @Test
    fun `8_8 최신 구조 상태와 진행 중 구조 번호, 탈퇴 신고자 이름을 채운다`() {
        val withdrawn = FoundReport(50, 7, content, null).also { ReflectionTestUtils.setField(it, "foundReportSeq", 211) }
        val pageable = PageRequest.of(0, 8)
        given(repository.findAssigned(7, AssignedFilter(), pageable)).willReturn(PageImpl(listOf(report(), withdrawn), pageable, 2))
        given(repository.findReporters(listOf(42, 50))).willReturn(
            mapOf(42 to ReporterRow(42, "발견도우미", "김가람", null, false), 50 to ReporterRow(50, "탈퇴자", "이름", null, true)),
        )
        given(repository.findLatestRescues(listOf(210, 211))).willReturn(mapOf(210 to RescueRow(301, "DONE"), 211 to RescueRow(302, "CANCELED")))

        val items = service.getAssigned(7, AssignedFilter(), pageable).content

        assertEquals(RescueProgress.DONE, items[0].rescueStatus)
        assertEquals(301, items[0].activeRescueSeq)
        assertEquals("김가람", items[0].reporter.name)
        assertEquals(RescueProgress.ABORTED, items[1].rescueStatus)
        assertNull(items[1].activeRescueSeq)
        assertEquals("(탈퇴한 사용자)", items[1].reporter.name)
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> anyArg(type: Class<T>): T {
            ArgumentMatchers.any(type)
            return null as T
        }
    }
}
