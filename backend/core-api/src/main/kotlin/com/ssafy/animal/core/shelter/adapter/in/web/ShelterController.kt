package com.ssafy.animal.core.shelter.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "보호소 정보", description = "보호소 목록·상세, 소속 보호소(SHELTER) 운영정보·이미지 관리. 식별정보는 시드로만 바꾼다")
@RestController
@RequestMapping("/api/core/v1/shelters")
class ShelterController(
    private val shelterUseCase: ShelterUseCase,
) {

    /** 9.1 보호소 목록·검색 */
    @Operation(
        summary = "보호소 목록·검색",
        description = "- 로그인 필요(역할 무관)\n" +
            "- 삭제되지 않은 보호소만 나온다.\n" +
            "- `keyword`: 보호소 이름 또는 주소 부분 일치(대소문자 무시). 앞뒤 공백을 지우고, 비면 조건 없이 조회한다.\n" +
            "- `districtCode`: 보호소 소재지 시군구 코드(5.5). 없는 코드면 400 `CATALOG_INVALID_DISTRICT_CODE`.\n" +
            "- size 기본 20. sort 허용 `shelterName`·`regDttm`, 기본 `shelterName,asc`.\n" +
            "- 항목은 상세에서 `description`·`operatingHours`·`closedDays`·`backgroundImage`를 뺀 것. 상세는 9.2로 본다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = PAGE_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "없는 시군구 코드, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "CATALOG_INVALID_DISTRICT_CODE", value = CATALOG_INVALID_DISTRICT_CODE),
                        ExampleObject(name = "정렬 필드 허용 밖", value = INVALID_SORT),
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
    @GetMapping
    fun getPage(
        @Parameter(description = "보호소 이름·주소 검색어(부분 일치, 대소문자 무시)", example = "행복")
        @RequestParam(required = false) keyword: String?,
        @Parameter(description = "보호소 소재지 시군구 코드 5자리(5.5)", example = "27110")
        @RequestParam(required = false) districtCode: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(shelterUseCase.getPage(keyword, districtCode, pageable).map(ShelterSummaryResponse::from))

    /** 9.3 소속 보호소 상세. 대상은 토큰 shelterSeq로만 정한다 */
    @Operation(
        summary = "소속 보호소 상세",
        description = "- SHELTER만\n" +
            "- 대상 보호소는 토큰의 보호소로만 정한다. 파라미터 없음.\n" +
            "- 응답은 9.2 상세와 같다. 이 중 9.4로 고칠 수 있는 것은 `description`·`operatingHours`·`closedDays`·`profileImage`·`backgroundImage`뿐이다.\n" +
            "- SHELTER 계정은 3.1 내 프로필 대신 이 API를 쓴다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = DETAIL_OK)],
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
            responseCode = "403",
            description = "SHELTER가 아닌 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "토큰의 보호소가 삭제됨",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SHELTER_NOT_FOUND", value = SHELTER_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/me")
    @PreAuthorize("hasRole('SHELTER')")
    fun getMine(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) = ShelterResponse.from(shelterUseCase.get(user.shelterSeq))

    /** 9.4 소속 보호소 운영정보·이미지 부분 수정 */
    @Operation(
        summary = "소속 보호소 운영정보·이미지 수정",
        description = "- SHELTER만\n" +
            "- 대상 보호소는 토큰의 보호소로만 정한다.\n" +
            "- 부분 수정. `description`·`operatingHours`·`closedDays`·`profileImageSeq`·`backgroundImageSeq` 중 보낸 필드만 바꾸고 생략한 필드는 유지한다. null을 보내면 비운다(이미지는 연결 해제).\n" +
            "- 식별정보(`shelterName`·`address`·`phone`·`latitude`·`longitude`·`districtCode`·`externalShelterCode`)를 보내면 값이 null이어도 무시하지 않고 400.\n" +
            "- 이미지는 4.2로 이 계정이 직접 올린 것만 새로 연결할 수 있다. 지금 연결된 이미지를 그대로 다시 보내면 소유 확인 없이 통과한다.\n" +
            "- 연결이 풀린 이전 이미지는 다른 곳에서 쓰지 않으면 삭제된다.\n" +
            "- 응답은 수정 후 전체 상세(9.2와 같은 형태).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 성공. 수정 후 상세",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 성공", value = DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "식별정보 필드 포함, 또는 본문을 읽을 수 없음(이미지 번호 타입 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "식별정보 수정 시도", value = IDENTITY_FIELD),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
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
            responseCode = "403",
            description = "SHELTER가 아닌 계정, 정지된 계정, 또는 다른 사람이 올린 이미지",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "토큰의 보호소가 삭제됨, 또는 이미지 번호가 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SHELTER_NOT_FOUND", value = SHELTER_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
    )
    @PatchMapping("/me")
    @PreAuthorize("hasRole('SHELTER')")
    fun updateMine(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @RequestBody request: UpdateShelterRequest) =
        ShelterResponse.from(shelterUseCase.updateMine(user.userSeq, user.shelterSeq, request.toCommand()))

    /** 9.2 보호소 상세 */
    @Operation(
        summary = "보호소 상세",
        description = "- 로그인 필요(역할 무관)\n" +
            "- 삭제되지 않은 보호소의 공개 프로필. 내부 적재 키(`externalShelterCode`)는 내보내지 않는다.\n" +
            "- 이미지가 없으면 `profileImage`·`backgroundImage`는 null.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "shelterSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "shelterSeq 타입 오류", value = INVALID_SHELTER_SEQ)],
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
            responseCode = "404",
            description = "보호소가 없거나 삭제됨",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SHELTER_NOT_FOUND", value = SHELTER_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{shelterSeq}")
    fun get(@Parameter(description = "보호소 번호", example = "7") @PathVariable shelterSeq: Long) = ShelterResponse.from(shelterUseCase.get(shelterSeq))

    private companion object {
        // 문서 예시 전용
        const val PAGE_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"shelterSeq":7,"shelterName":"행복동물보호센터","address":"대구광역시 중구 동덕로 123","districtCode":"27110","latitude":35.87,"longitude":128.6,"phone":"0531234567","profileImage":{"imageSeq":1701,"imageUrl":"https://cdn.example.com/images/1701.jpg"}}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"shelterSeq":7,"shelterName":"행복동물보호센터","address":"대구광역시 중구 동덕로 123","districtCode":"27110","latitude":35.87,"longitude":128.6,"phone":"0531234567","description":"센터 소개","operatingHours":"09:00~18:00","closedDays":"일요일","profileImage":{"imageSeq":1701,"imageUrl":"https://cdn.example.com/images/1701.jpg"},"backgroundImage":{"imageSeq":1702,"imageUrl":"https://cdn.example.com/images/1702.jpg"}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_INVALID_DISTRICT_CODE =
            """{"status":400,"code":"CATALOG_INVALID_DISTRICT_CODE","message":"존재하지 않는 행정구역 코드입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SORT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: phone (허용: shelterName, regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SHELTER_SEQ =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"shelterSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IDENTITY_FIELD =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"shelterName","reason":"보호소 식별정보는 수정할 수 없습니다."},{"field":"phone","reason":"보호소 식별정보는 수정할 수 없습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SHELTER_NOT_FOUND =
            """{"status":404,"code":"SHELTER_NOT_FOUND","message":"보호소를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
