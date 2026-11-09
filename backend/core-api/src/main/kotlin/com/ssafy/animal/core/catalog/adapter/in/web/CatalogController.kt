package com.ssafy.animal.core.catalog.adapter.`in`.web

import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.BreedView
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogQueryUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.ColorView
import com.ssafy.animal.core.catalog.application.port.`in`.DistrictView
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.SpeciesView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** API 명세 5장 기준정보. 로그인만 요구하고 역할 제한은 없다 */
@Tag(name = "기준정보", description = "동물 종·품종·색상·조합, 행정구역 목록과 주소 검색. 목록은 시드 전량을 페이지 없이 준다")
@RestController
@RequestMapping("/api/core/v1")
class CatalogController(
    private val catalogQueryUseCase: CatalogQueryUseCase,
    private val geocodeUseCase: GeocodeUseCase,
) {

    /** 5.1 */
    @Operation(
        summary = "종 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 종 전체를 speciesSeq 오름차순으로 준다. 페이지 없음.\n" +
            "- 다음 호출: 고른 종의 품종 목록 `GET /api/core/v1/animal-species/{speciesSeq}/breeds`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CatalogSpeciesListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "종 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"speciesSeq":1,"speciesCode":"DOG","speciesName":"개"},{"speciesSeq":2,"speciesCode":"CAT","speciesName":"고양이"},{"speciesSeq":3,"speciesCode":"ETC","speciesName":"기타"}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/animal-species")
    fun species() = catalogQueryUseCase.findSpecies()

    /** 5.2 */
    @Operation(
        summary = "품종 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 한 종의 품종 전체를 breedSeq 오름차순으로 준다. 페이지 없음.\n" +
            "- 없는 speciesSeq면 404가 아니라 빈 배열 200.\n" +
            "- `personality`는 모의 입양에 쓰는 성격이며 모든 품종에 있다. `representativeImageUrl`은 없으면 null.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 없는 종이면 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CatalogBreedListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "품종 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"breedSeq":21,"speciesSeq":1,"breedName":"말티즈","personality":"FRIENDLY","representativeImageUrl":"https://cdn.example.com/breeds/21.jpg"},{"breedSeq":22,"speciesSeq":1,"breedName":"진돗개","personality":"ACTIVE","representativeImageUrl":null}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "없는 종", value = EMPTY_LIST),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "speciesSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "speciesSeq 타입 오류", value = INVALID_SPECIES_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/animal-species/{speciesSeq}/breeds")
    fun breeds(@Parameter(description = "종 번호(종 목록의 speciesSeq)", example = "1") @PathVariable speciesSeq: Int) =
        catalogQueryUseCase.findBreeds(speciesSeq)

    /** 5.3 */
    @Operation(
        summary = "색상 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 색상 전체를 colorSeq 오름차순으로 준다. 페이지 없음.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CatalogColorListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "색상 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"colorSeq":1,"colorName":"흰색"},{"colorSeq":2,"colorName":"검은색"}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/animal-colors")
    fun colors() = catalogQueryUseCase.findColors()

    /** 5.4 */
    @Operation(
        summary = "종·품종·색상 조합 조회",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 유효한 품종·색상 조합을 combinationSeq 오름차순으로 준다. 페이지 없음.\n" +
            "- `breedSeq`·`colorSeq`는 각각 선택. 둘 다 생략하면 전체 조합.\n" +
            "- 맞는 조합이 없거나 없는 번호면 빈 배열 200.\n" +
            "- 동물 등록·수정 요청에는 여기서 고른 `combinationSeq`를 보낸다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 맞는 조합이 없으면 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CatalogCombinationListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "조합 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"combinationSeq":88,"speciesSeq":1,"speciesCode":"DOG","speciesName":"개","breedSeq":21,"breedName":"말티즈","colorSeq":1,"colorName":"흰색"}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "맞는 조합 없음", value = EMPTY_LIST),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "breedSeq·colorSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "breedSeq 타입 오류", value = INVALID_BREED_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/animal-combinations")
    fun combinations(
        @Parameter(description = "품종 번호 필터(선택)", example = "21") @RequestParam(required = false) breedSeq: Int?,
        @Parameter(description = "색상 번호 필터(선택)", example = "1") @RequestParam(required = false) colorSeq: Int?,
    ) = catalogQueryUseCase.findCombinations(breedSeq, colorSeq)

    /** 5.5 */
    @Operation(
        summary = "행정구역 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 시·군·구 행정구역을 districtCode 오름차순으로 준다. 페이지 없음.\n" +
            "- `sidoName`은 앞뒤 공백을 지우고 시·도 이름과 정확히 같은 것만 고른다(부분 일치 아님). 생략·공백이면 전국.\n" +
            "- 맞는 시·도가 없으면 빈 배열 200.\n" +
            "- `districtCode`는 신고·보호소 목록의 지역 필터 값으로 쓴다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 맞는 시·도가 없으면 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CatalogDistrictListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "대구광역시",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"districtCode":"27110","sidoName":"대구광역시","sigunguName":"중구"},{"districtCode":"27140","sidoName":"대구광역시","sigunguName":"동구"}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "맞는 시·도 없음", value = EMPTY_LIST),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/districts")
    fun districts(
        @Parameter(description = "시·도 이름(선택, 정확히 일치). 생략하면 전국", example = "대구광역시")
        @RequestParam(required = false) sidoName: String?,
    ) = catalogQueryUseCase.findDistricts(sidoName?.trim()?.ifEmpty { null })

    /** 5.6 query는 공백 제거 후 1~100자 */
    @Operation(
        summary = "주소 검색",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 주소 텍스트로 네이버 지오코딩을 호출해 후보(주소·위경도)를 준다. 저장·캐시 없음.\n" +
            "- `query`는 앞뒤 공백을 지우고 1~100자.\n" +
            "- 결과가 없으면 빈 배열 200. 다른 표현으로 다시 검색하게 한다.\n" +
            "- `roadAddress`·`jibunAddress`는 없으면 null. 표시 주소는 `roadAddress`가 있으면 그것, 없으면 `jibunAddress`를 쓴다.\n" +
            "- 고른 후보의 주소·위경도를 실종·발견 신고 등록 요청의 `address`·`latitude`·`longitude`에 그대로 넣는다.\n" +
            "- 네이버 응답이 3초 안에 오지 않거나 실패하면 503. 잠시 후 재시도한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "검색 성공. 결과가 없으면 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CatalogGeocodeListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "검색 결과",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"roadAddress":"대구광역시 중구 동성로 1","jibunAddress":"대구광역시 중구 동성로2가 1","latitude":35.8714,"longitude":128.6014}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "결과 없음", value = EMPTY_LIST),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "query 누락·공백·100자 초과",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "query 누락·길이 오류", value = INVALID_GEOCODE_QUERY)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "503",
            description = "네이버 지오코딩 호출 실패·시간 초과(3초)·키 미설정. 잠시 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CATALOG_GEOCODE_UNAVAILABLE", value = CATALOG_GEOCODE_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @GetMapping("/geocode")
    fun geocode(
        @Parameter(description = "주소 텍스트. 앞뒤 공백 제거 후 1~100자(필수)", example = "대구광역시 중구 동성로 1")
        @RequestParam(required = false) query: String?,
    ): List<GeocodeCandidate> {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > MAX_QUERY_LENGTH) {
            throw BusinessException(
                CommonErrorCode.COMMON_INVALID_INPUT,
                errors = listOf(ErrorDetail("query", "1자 이상 ${MAX_QUERY_LENGTH}자 이하로 입력하세요.")),
            )
        }
        return geocodeUseCase.search(trimmed)
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 100

        // 문서 예시 전용
        const val EMPTY_LIST =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SPECIES_SEQ_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"speciesSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_BREED_SEQ_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"breedSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_GEOCODE_QUERY =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"query","reason":"1자 이상 100자 이하로 입력하세요."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_GEOCODE_UNAVAILABLE =
            """{"status":503,"code":"CATALOG_GEOCODE_UNAVAILABLE","message":"주소 검색을 일시적으로 사용할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class CatalogSpeciesListSuccessSchema : SuccessResponseSchema<List<SpeciesView>>()
abstract class CatalogBreedListSuccessSchema : SuccessResponseSchema<List<BreedView>>()
abstract class CatalogColorListSuccessSchema : SuccessResponseSchema<List<ColorView>>()
abstract class CatalogCombinationListSuccessSchema : SuccessResponseSchema<List<AnimalClassification>>()
abstract class CatalogDistrictListSuccessSchema : SuccessResponseSchema<List<DistrictView>>()
abstract class CatalogGeocodeListSuccessSchema : SuccessResponseSchema<List<GeocodeCandidate>>()
