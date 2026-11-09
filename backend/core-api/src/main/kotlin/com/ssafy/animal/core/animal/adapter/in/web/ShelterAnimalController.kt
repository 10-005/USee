package com.ssafy.animal.core.animal.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.RegistrationPeriod
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalUseCase
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.Gender
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
import org.springframework.format.annotation.DateTimeFormat
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** 명세 11장 보호동물. 소속 검증은 서비스가 토큰 shelterSeq로 한다 */
@Tag(name = "보호동물", description = "보호동물 목록·앱 상세(USER), 보호소 웹의 목록·상세·등록·수정·상태 관리·삭제(SHELTER). 다른 보호소 동물은 404로 감춘다")
@RestController
@RequestMapping("/api/core/v1/shelter-animals")
class ShelterAnimalController(
    private val shelterAnimalUseCase: ShelterAnimalUseCase,
) {

    /** 11.1 목록·검색(로그인) */
    @Operation(
        summary = "보호동물 목록·검색",
        description = "- 로그인 필요(역할 무관)\n" +
            "- 삭제되지 않은 보호동물 전체가 대상이다. 필터는 모두 선택이고 생략하면 그 조건 없이 조회한다.\n" +
            "- `status`: 보호 상태와 입양 상태를 합친 값. PROTECTING_AVAILABLE=보호 중·입양 가능, PROTECTING_UNAVAILABLE=보호 중·입양 불가, RETURNED=반환 완료, ADOPTED=입양 완료, ENDED=보호 종료.\n" +
            "- `regFrom`·`regTo`: 등록일(KST) 범위, 양끝 포함. 한쪽만 보내도 된다. `regFrom`이 `regTo`보다 늦으면 400.\n" +
            "- `districtCode`: 보호소 소재지 기준. 빈 문자열은 무시하고, 없는 코드면 400 `CATALOG_INVALID_DISTRICT_CODE`.\n" +
            "- `gender`: MALE·FEMALE은 UNKNOWN도 포함한다. UNKNOWN은 UNKNOWN만.\n" +
            "- `isNeutered`: true는 true와 모름(null), false는 false와 모름(null)을 포함한다.\n" +
            "- `keyword`: 특징(`description`)·건강 검진(`healthCheck`) 부분 일치(대소문자 무시).\n" +
            "- size 기본 20. sort 허용 `regDttm`만, 기본 `regDttm,desc`.\n" +
            "- 상세는 USER면 11.2, SHELTER면 11.10으로 본다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterAnimalPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = SEARCH_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용값 밖의 status·gender·isNeutered, 날짜 형식 오류, regFrom > regTo, 없는 시군구 코드, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "status 허용값 밖", value = INVALID_STATUS_PARAM),
                        ExampleObject(name = "날짜 형식 오류", value = INVALID_REG_FROM),
                        ExampleObject(name = "날짜 범위 역전", value = REG_RANGE_REVERSED),
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
    fun search(
        @Parameter(description = "합성 상태. 생략하면 전체", example = "PROTECTING_AVAILABLE")
        @RequestParam(required = false) status: AnimalStatus?,
        @Parameter(description = "등록일 시작(YYYY-MM-DD, KST, 포함)", example = "2026-10-01")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) regFrom: LocalDate?,
        @Parameter(description = "등록일 끝(YYYY-MM-DD, KST, 포함)", example = "2026-10-31")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) regTo: LocalDate?,
        @Parameter(description = "보호소 소재지 시군구 코드 5자리(5.5)", example = "27110")
        @RequestParam(required = false) districtCode: String?,
        @Parameter(description = "축종 번호(5.1)", example = "1")
        @RequestParam(required = false) speciesSeq: Int?,
        @Parameter(description = "성별. MALE·FEMALE은 UNKNOWN 포함", example = "MALE")
        @RequestParam(required = false) gender: Gender?,
        @Parameter(description = "중성화 여부. 모름(null)도 포함", example = "true")
        @RequestParam(required = false) isNeutered: Boolean?,
        @Parameter(description = "특징·건강 검진 검색어(부분 일치)", example = "순함")
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(
        shelterAnimalUseCase.search(
            AnimalSearchCondition(status, regFrom, regTo, districtCode?.takeIf { it.isNotBlank() }, speciesSeq, gender, isNeutered, keyword),
            pageable,
        ),
    )

    /** 11.3 우리 보호소 보호동물(size 기본 8, 상태별 건수 동봉) */
    @Operation(
        summary = "우리 보호소 보호동물 목록",
        description = "- SHELTER만\n" +
            "- 토큰 보호소의 삭제되지 않은 보호동물 목록과 상태 탭별 건수(`statusCounts`)를 한 응답으로 준다.\n" +
            "- 필터는 모두 선택이다. `status` 값은 11.1과 같다.\n" +
            "- `period`: `regDttm`(등록 시각)이 요청 시각 기준 최근 7·30·90일 이내.\n" +
            "- `gender`·`isNeutered`는 11.1과 같은 포함 매칭이다.\n" +
            "- `statusCounts`: `total`과 합성 상태 다섯 개. `status`를 뺀 나머지 필터만 적용해 센다. `page.totalElements`는 현재 탭의 건수다.\n" +
            "- size 기본 **8**. sort 허용 `regDttm`만, 기본 `regDttm,desc`.\n" +
            "- 항목은 11.1과 같다. 상세는 11.10으로 본다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MyAnimalPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = MINE_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용값 밖의 status·period·gender·isNeutered, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "status 허용값 밖", value = INVALID_STATUS_PARAM),
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
    )
    @GetMapping("/me")
    @PreAuthorize("hasRole('SHELTER')")
    fun searchMine(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "합성 상태 탭. 생략하면 전체", example = "PROTECTING_AVAILABLE")
        @RequestParam(required = false) status: AnimalStatus?,
        @Parameter(description = "등록 기간. regDttm이 최근 7·30·90일 이내. 생략하면 전체", example = "RECENT_30D")
        @RequestParam(required = false) period: RegistrationPeriod?,
        @Parameter(description = "축종 번호(5.1)", example = "1")
        @RequestParam(required = false) speciesSeq: Int?,
        @Parameter(description = "성별. MALE·FEMALE은 UNKNOWN 포함", example = "FEMALE")
        @RequestParam(required = false) gender: Gender?,
        @Parameter(description = "중성화 여부. 모름(null)도 포함", example = "true")
        @RequestParam(required = false) isNeutered: Boolean?,
        @PageableDefault(size = 8) pageable: Pageable,
    ) = shelterAnimalUseCase.searchMine(user, MyAnimalSearchCondition(status, period, speciesSeq, gender, isNeutered), pageable)

    /** 11.10 보호소 전용 상세 */
    @Operation(
        summary = "보호동물 상세(보호소 웹)",
        description = "- SHELTER만\n" +
            "- 우리 보호소의 삭제되지 않은 보호동물만 보인다. 없음·삭제·다른 보호소 동물은 모두 404.\n" +
            "- 11.2에서 `shelter`·`representativeImageUrl`·`isBookmarked`를 빼고 관리 정보(`returnInfo`·`adoptionInfo`·`applicationCounts`)를 더한 구성이다.\n" +
            "- `returnInfo`: 반환 기록이 있으면 현재 상태와 관계없이 채운다.\n" +
            "- `adoptionInfo`: ADOPTED이거나 입양 사유·채택 신청서가 남아 있으면 채운다.\n" +
            "- `applicationCounts`의 자세한 목록은 12.9(`animalSeq`)로 본다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterAnimalDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = SHELTER_DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "animalSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "animalSeq 타입 오류", value = INVALID_ANIMAL_SEQ)],
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
            description = "보호동물이 없거나 삭제됨, 또는 다른 보호소 소속",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/me/{animalSeq}")
    @PreAuthorize("hasRole('SHELTER')")
    fun getForShelter(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "보호동물 번호", example = "700") @PathVariable animalSeq: Int,
    ) = shelterAnimalUseCase.getForShelter(user, animalSeq)

    /** 11.2 앱 상세(USER 전용, Q478) */
    @Operation(
        summary = "보호동물 상세(앱)",
        description = "- USER만. SHELTER는 11.10을 쓴다(403).\n" +
            "- 삭제되지 않은 보호동물이면 상태와 관계없이 보인다.\n" +
            "- `shelter`는 연락처 블록 `{ shelterSeq, shelterName, phone, address }`. `shelterSeq`로 채팅방을 만든다(14.2).\n" +
            "- `rescueInfo`: 구조 연결이 없거나 구조가 삭제됐으면 다섯 필드가 모두 null(객체는 항상 있음).\n" +
            "- `isBookmarked`는 호출한 회원 기준.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AnimalDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = USER_DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "animalSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "animalSeq 타입 오류", value = INVALID_ANIMAL_SEQ)],
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
            description = "USER가 아닌 계정(SHELTER). 11.10을 쓴다",
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
            description = "보호동물이 없거나 삭제됨",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{animalSeq}")
    @PreAuthorize("hasRole('USER')")
    fun getForUser(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "보호동물 번호", example = "700") @PathVariable animalSeq: Int,
    ) = shelterAnimalUseCase.getForUser(user.userSeq, animalSeq)

    /** 11.4 등록 */
    @Operation(
        summary = "보호동물 등록",
        description = "- SHELTER만\n" +
            "- 보호소는 토큰 보호소로 서버가 정한다(요청에 shelterSeq 없음). 보호 상태는 항상 PROTECTING.\n" +
            "- `rescueSeq`를 보내면 DONE인 구조만 연결할 수 있다(다른 보호소의 구조도 가능). 한 구조에 여러 마리를 등록할 수 있다.\n" +
            "- 공고기간·입양 상태는 선택: 생략하면 시작일=등록일(KST), 종료일=시작일+10일, `adoptionStatus=UNAVAILABLE`.\n" +
            "- 사진은 1~3장, 중복 없이, 4.2로 이 계정이 올린 것만. 배열 순서가 표시 순서다.\n" +
            "- 검사 순서: 필수값·길이(400) → 사진 개수(400) → 입양 상태·공고기간(400) → 구조(404·409) → 조합(404) → 사진 소유(404·403).\n" +
            "- 등록하면 AI 매칭 작업을 요청한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "등록 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RegisteredAnimalSuccessSchema::class),
                    examples = [ExampleObject(name = "등록 완료", value = REGISTERED_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락, 허용값 밖 gender·adoptionStatus, 길이 초과, 공고기간 역전, 사진 개수·중복 오류, 본문을 읽을 수 없음(날짜 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = REGISTER_REQUIRED_MISSING),
                        ExampleObject(name = "gender 허용값 밖", value = INVALID_GENDER),
                        ExampleObject(name = "adoptionStatus=ADOPTED", value = ADOPTION_STATUS_ADOPTED),
                        ExampleObject(name = "길이 초과", value = TOO_LONG),
                        ExampleObject(name = "공고기간 역전", value = NOTICE_REVERSED),
                        ExampleObject(name = "IMAGE_INVALID_COUNT", value = IMAGE_INVALID_COUNT),
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
            description = "구조 기록이 없거나 삭제됨, 조합 번호가 없음, 이미지 번호가 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "RESCUE_NOT_FOUND", value = RESCUE_NOT_FOUND),
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "구조가 DONE이 아님. 구조를 먼저 완료 처리(10.5)한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_NOT_DONE", value = RESCUE_NOT_DONE)],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SHELTER')")
    fun register(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: RegisterAnimalRequest) =
        shelterAnimalUseCase.register(user, request.toCommand())

    /** 11.5 기초 정보·사진 수정 */
    @Operation(
        summary = "보호동물 기초 정보·사진 수정",
        description = "- SHELTER만\n" +
            "- 부분 수정. 보낸 필드만 바꾸고 생략한 필드는 유지한다. 선택 필드는 null을 보내면 비운다(`combinationSeq`·`gender`의 null은 미전달로 본다).\n" +
            "- 공고기간·보호 상태·입양 상태는 받지 않는다(보내도 무시). 11.6으로 바꾼다. 구조 정보는 10.4로 고친다.\n" +
            "- `imageSeqs`는 전체 최종 배열(1~3장). 새로 넣는 이미지만 소유를 확인하고, 빠진 이미지는 다른 곳에서 쓰지 않으면 삭제된다.\n" +
            "- 사진 구성이 바뀌고(순서만 바뀐 것은 제외) PROTECTING이면 AI 재매칭을 요청한다.\n" +
            "- 응답은 수정 후 보호소 상세(11.10과 같은 형태).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 성공. 수정 후 상세",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterAnimalDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 성공", value = SHELTER_DETAIL_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용값 밖 gender, 길이 초과, 사진 개수·중복 오류, animalSeq 타입 오류, 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "gender 허용값 밖", value = INVALID_GENDER),
                        ExampleObject(name = "길이 초과", value = TOO_LONG),
                        ExampleObject(name = "IMAGE_INVALID_COUNT", value = IMAGE_INVALID_COUNT),
                        ExampleObject(name = "animalSeq 타입 오류", value = INVALID_ANIMAL_SEQ),
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
            description = "보호동물이 없거나 삭제됨·다른 보호소 소속, 조합 번호가 없음, 이미지 번호가 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND),
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
    )
    @PatchMapping("/{animalSeq}")
    @PreAuthorize("hasRole('SHELTER')")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "보호동물 번호", example = "700") @PathVariable animalSeq: Int,
        @Valid @RequestBody request: UpdateAnimalRequest,
    ) = shelterAnimalUseCase.update(user, animalSeq, request.toCommand())

    /** 11.6 보호 상태·입양 가능 여부·공고기간 관리 */
    @Operation(
        summary = "보호 상태·입양 상태·공고기간 관리",
        description = "- SHELTER만\n" +
            "- 보낸 필드만 바꾼다. `status`를 생략하면 공고기간만 바꾼다. 공고기간은 상태와 관계없이 언제든 고칠 수 있다.\n" +
            "- 상태 전환은 어느 상태에서든 허용한다. `status`별 추가 입력:\n" +
            "  - PROTECTING: `adoptionStatus`(AVAILABLE·UNAVAILABLE). 생략하면 현재 값 유지, 다른 상태에서 복귀하면 UNAVAILABLE.\n" +
            "  - ENDED: `endReason`(NATURAL_DEATH·EUTHANIZED·OTHER). 생략하면 OTHER.\n" +
            "  - RETURNED: `recipientName`·`recipientPhone` 필수. 반환 기록은 동물당 1건이라 다시 보내면 덮어쓴다. 같은 번호의 활성 USER가 정확히 1명이면 `returnedUserSeq`로 연결한다.\n" +
            "  - ADOPTED: `reason` 필수, `memo` 선택. 외부 입양 처리이며 신청서는 연결하지 않는다(신청서 채택은 12.11).\n" +
            "- 그 `status`에 맞지 않는 추가 필드를 보내면 400.\n" +
            "- RETURNED·ADOPTED·ENDED로 바꾸면 진행 중인 신청(DRAFT·SUBMITTED·APPROVED)을 자동 거절하고 신청자에게 알린다. 건수는 `autoRejectedApplications`.\n" +
            "- PROTECTING으로 되돌려도 자동 거절된 신청은 되살리지 않고, 반환·입양 기록은 이력으로 남는다.\n" +
            "- 검사 순서: 입력 검증(400) → 보호동물(404) → 공고기간 역전(400).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "변경 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ManagedAnimalSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "반환 완료", value = MANAGED_RETURNED_OK),
                        ExampleObject(name = "보호 중·입양 가능", value = MANAGED_PROTECTING_OK),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용값 밖 status·adoptionStatus·endReason, 상태와 맞지 않는 추가 필드, RETURNED의 이름·연락처 누락, ADOPTED의 사유 누락, 길이 초과, 공고기간 역전, animalSeq 타입 오류, 본문을 읽을 수 없음(날짜 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "status 허용값 밖", value = INVALID_MANAGE_STATUS),
                        ExampleObject(name = "상태와 맞지 않는 필드", value = MANAGE_EXTRA_FIELD),
                        ExampleObject(name = "RETURNED 필수값 누락", value = MANAGE_RETURNED_MISSING),
                        ExampleObject(name = "endReason 입력 불가 값", value = MANAGE_END_REASON),
                        ExampleObject(name = "공고기간 역전", value = NOTICE_REVERSED),
                        ExampleObject(name = "animalSeq 타입 오류", value = INVALID_ANIMAL_SEQ),
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
            description = "SHELTER가 아닌 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "보호동물이 없거나 삭제됨, 또는 다른 보호소 소속",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "동시 변경 경합으로 상태 조합이 DB 제약에 어긋남. 상세를 다시 읽고 재시도한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_INVALID_STATUS_COMBINATION", value = ANIMAL_INVALID_STATUS_COMBINATION)],
                ),
            ],
        ),
    )
    @PatchMapping("/{animalSeq}/management")
    @PreAuthorize("hasRole('SHELTER')")
    fun manage(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "보호동물 번호", example = "700") @PathVariable animalSeq: Int,
        @RequestBody request: ManageAnimalRequest,
    ) = shelterAnimalUseCase.manage(user, animalSeq, request.toCommand())

    /** 11.9 논리 삭제 */
    @Operation(
        summary = "보호동물 삭제",
        description = "- SHELTER만\n" +
            "- 논리 삭제. 삭제된 보호동물은 목록·상세·AI 매칭에서 빠진다.\n" +
            "- 진행 중인 입양 신청(DRAFT·SUBMITTED·APPROVED)이 하나라도 있으면 거부한다(409). 신청을 먼저 정리한다.\n" +
            "- 이미 삭제된 보호동물을 다시 지우면 404.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "animalSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "animalSeq 타입 오류", value = INVALID_ANIMAL_SEQ)],
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
            description = "SHELTER가 아닌 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "보호동물이 없거나 이미 삭제됨, 또는 다른 보호소 소속",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "진행 중인 입양 신청이 있음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_HAS_ACTIVE_APPLICATIONS", value = ANIMAL_HAS_ACTIVE_APPLICATIONS)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{animalSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SHELTER')")
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "보호동물 번호", example = "700") @PathVariable animalSeq: Int,
    ) {
        shelterAnimalUseCase.delete(user, animalSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val SEARCH_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"animalSeq":700,"classification":{"combinationSeq":88,"speciesName":"개","breedName":"말티즈","colorName":"흰색"},"gender":"UNKNOWN","age":"1살 추정","isNeutered":null,"weight":"3kg","protectionStatus":"PROTECTING","adoptionStatus":"AVAILABLE","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-08","shelter":{"shelterSeq":7,"shelterName":"대구동물보호센터","districtCode":"27110"},"representativeImageUrl":"https://cdn.example.com/images/1701.jpg","regDttm":"2026-10-29T12:00:00+09:00"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MINE_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"statusCounts":{"total":31,"PROTECTING_AVAILABLE":9,"PROTECTING_UNAVAILABLE":12,"RETURNED":4,"ADOPTED":5,"ENDED":1},"page":{"content":[{"animalSeq":700,"classification":{"combinationSeq":88,"speciesName":"개","breedName":"말티즈","colorName":"흰색"},"gender":"UNKNOWN","age":"1살 추정","isNeutered":null,"weight":"3kg","protectionStatus":"PROTECTING","adoptionStatus":"AVAILABLE","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-08","shelter":{"shelterSeq":7,"shelterName":"대구동물보호센터","districtCode":"27110"},"representativeImageUrl":"https://cdn.example.com/images/1701.jpg","regDttm":"2026-10-29T12:00:00+09:00"}],"page":0,"size":8,"totalElements":9,"totalPages":2,"hasNext":true}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val USER_DETAIL_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"animalSeq":700,"classification":{"combinationSeq":88,"speciesSeq":1,"speciesName":"개","breedSeq":21,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"},"gender":"UNKNOWN","age":"1살 추정","isNeutered":null,"weight":"3kg","protectionStatus":"PROTECTING","adoptionStatus":"AVAILABLE","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-08","shelter":{"shelterSeq":7,"shelterName":"대구동물보호센터","phone":"0531234567","address":"대구광역시 중구 동덕로 123"},"representativeImageUrl":"https://cdn.example.com/images/1701.jpg","regDttm":"2026-10-29T12:00:00+09:00","modDttm":null,"rescueSeq":301,"microchipNumber":null,"registrationNumber":null,"description":"사람을 잘 따름","healthCheck":"기초 검진 완료","vaccinationStatus":"예방접종 확인 중","endReason":null,"protectionEndedDttm":null,"rescueInfo":{"rescuedDttm":"2026-10-29T09:30:00+09:00","rescueReasonCode":"WANDERING","rescueAddress":"대구광역시 중구 동인동 12","rescueAddressDetail":null,"rescueMemo":null},"images":[{"imageSeq":1701,"imageUrl":"https://cdn.example.com/images/1701.jpg","displayOrder":1}],"isBookmarked":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SHELTER_DETAIL_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"animalSeq":700,"classification":{"combinationSeq":88,"speciesSeq":1,"speciesName":"개","breedSeq":21,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"},"gender":"UNKNOWN","age":"1살 추정","isNeutered":null,"weight":"3kg","microchipNumber":null,"registrationNumber":null,"description":"사람을 잘 따름","healthCheck":"기초 검진 완료","vaccinationStatus":"예방접종 확인 중","protectionStatus":"ADOPTED","adoptionStatus":"ADOPTED","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-08","endReason":"ADOPTED","protectionEndedDttm":"2026-11-05T14:00:00+09:00","rescueSeq":301,"rescueInfo":{"rescuedDttm":"2026-10-29T09:30:00+09:00","rescueReasonCode":"WANDERING","rescueAddress":"대구광역시 중구 동인동 12","rescueAddressDetail":null,"rescueMemo":null},"images":[{"imageSeq":1701,"imageUrl":"https://cdn.example.com/images/1701.jpg","displayOrder":1}],"returnInfo":null,"adoptionInfo":{"completedApplicationSeq":901,"applicantName":"김가람","reason":null,"memo":null,"completedDttm":"2026-11-05T14:00:00+09:00"},"applicationCounts":{"total":5,"SUBMITTED":0,"APPROVED":0,"ADOPTED":1,"REJECTED":4},"regDttm":"2026-10-29T12:00:00+09:00","modDttm":"2026-11-05T14:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REGISTERED_OK =
            """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"animalSeq":700,"protectionStatus":"PROTECTING","adoptionStatus":"UNAVAILABLE","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-08"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MANAGED_RETURNED_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"animalSeq":700,"protectionStatus":"RETURNED","adoptionStatus":"UNAVAILABLE","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-15","endReason":"RETURNED","protectionEndedDttm":"2026-11-05T14:00:00+09:00","returnInfo":{"returnSeq":801,"recipientName":"김가람","recipientPhone":"01012345678","returnedUserSeq":17,"returnedDttm":"2026-11-05T14:00:00+09:00"},"adoptionInfo":null,"autoRejectedApplications":2},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MANAGED_PROTECTING_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"animalSeq":700,"protectionStatus":"PROTECTING","adoptionStatus":"AVAILABLE","noticeStartDate":"2026-10-29","noticeEndDate":"2026-11-15","endReason":null,"protectionEndedDttm":null,"returnInfo":null,"adoptionInfo":null,"autoRejectedApplications":0},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_STATUS_PARAM =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_REG_FROM =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"regFrom","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REG_RANGE_REVERSED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"regFrom","reason":"시작일이 종료일보다 늦습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_INVALID_DISTRICT_CODE =
            """{"status":400,"code":"CATALOG_INVALID_DISTRICT_CODE","message":"존재하지 않는 행정구역 코드입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SORT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: noticeEndDate (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_ANIMAL_SEQ =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"animalSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REGISTER_REQUIRED_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"combinationSeq","reason":"널이어서는 안됩니다"},{"field":"gender","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_GENDER =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"gender","reason":"허용되지 않는 값입니다: BOY"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ADOPTION_STATUS_ADOPTED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"adoptionStatus","reason":"AVAILABLE 또는 UNAVAILABLE만 보낼 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val TOO_LONG =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"registrationNumber","reason":"20자 이하로 입력해 주세요."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val NOTICE_REVERSED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"noticeEndDate","reason":"공고 종료일은 시작일 이후여야 합니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_INVALID_COUNT =
            """{"status":400,"code":"IMAGE_INVALID_COUNT","message":"사진은 1~3장이어야 합니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_MANAGE_STATUS =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"허용되지 않는 값입니다: DELETED"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MANAGE_EXTRA_FIELD =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"adoptionStatus","reason":"status=PROTECTING일 때만 보낼 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MANAGE_RETURNED_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"recipientName","reason":"필수 입력값입니다."},{"field":"recipientPhone","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MANAGE_END_REASON =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"endReason","reason":"NATURAL_DEATH, EUTHANIZED, OTHER만 보낼 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_NOT_FOUND =
            """{"status":404,"code":"ANIMAL_NOT_FOUND","message":"보호동물을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_NOT_FOUND =
            """{"status":404,"code":"RESCUE_NOT_FOUND","message":"구조 기록을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_COMBINATION_NOT_FOUND =
            """{"status":404,"code":"CATALOG_COMBINATION_NOT_FOUND","message":"존재하지 않는 종·품종·색상 조합입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_NOT_DONE =
            """{"status":409,"code":"RESCUE_NOT_DONE","message":"구조가 완료된 기록만 연결할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_INVALID_STATUS_COMBINATION =
            """{"status":409,"code":"ANIMAL_INVALID_STATUS_COMBINATION","message":"보호 상태와 입양 상태 조합이 올바르지 않습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_HAS_ACTIVE_APPLICATIONS =
            """{"status":409,"code":"ANIMAL_HAS_ACTIVE_APPLICATIONS","message":"진행 중인 입양 신청이 있어 삭제할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
