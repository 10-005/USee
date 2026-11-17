package com.ssafy.animal.core.pet.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.pet.application.port.`in`.PetUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 6장 내 반려동물. USER 전용 */
@Tag(name = "내 반려동물", description = "일반 회원(USER)이 자기 반려동물을 등록·조회·수정·삭제한다. 다른 회원의 반려동물은 볼 수 없다")
@RestController
@RequestMapping("/api/core/v1/users/me/pets")
@PreAuthorize("hasRole('USER')")
class PetController(
    private val petUseCase: PetUseCase,
) {

    /** 6.1 */
    @Operation(
        summary = "내 반려동물 목록",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 본인 소유이고 삭제하지 않은 반려동물만. 필터 없음.\n" +
            "- 정렬 허용 필드: `regDttm`, `name`. 기본 `regDttm,desc`. size 기본 20.\n" +
            "- 없으면 빈 `content`로 200.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PetPageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"petSeq":5,"name":"초코","classification":$CLASSIFICATION,"gender":"MALE","representativeImageUrl":"https://cdn.example.com/images/2026/11/3f1c2a9e.jpg","regDttm":"2026-11-09T10:15:30+09:00"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "등록한 반려동물 없음",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용하지 않는 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "정렬 필드 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: age (허용: regDttm, name)"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "보호소(SHELTER) 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping
    fun getPage(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(petUseCase.getPage(user.userSeq, pageable).map(PetSummaryResponse::from))

    /** 6.2 */
    @Operation(
        summary = "내 반려동물 상세",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 목록 항목에 나이·몸무게·중성화·마이크로칩·설명·사진 목록·수정 시각을 더한다.\n" +
            "- `images`는 표시 순서대로이고 첫 번째가 대표 사진이다.\n" +
            "- 없는 번호, 삭제한 반려동물, 다른 회원의 반려동물은 모두 404 `USER_PET_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PetDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "상세", value = PET_DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "petSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "petSeq 타입 오류", value = INVALID_PET_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "보호소(SHELTER) 계정. 재시도하지 않는다",
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
            description = "없음·삭제됨·다른 회원 소유",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "USER_PET_NOT_FOUND", value = USER_PET_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{petSeq}")
    fun get(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "반려동물 번호", example = "5") @PathVariable petSeq: Long,
    ) = PetDetailResponse.from(petUseCase.get(user.userSeq, petSeq))

    /** 6.3 */
    @Operation(
        summary = "반려동물 등록",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 필수: `name`·`combinationSeq`·`gender`·`imageSeqs`. 나이·몸무게는 자유 텍스트, 중성화는 true·false·null.\n" +
            "- 사진은 1~3장, 중복 불가, 본인이 확정(`POST /api/core/v1/images`)한 이미지만. 배열 순서가 표시 순서이고 첫 번째가 대표다.\n" +
            "- 검사 순서: 필수값 → 길이 → 조합 존재 → 사진 수 → 사진 중복 → 사진 존재·소유.\n" +
            "- 다음 호출: 받은 `petSeq`로 상세 조회.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "등록 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreatedPetSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "등록 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"반려동물이 등록되었습니다.","data":{"petSeq":5,"imageCount":2,"representativeImageSeq":1001},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락, 길이 초과, 사진 수(1~3장) 위반, 같은 사진 중복, 또는 본문을 읽을 수 없음(gender 값 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "필수값 누락",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"name","reason":"공백일 수 없습니다"},{"field":"imageSeqs","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "길이 초과", value = INVALID_TEXT_LENGTH),
                        ExampleObject(name = "사진 수 위반", value = IMAGE_INVALID_COUNT),
                        ExampleObject(name = "사진 중복", value = DUPLICATE_IMAGE),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "정지된 계정, 보호소(SHELTER) 계정, 또는 본인이 올리지 않은 사진",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 종·품종·색상 조합, 또는 없는 사진",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("반려동물이 등록되었습니다.")
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: CreatePetRequest,
    ) = CreatedPetResponse.from(petUseCase.create(user.userSeq, request.toCommand()))

    /** 6.4 */
    @Operation(
        summary = "반려동물 수정",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`), 본인 소유만\n" +
            "- 부분 수정: 보낸 필드만 바꾼다. `name`·`combinationSeq`·`gender`는 생략하거나 null이면 유지한다.\n" +
            "- `age`·`weight`·`isNeutered`·`microchipNumber`·`description`은 생략하면 유지, null이면 지운다.\n" +
            "- `imageSeqs`는 원하는 최종 배열 전체를 순서대로 보낸다(1~3장, 중복 불가). 표시 순서는 배열 순서로 다시 매기고 첫 번째가 대표다. 배열의 모든 사진(기존 사진 포함)이 본인 소유여야 한다.\n" +
            "- 빠진 사진은 연결을 끊고, 다른 곳에서 참조하지 않으면 원본도 삭제한다.\n" +
            "- 응답은 수정된 상세(상세 조회와 같은 형태).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 성공. 수정된 상세",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PetDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 성공", value = PET_DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "name 공백, 길이 초과, 사진 수(1~3장) 위반, 같은 사진 중복, petSeq 타입 오류, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "name 공백",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"name","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "길이 초과", value = INVALID_TEXT_LENGTH),
                        ExampleObject(name = "사진 수 위반", value = IMAGE_INVALID_COUNT),
                        ExampleObject(name = "사진 중복", value = DUPLICATE_IMAGE),
                        ExampleObject(name = "petSeq 타입 오류", value = INVALID_PET_SEQ_TYPE),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "정지된 계정, 보호소(SHELTER) 계정, 또는 본인이 올리지 않은 사진",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "반려동물 없음·삭제됨·다른 회원 소유, 없는 조합, 또는 없는 사진",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "USER_PET_NOT_FOUND", value = USER_PET_NOT_FOUND),
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
    )
    @PatchMapping("/{petSeq}")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "반려동물 번호", example = "5") @PathVariable petSeq: Long,
        @RequestBody request: UpdatePetRequest,
    ) = PetDetailResponse.from(petUseCase.update(user.userSeq, petSeq, request.toCommand()))

    /** 6.5 */
    @Operation(
        summary = "반려동물 삭제",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`), 본인 소유만\n" +
            "- 소프트 삭제. 이후 목록·상세에서 보이지 않는다.\n" +
            "- 사진 연결을 모두 끊고, 다른 곳(예: 실종 신고)에서 참조하지 않는 사진만 원본을 삭제한다.\n" +
            "- 멱등 아님: 이미 삭제한 반려동물을 다시 삭제하면 404 `USER_PET_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "petSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "petSeq 타입 오류", value = INVALID_PET_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "정지된 계정, 또는 보호소(SHELTER) 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없음·이미 삭제됨·다른 회원 소유",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "USER_PET_NOT_FOUND", value = USER_PET_NOT_FOUND)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{petSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "반려동물 번호", example = "5") @PathVariable petSeq: Long,
    ) {
        petUseCase.delete(user.userSeq, petSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val CLASSIFICATION =
            """{"combinationSeq":88,"speciesSeq":1,"speciesCode":"DOG","speciesName":"개","breedSeq":12,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"}"""
        const val PET_DETAIL_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"petSeq":5,"name":"초코","classification":$CLASSIFICATION,"gender":"MALE","representativeImageUrl":"https://cdn.example.com/images/2026/11/3f1c2a9e.jpg","age":"3살","weight":"4.5kg","isNeutered":true,"microchipNumber":null,"description":"파란 목줄","images":[{"imageSeq":1001,"imageUrl":"https://cdn.example.com/images/2026/11/3f1c2a9e.jpg","displayOrder":1},{"imageSeq":1002,"imageUrl":"https://cdn.example.com/images/2026/11/5d6e7f80.jpg","displayOrder":2}],"regDttm":"2026-11-09T10:15:30+09:00","modDttm":"2026-11-09T11:20:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_PET_SEQ_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"petSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_TEXT_LENGTH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"name","reason":"100자 이하로 입력해 주세요."},{"field":"age","reason":"30자 이하로 입력해 주세요."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_INVALID_COUNT =
            """{"status":400,"code":"IMAGE_INVALID_COUNT","message":"사진은 1~3장이어야 합니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DUPLICATE_IMAGE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"imageSeqs","reason":"같은 사진을 두 번 넣을 수 없습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val USER_PET_NOT_FOUND =
            """{"status":404,"code":"USER_PET_NOT_FOUND","message":"반려동물을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_COMBINATION_NOT_FOUND =
            """{"status":404,"code":"CATALOG_COMBINATION_NOT_FOUND","message":"존재하지 않는 종·품종·색상 조합입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
