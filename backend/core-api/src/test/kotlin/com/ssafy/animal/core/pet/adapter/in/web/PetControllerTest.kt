package com.ssafy.animal.core.pet.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.pet.application.port.`in`.CreatePetCommand
import com.ssafy.animal.core.pet.application.port.`in`.CreatedPet
import com.ssafy.animal.core.pet.application.port.`in`.PetDetail
import com.ssafy.animal.core.pet.application.port.`in`.PetImageRef
import com.ssafy.animal.core.pet.application.port.`in`.PetSummary
import com.ssafy.animal.core.pet.application.port.`in`.PetUseCase
import com.ssafy.animal.core.pet.application.port.`in`.UpdatePetCommand
import com.ssafy.animal.core.pet.domain.Pet
import com.ssafy.animal.core.pet.domain.PetErrorCode
import com.ssafy.animal.core.pet.domain.PetGender
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date
import java.util.Optional

@WebMvcTest(PetController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class PetControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var petUseCase: PetUseCase

    private val classification = AnimalClassification(88, 1, SpeciesCode.DOG, "개", 3, "말티즈", 2, "흰색")

    private val pet = Pet(42, "초코", 88, PetGender.MALE, "3살", "4.5kg", true, null, "파란 목줄")
        .also { ReflectionTestUtils.setField(it, "petSeq", 5) }

    private val detail = PetDetail(pet, classification, listOf(PetImageRef(1001, "https://cdn/1001.jpg", 1), PetImageRef(1002, null, 2)))

    @Test
    fun `6_1은 본인 반려동물 페이지를 돌려준다`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        given(petUseCase.getPage(42, pageable)).willReturn(PageImpl(listOf(PetSummary(pet, classification, "https://cdn/1001.jpg")), pageable, 1))

        mockMvc.get("/api/core/v1/users/me/pets?sort=name,asc") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].petSeq") { value(5) }
            jsonPath("$.data.content[0].name") { value("초코") }
            jsonPath("$.data.content[0].classification.breedName") { value("말티즈") }
            jsonPath("$.data.content[0].gender") { value("MALE") }
            jsonPath("$.data.content[0].representativeImageUrl") { value("https://cdn/1001.jpg") }
            jsonPath("$.data.totalElements") { value(1) }
        }
    }

    @Test
    fun `SHELTER는 6장을 쓸 수 없다`() {
        mockMvc.get("/api/core/v1/users/me/pets") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(petUseCase).should(never()).get(anyLong(), anyLong())
    }

    @Test
    fun `6_2는 상세와 이미지 순서를 돌려주고 없으면 404 USER_PET_NOT_FOUND`() {
        given(petUseCase.get(42, 5)).willReturn(detail)
        given(petUseCase.get(42, 6)).willThrow(BusinessException(PetErrorCode.USER_PET_NOT_FOUND))

        mockMvc.get("/api/core/v1/users/me/pets/5") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.age") { value("3살") }
            jsonPath("$.data.weight") { value("4.5kg") }
            jsonPath("$.data.isNeutered") { value(true) }
            jsonPath("$.data.microchipNumber") { value(null as Any?) }
            jsonPath("$.data.images[1].imageSeq") { value(1002) }
            jsonPath("$.data.images[1].displayOrder") { value(2) }
            jsonPath("$.data.representativeImageUrl") { value("https://cdn/1001.jpg") }
        }
        mockMvc.get("/api/core/v1/users/me/pets/6") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_PET_NOT_FOUND") }
        }
    }

    @Test
    fun `6_3은 201로 등록 결과를 돌려준다`() {
        val command = CreatePetCommand("초코", 88, PetGender.MALE, "3살", "4.5kg", true, null, "파란 목줄", listOf(1001, 1002))
        given(petUseCase.create(42, command)).willReturn(CreatedPet(5, 2, 1001))

        mockMvc.post("/api/core/v1/users/me/pets") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"name":"초코","combinationSeq":88,"gender":"MALE","age":"3살","weight":"4.5kg","isNeutered":true,
                 "microchipNumber":null,"description":"파란 목줄","imageSeqs":[1001,1002]}
            """
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.petSeq") { value(5) }
            jsonPath("$.data.imageCount") { value(2) }
            jsonPath("$.data.representativeImageSeq") { value(1001) }
        }
    }

    @Test
    fun `6_3 필수값 누락은 400, 서비스 오류는 명세 코드 그대로`() {
        mockMvc.post("/api/core/v1/users/me/pets") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":" ","gender":"MALE","imageSeqs":[1]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }

        listOf(
            listOf(1L) to BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND),
            listOf(2L) to BusinessException(ImageErrorCode.IMAGE_NOT_FOUND),
            listOf(3L) to BusinessException(ImageErrorCode.IMAGE_NOT_OWNED),
            listOf(1L, 2L, 3L, 4L) to BusinessException(PetErrorCode.IMAGE_INVALID_COUNT),
        ).forEach { (imageSeqs, error) ->
            willThrow(error).given(petUseCase).create(42, CreatePetCommand("초코", 88, PetGender.MALE, imageSeqs = imageSeqs))
            mockMvc.post("/api/core/v1/users/me/pets") {
                header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"초코","combinationSeq":88,"gender":"MALE","imageSeqs":${imageSeqs}}"""
            }.andExpect {
                status { isEqualTo(error.errorCode.status.value()) }
                jsonPath("$.code") { value(error.errorCode.toString()) }
            }
        }
    }

    @Test
    fun `6_4는 미전달·명시적 null을 구분해 넘기고 상세를 돌려준다`() {
        val command = UpdatePetCommand(name = "콩이", age = Optional.empty(), imageSeqs = listOf(1002, 1001))
        given(petUseCase.update(42, 5, command)).willReturn(detail)
        willThrow(BusinessException(PetErrorCode.USER_PET_NOT_FOUND)).given(petUseCase).update(42, 6, UpdatePetCommand(name = "x"))

        mockMvc.patch("/api/core/v1/users/me/pets/5") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"콩이","age":null,"imageSeqs":[1002,1001]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.petSeq") { value(5) }
        }
        mockMvc.patch("/api/core/v1/users/me/pets/6") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"x"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_PET_NOT_FOUND") }
        }
    }

    @Test
    fun `6_5는 204 본문 없음, 타인 것은 404`() {
        willThrow(BusinessException(PetErrorCode.USER_PET_NOT_FOUND)).given(petUseCase).delete(42, 6)

        mockMvc.delete("/api/core/v1/users/me/pets/5") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(petUseCase).should().delete(42, 5)
        mockMvc.delete("/api/core/v1/users/me/pets/6") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_PET_NOT_FOUND") }
        }
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        private val USER_TOKEN = bearer(mapOf("userSeq" to 42, "role" to "USER"))
        private val SHELTER_TOKEN = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
