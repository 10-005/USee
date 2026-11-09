package com.ssafy.animal.core.animal.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.animal.application.port.`in`.AnimalCard
import com.ssafy.animal.core.animal.application.port.`in`.AnimalDetail
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.ClassificationBrief
import com.ssafy.animal.core.animal.application.port.`in`.ClassificationFull
import com.ssafy.animal.core.animal.application.port.`in`.ImageRef
import com.ssafy.animal.core.animal.application.port.`in`.ManageAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.ManagedAnimal
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalPage
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.RegisterAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.RegisteredAnimal
import com.ssafy.animal.core.animal.application.port.`in`.UpdateAnimalCommand
import com.ssafy.animal.core.animal.domain.AnimalBasics
import com.ssafy.animal.core.animal.application.port.`in`.RegistrationPeriod
import com.ssafy.animal.core.animal.application.port.`in`.RescueInfo
import com.ssafy.animal.core.animal.application.port.`in`.ReturnInfo
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalDetail
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalUseCase
import com.ssafy.animal.core.animal.application.port.`in`.ShelterBrief
import com.ssafy.animal.core.animal.application.port.`in`.ShelterContact
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalErrorCode
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.EndReason
import com.ssafy.animal.core.animal.domain.Gender
import com.ssafy.animal.core.animal.domain.ProtectionStatus
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date
import java.util.Optional

