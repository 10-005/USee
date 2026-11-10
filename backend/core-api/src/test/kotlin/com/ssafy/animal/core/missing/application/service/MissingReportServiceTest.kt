package com.ssafy.animal.core.missing.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.missing.application.port.`in`.CreateMissingCommand
import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.application.port.`in`.UpdateMissingCommand
import com.ssafy.animal.core.missing.application.port.out.AuthorRow
import com.ssafy.animal.core.missing.application.port.out.MissingReportRepository
import com.ssafy.animal.core.missing.domain.MissingErrorCode
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingReport
import com.ssafy.animal.core.missing.domain.MissingStatus
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissingReportServiceTest {

    private val reports = mutableMapOf<Int, MissingReport>()
    private val links = mutableMapOf<Int, List<Long>>()

    private val repository = object : MissingReportRepository {
        override fun saveAndFlush(report: MissingReport): MissingReport {
            if (report.missingReportSeq == null) ReflectionTestUtils.setField(report, "missingReportSeq", 100 + reports.size + 1)
            reports[report.missingReportSeq!!] = report
            return report
        }
        override fun findVisible(missingReportSeq: Int) = reports[missingReportSeq]?.takeUnless { it.isDeleted }
        override fun findOwned(userSeq: Int, missingReportSeq: Int) =
            reports[missingReportSeq]?.takeIf { it.userSeq == userSeq && !it.isDeleted }
        override fun search(filter: MissingSearchFilter, pageable: Pageable) =
            PageImpl(reports.values.filterNot { it.isDeleted }, pageable, reports.size.toLong())
        override fun findMine(userSeq: Int) = reports.values.filter { it.userSeq == userSeq && !it.isDeleted }
        override fun findImageSeqs(missingReportSeq: Int) = links[missingReportSeq].orEmpty()
        override fun findRepresentativeImageSeqs(missingReportSeqs: Collection<Int>) =
            missingReportSeqs.mapNotNull { seq -> links[seq]?.firstOrNull()?.let { seq to it } }.toMap()
        override fun replaceImages(missingReportSeq: Int, imageSeqs: List<Long>) {
            links[missingReportSeq] = imageSeqs
        }
        override fun findImageKeys(imageSeqs: List<Long>) = imageSeqs.map { "images/2026/11/$it.jpg" }
        override fun findAuthor(userSeq: Int) = object : AuthorRow {
            override val userSeq = userSeq
            override val nickname = "dev$userSeq"
            override val role = "USER"
            override val profileImageSeq: Long? = null
        }
        override fun isBookmarked(userSeq: Int, missingReportSeq: Int) = userSeq == 2
        override fun countComments(missingReportSeq: Int) = 3L
    }

    private val catalogUseCase = object : CatalogUseCase {
        override fun requireClassification(combinationSeq: Int) =
            findClassifications(listOf(combinationSeq))[combinationSeq]
                ?: throw BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND)
        override fun findClassifications(combinationSeqs: Collection<Int>) = combinationSeqs.filter { it == 88 || it == 90 }
            .associateWith {
                val cat = it == 90
                AnimalClassification(it, if (cat) 2 else 1, if (cat) SpeciesCode.CAT else SpeciesCode.DOG, "종", 3, "품종", 2, "색")
            }
        override fun requireDistrict(districtCode: String) {
            if (districtCode != "47190") throw BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE)
        }
    }

    /** 위도 36 이상만 경북 구미(47190)로 풀리고 나머지는 실패(null) */
    private val geocodeCalls = mutableListOf<Pair<Double, Double>>()
    private val geocodeUseCase = object : GeocodeUseCase {
        override fun search(query: String): List<GeocodeCandidate> = error("not used")
        override fun resolveDistrictCode(latitude: Double, longitude: Double): String? {
            geocodeCalls += latitude to longitude
            return "47190".takeIf { latitude >= 36.0 }
        }
    }

    /** 1000번대는 회원 1, 2000번대는 회원 2가 올린 사진 */
    private val released = mutableListOf<Long>()
    private val imageUseCase = object : ImageUseCase {
        override fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary {
            val owner = when (imageSeq / 1000) { 1L -> 1L; 2L -> 2L; else -> throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND) }
            if (owner != userSeq) throw BusinessException(ImageErrorCode.IMAGE_NOT_OWNED)
            return ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        }
        override fun findSummary(imageSeq: Long) = ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        override fun deleteIfUnreferenced(imageSeq: Long) {
            released += imageSeq
        }
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
    }

    private val events = mutableListOf<Any>()
    private val eventPublisher = object : EventPublisher {
        override fun publish(event: Any) {
            events += event
        }
    }

    private val txManager = object : AbstractPlatformTransactionManager() {
        override fun doGetTransaction() = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private val service = MissingReportService(repository, catalogUseCase, geocodeUseCase, imageUseCase, eventPublisher, txManager)

    private val occurred = OffsetDateTime.parse("2026-10-28T18:00:00+09:00")

    private fun command(imageSeqs: List<Long> = listOf(1001, 1002), latitude: Double = 36.1, combinationSeq: Int = 88) =
        CreateMissingCommand(
            combinationSeq = combinationSeq, gender = MissingGender.MALE, weight = "4.5kg", address = "경북 구미시",
            latitude = latitude, longitude = 128.3, occurredDttm = occurred, contactPhone = "01012345678", imageSeqs = imageSeqs,
        )

    private fun create(userSeq: Long = 1, command: CreateMissingCommand = command()) = service.create(userSeq, command).missingReportSeq

    private fun assertFails(code: ErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `7_4는 역지오코딩한 districtCode로 저장하고 매칭 요청을 발행한다`() {
        val created = service.create(1, command())

        assertEquals(MissingStatus.ACTIVE, created.status)
        assertEquals("47190", created.districtCode)
        assertTrue(created.aiMatchingRequested)
        assertEquals(listOf(1001L, 1002L), links[created.missingReportSeq])
        assertEquals(
            MatchRequested(
                MatchEventType.MISSING_REPORT_REGISTERED, TargetType.MISSING, created.missingReportSeq.toLong(),
                listOf("images/2026/11/1001.jpg", "images/2026/11/1002.jpg"), "DOG", occurred,
            ),
            events.single(),
        )
    }

    @Test
    fun `7_4 역지오코딩이 실패해도 districtCode null로 등록한다`() {
        assertNull(service.create(1, command(latitude = 35.0)).districtCode)
    }

    @Test
    fun `7_4 진행 중 신고가 있어도 회원은 여러 건을 등록하고 각각 매칭을 요청한다(Q505)`() {
        val first = create()
        val second = create()

        assertNotEquals(first, second)
        assertEquals(listOf(MissingStatus.ACTIVE, MissingStatus.ACTIVE), service.getMine(1).map { it.report.status })
        assertEquals(2, events.count { it is MatchRequested })
    }

    @Test
    fun `7_4 사진·조합 검증, 실패하면 이벤트 없음`() {
        create()
        events.clear()

        assertFails(MissingErrorCode.IMAGE_INVALID_COUNT) { create(2, command(emptyList())) }
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { create(2, command(listOf(2001, 2001))) }
        assertFails(ImageErrorCode.IMAGE_NOT_OWNED) { create(2, command(listOf(1001))) }
        assertFails(ImageErrorCode.IMAGE_NOT_FOUND) { create(2, command(listOf(9001))) }
        assertFails(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND) { create(2, command(listOf(2001), combinationSeq = 99)) }
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { create(2, command(listOf(2001), latitude = 91.0)) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `7_1 날짜 역전은 400, 없는 districtCode는 400 CATALOG`() {
        val page = PageRequest.of(0, 20)
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) {
            service.search(MissingSearchFilter(occurredFrom = LocalDate.of(2026, 10, 2), occurredTo = LocalDate.of(2026, 10, 1)), page)
        }
        assertFails(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE) { service.search(MissingSearchFilter(districtCode = "99999"), page) }

        val seq = create()
        val summary = service.search(MissingSearchFilter(districtCode = "47190"), page).content.single()
        assertEquals(seq, summary.report.missingReportSeq)
        assertEquals("https://cdn/1001.jpg", summary.representativeImageUrl)
    }

    @Test
    fun `7_2 비공개 연락처는 작성자에게만, 공개면 로그인 사용자 전원`() {
        val seq = create().toLong()

        assertEquals("01012345678", service.get(1, seq).contactPhone)
        val other = service.get(2, seq)
        assertNull(other.contactPhone)
        assertTrue(other.isBookmarked)
        assertEquals(3, other.commentCount)
        assertEquals("dev1", other.author!!.displayName)

        service.update(1, seq, UpdateMissingCommand(isPhonePublic = true))
        assertEquals("01012345678", service.get(2, seq).contactPhone)
        assertFails(MissingErrorCode.MISSING_REPORT_NOT_FOUND) { service.get(1, 999) }
    }

    @Test
    fun `7_5 사진 집합이 바뀌면 재매칭, 순서만 바뀌면 재매칭 없음`() {
        val seq = create().toLong()
        events.clear()

        service.update(1, seq, UpdateMissingCommand(imageSeqs = listOf(1002, 1001)))
        assertTrue(events.isEmpty())
        assertEquals(listOf(1002L, 1001L), links[seq.toInt()])

        val detail = service.update(1, seq, UpdateMissingCommand(combinationSeq = 90, imageSeqs = listOf(1003, 1001)))
        val event = events.single() as MatchRequested
        assertEquals(MatchEventType.IMAGES_CHANGED, event.eventType)
        assertEquals("CAT", event.speciesCode)
        assertEquals(listOf(1002L), released)
        assertEquals(listOf(1003L, 1001L), detail.images.map { it.imageSeq })
        assertEquals(1, reports[seq.toInt()]!!.modUsrSeq)
    }

    @Test
    fun `7_5 좌표를 바꾸면 districtCode를 다시 구하고, 선택 필드 명시적 null은 지운다`() {
        val seq = create().toLong()
        geocodeCalls.clear()

        service.update(1, seq, UpdateMissingCommand(weight = Optional.empty(), latitude = 35.0, longitude = 128.0))

        val report = reports[seq.toInt()]!!
        assertEquals(listOf(35.0 to 128.0), geocodeCalls)
        assertNull(report.districtCode)
        assertNull(report.weight)
        assertEquals(35.0, report.latitude)
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { service.update(1, seq, UpdateMissingCommand(latitude = 36.0)) }
    }

    @Test
    fun `7_5 CLOSED는 409, 타인 신고는 404`() {
        val seq = create().toLong()
        service.changeStatus(1, seq, MissingStatus.CLOSED)

        assertFails(MissingErrorCode.MISSING_REPORT_CLOSED) { service.update(1, seq, UpdateMissingCommand(age = Optional.of("4살"))) }
        assertFails(MissingErrorCode.MISSING_REPORT_NOT_FOUND) { service.update(2, seq, UpdateMissingCommand(age = Optional.of("4살"))) }
    }

    @Test
    fun `7_6 같은 상태는 400, 다른 ACTIVE가 있어도 재개하고 매칭을 요청한다`() {
        val first = create().toLong()
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { service.changeStatus(1, first, MissingStatus.ACTIVE) }
        assertEquals(MissingStatus.CLOSED, service.changeStatus(1, first, MissingStatus.CLOSED).status)
        val second = create().toLong()

        assertFails(MissingErrorCode.MISSING_REPORT_NOT_FOUND) { service.changeStatus(2, second, MissingStatus.CLOSED) }

        events.clear()
        service.changeStatus(1, first, MissingStatus.ACTIVE)
        assertEquals(MatchEventType.MISSING_REPORT_REGISTERED, (events.single() as MatchRequested).eventType)
        assertEquals(setOf(MissingStatus.ACTIVE), service.getMine(1).map { it.report.status }.toSet())
    }

    @Test
    fun `7_7 소프트 삭제 후 사진 정리, 이후 조회 404`() {
        val seq = create().toLong()

        service.delete(1, seq)

        assertTrue(reports[seq.toInt()]!!.isDeleted)
        assertEquals(listOf(1001L, 1002L), released)
        assertFails(MissingErrorCode.MISSING_REPORT_NOT_FOUND) { service.get(1, seq) }
        assertFails(MissingErrorCode.MISSING_REPORT_NOT_FOUND) { service.delete(1, seq) }
        assertFalse(service.getMine(1).any { it.report.missingReportSeq == seq.toInt() })
    }
}