@WebMvcTest(ShelterAnimalController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ShelterAnimalControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var useCase: ShelterAnimalUseCase

    private val start = LocalDate.of(2026, 10, 29)
    private val end = LocalDate.of(2026, 11, 8)
    private val classification = ClassificationFull(88, 1, "개", 21, "말티즈", 3, "흰색")

    private val card = AnimalCard(
        700, ClassificationBrief(88, "개", "말티즈", "흰색"), Gender.UNKNOWN, "1살 추정", null, "3kg",
        ProtectionStatus.PROTECTING, AdoptionStatus.UNAVAILABLE, start, end, ShelterBrief(7, "대구동물보호센터", "27110"),
        "https://cdn/1701.jpg", OffsetDateTime.parse("2026-10-29T12:00:00+09:00"),
    )

    private val shelterDetail = ShelterAnimalDetail(
        700, classification, Gender.UNKNOWN, "1살 추정", null, "3kg", null, null, "사람을 잘 따름", null, null,
        ProtectionStatus.PROTECTING, AdoptionStatus.AVAILABLE, start, end, null, null, null, RescueInfo.EMPTY,
        listOf(ImageRef(1701, "https://cdn/1701.jpg", 1)), null, null,
        mapOf("total" to 0L, "SUBMITTED" to 0L, "APPROVED" to 0L, "ADOPTED" to 0L, "REJECTED" to 0L),
        OffsetDateTime.parse("2026-10-29T12:00:00+09:00"), null,
    )

    @Test
    fun `11_1은 필터를 넘기고 Page로 감싼다, 허용값 밖 status는 400`() {
        val condition = AnimalSearchCondition(status = AnimalStatus.PROTECTING_AVAILABLE, regFrom = LocalDate.of(2026, 10, 1), gender = Gender.MALE, isNeutered = true)
        given(useCase.search(condition, PageRequest.of(0, 20))).willReturn(PageImpl(listOf(card), PageRequest.of(0, 20), 1))

        mockMvc.get("/api/core/v1/shelter-animals?status=PROTECTING_AVAILABLE&regFrom=2026-10-01&gender=MALE&isNeutered=true") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].animalSeq") { value(700) }
            jsonPath("$.data.content[0].shelter.districtCode") { value("27110") }
            jsonPath("$.data.content[0].classification.breedName") { value("말티즈") }
            jsonPath("$.data.content[0].noticeEndDate") { value("2026-11-08") }
            jsonPath("$.data.size") { value(20) }
        }
        mockMvc.get("/api/core/v1/shelter-animals?status=PROTECTING") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `11_2는 USER만, SHELTER는 403 AUTH_FORBIDDEN, 없으면 404`() {
        val detail = AnimalDetail(
            700, classification, Gender.UNKNOWN, null, null, null, ProtectionStatus.PROTECTING, AdoptionStatus.AVAILABLE, start, end,
            ShelterContact(7, "대구동물보호센터", "0531234567", "대구광역시"), null, null, null, null, null, null, null, null, null, null, null,
            RescueInfo.EMPTY, emptyList(), true,
        )
        given(useCase.getForUser(42, 700)).willReturn(detail)
        given(useCase.getForUser(42, 701)).willThrow(BusinessException(AnimalErrorCode.ANIMAL_NOT_FOUND))

        mockMvc.get("/api/core/v1/shelter-animals/700") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.shelter.phone") { value("0531234567") }
            jsonPath("$.data.classification.speciesSeq") { value(1) }
            jsonPath("$.data.rescueInfo.rescuedDttm") { value(null as Any?) }
            jsonPath("$.data.isBookmarked") { value(true) }
        }
        mockMvc.get("/api/core/v1/shelter-animals/701") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ANIMAL_NOT_FOUND") }
        }
        mockMvc.get("/api/core/v1/shelter-animals/700") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
    }

    @Test
    fun `11_3은 size 기본 8에 statusCounts와 page를 함께 내린다, USER는 403`() {
        val result = MyAnimalPage(
            linkedMapOf("total" to 1L, "PROTECTING_AVAILABLE" to 0L, "PROTECTING_UNAVAILABLE" to 1L, "RETURNED" to 0L, "ADOPTED" to 0L, "ENDED" to 0L),
            PageResponse(listOf(card), 0, 8, 1, 1, false),
        )
        given(useCase.searchMine(SHELTER, MyAnimalSearchCondition(period = RegistrationPeriod.RECENT_30D), PageRequest.of(0, 8))).willReturn(result)

        mockMvc.get("/api/core/v1/shelter-animals/me?period=RECENT_30D") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.statusCounts.total") { value(1) }
            jsonPath("$.data.statusCounts.PROTECTING_UNAVAILABLE") { value(1) }
            jsonPath("$.data.page.content[0].animalSeq") { value(700) }
            jsonPath("$.data.page.size") { value(8) }
        }
        mockMvc.get("/api/core/v1/shelter-animals/me") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `11_4는 201로 등록 결과를 돌려준다, 허용값 밖 adoptionStatus·필수 누락은 400`() {
        val command = RegisterAnimalCommand(301, AnimalBasics(88, Gender.UNKNOWN, null, null, null, null, "3kg", null, null, null), null, null, null, listOf(1701, 1702))
        given(useCase.register(SHELTER, command)).willReturn(RegisteredAnimal(700, ProtectionStatus.PROTECTING, AdoptionStatus.UNAVAILABLE, start, end))

        mockMvc.post("/api/core/v1/shelter-animals") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rescueSeq":301,"combinationSeq":88,"gender":"UNKNOWN","weight":"3kg","imageSeqs":[1701,1702]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.animalSeq") { value(700) }
            jsonPath("$.data.protectionStatus") { value("PROTECTING") }
            jsonPath("$.data.noticeStartDate") { value("2026-10-29") }
        }
        mockMvc.post("/api/core/v1/shelter-animals") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"combinationSeq":88,"gender":"UNKNOWN","adoptionStatus":"ADOPTABLE","imageSeqs":[1701]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("adoptionStatus") }
        }
        mockMvc.post("/api/core/v1/shelter-animals") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"gender":"UNKNOWN","imageSeqs":[1701]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("combinationSeq") }
        }
        mockMvc.post("/api/core/v1/shelter-animals") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"combinationSeq":88,"gender":"UNKNOWN","imageSeqs":[1701]}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `11_5는 보낸 필드만 넘기고 11_10 상세를 돌려준다`() {
        val command = UpdateAnimalCommand(age = Optional.of("2살 추정"), isNeutered = Optional.of(true), weight = Optional.empty(), imageSeqs = listOf(1702))
        given(useCase.update(SHELTER, 700, command)).willReturn(shelterDetail)

        mockMvc.patch("/api/core/v1/shelter-animals/700") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"age":"2살 추정","isNeutered":true,"weight":null,"imageSeqs":[1702]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.animalSeq") { value(700) }
            jsonPath("$.data.applicationCounts.total") { value(0) }
        }
    }

    @Test
    fun `11_6은 상태별 입력을 넘기고 결과를 돌려준다, 허용값 밖 status는 400`() {
        val returned = OffsetDateTime.parse("2026-11-05T14:00:00+09:00")
        given(useCase.manage(SHELTER, 700, ManageAnimalCommand(status = ProtectionStatus.RETURNED, recipientName = "김가람", recipientPhone = "01012345678")))
            .willReturn(
                ManagedAnimal(
                    700, ProtectionStatus.RETURNED, AdoptionStatus.UNAVAILABLE, start, end, EndReason.RETURNED, returned,
                    ReturnInfo(801, "김가람", "01012345678", 17, returned), null, 2,
                ),
            )

        mockMvc.patch("/api/core/v1/shelter-animals/700/management") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"RETURNED","recipientName":"김가람","recipientPhone":"01012345678"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.endReason") { value("RETURNED") }
            jsonPath("$.data.returnInfo.returnSeq") { value(801) }
            jsonPath("$.data.adoptionInfo") { value(null as Any?) }
            jsonPath("$.data.autoRejectedApplications") { value(2) }
        }
        mockMvc.patch("/api/core/v1/shelter-animals/700/management") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"LOST"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `11_9는 204 본문 없음, 활성 신청이 있으면 409`() {
        given(useCase.delete(SHELTER, 701)).willThrow(BusinessException(AnimalErrorCode.ANIMAL_HAS_ACTIVE_APPLICATIONS))

        mockMvc.delete("/api/core/v1/shelter-animals/700") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(useCase).should().delete(SHELTER, 700)
        mockMvc.delete("/api/core/v1/shelter-animals/701") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ANIMAL_HAS_ACTIVE_APPLICATIONS") }
        }
    }

    @Test
    fun `11_10은 SHELTER 전용, USER는 403이고 서비스는 호출하지 않는다`() {
        given(useCase.getForShelter(SHELTER, 700)).willReturn(shelterDetail)

        mockMvc.get("/api/core/v1/shelter-animals/me/700") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.images[0].displayOrder") { value(1) }
            jsonPath("$.data.returnInfo") { value(null as Any?) }
            jsonPath("$.data.shelter") { doesNotExist() }
        }
        mockMvc.get("/api/core/v1/shelter-animals/me/700") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isForbidden() }
        }
        then(useCase).should(never()).getForShelter(USER, 700)
    }

    @Test
    fun `토큰이 없으면 401`() {
        mockMvc.get("/api/core/v1/shelter-animals").andExpect { status { isUnauthorized() } }
        verifyNoInteractions(useCase)
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        private val USER = AuthUser(42, Role.USER, null)
        private val SHELTER = AuthUser(77, Role.SHELTER, 7)
        private val USER_TOKEN = bearer(mapOf("userSeq" to 42, "role" to "USER"))
        private val SHELTER_TOKEN = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
