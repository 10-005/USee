package com.ssafy.animal.core.adoption.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.adoption.application.port.`in`.AdoptionUseCase
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicationDetail
import com.ssafy.animal.core.adoption.application.port.`in`.Completed
import com.ssafy.animal.core.adoption.application.port.`in`.DraftCreated
import com.ssafy.animal.core.adoption.application.port.`in`.DraftSaved
import com.ssafy.animal.core.adoption.application.port.`in`.Failed
import com.ssafy.animal.core.adoption.application.port.`in`.FormInput
import com.ssafy.animal.core.adoption.application.port.`in`.FormPatch
import com.ssafy.animal.core.adoption.application.port.`in`.MyApplicationItem
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionList
import com.ssafy.animal.core.adoption.application.port.`in`.ReceivedItem
import com.ssafy.animal.core.adoption.application.port.`in`.ReviewDecision
import com.ssafy.animal.core.adoption.application.port.`in`.Reviewed
import com.ssafy.animal.core.adoption.application.port.`in`.StatusChanged
import com.ssafy.animal.core.adoption.application.port.`in`.Submitted
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.animal.adapter.`in`.web.toEnum
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.PageRequest
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.Optional

/** 명세 12장 입양 문항·신청·심사·완료. 12.15는 [보류]라 없다 */
@Tag(
    name = "입양",
    description = "입양 신청서 문항, 신청(DRAFT 생성·저장·제출·삭제·취소), 보호소 심사·입양 완료·승인 후 실패. " +
        "상태 전이: DRAFT → SUBMITTED → APPROVED → ADOPTED. SUBMITTED → REJECTED·CANCELED, APPROVED → REJECTED",
)
@RestController
@RequestMapping("/api/core/v1")
class AdoptionController(
    private val adoptionUseCase: AdoptionUseCase,
) {

    /** 12.3 상세(신청자·담당 보호소) */
    /** 12.1 현재 공통 문항 */
    @Operation(
        summary = "입양 신청서 문항 조회",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 시드된 현재 공통 문항 전체를 `displayOrder` 오름차순으로 준다. 문항 수를 고정해 가정하지 않는다.\n" +
            "- `questionSeq`는 모든 환경에서 1~20 고정이라 섹션 제목·placeholder는 앱이 `questionSeq`로 매핑한다.\n" +
            "- 조건부 문항(`condition`)은 기준 문항 답변이 조건에 맞을 때만 보이고, 숨김 문항의 답변은 서버가 저장·제출 때 지운다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "문항 목록",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionQuestionListSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "문항 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"questions":[{"questionSeq":1,"displayOrder":1,"questionText":"주거 형태","questionType":"SINGLE_CHOICE","isRequired":true,"options":["아파트","단독주택","다세대","오피스텔","기타"],"condition":null},{"questionSeq":20,"displayOrder":20,"questionText":"이수일","questionType":"DATE","isRequired":true,"options":null,"condition":{"depends_on":17,"operator":"EQ","value":"이수","required_when_matched":true}}]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
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
    @GetMapping("/adoption/questions")
    fun getQuestions() = adoptionUseCase.getQuestions()

    /** 12.2 내 입양 신청(정렬은 displayDttm desc 고정이라 sort는 무시) */
    @Operation(
        summary = "내 입양 신청 목록",
        description = "- USER만\n" +
            "- 본인 신청서를 모든 상태(DRAFT 포함)로 준다. `status`·`animalSeq`로 거른다.\n" +
            "- 정렬은 `displayDttm` 내림차순 고정(동률은 applicationSeq 내림차순). `sort` 파라미터는 무시한다. size 기본 20.\n" +
            "- `displayDttm`: DRAFT는 마지막 임시저장 시각, 그 외는 제출 시각.\n" +
            "- 신청 화면 진입 시 `?animalSeq={id}&status=DRAFT`로 이어 쓸 임시저장을 찾는다. 있으면 12.5(PATCH), 없으면 12.4(POST)로 저장한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "신청 목록. 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MyAdoptionApplicationPageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "내 신청 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"applicationSeq":902,"status":"DRAFT","displayDttm":"2026-10-30T09:00:00+09:00","submittedDttm":null,"rejectionReason":null,"animalSummary":""" + ANIMAL_SUMMARY_701 + """},{"applicationSeq":901,"status":"SUBMITTED","displayDttm":"2026-10-27T10:00:00+09:00","submittedDttm":"2026-10-27T10:00:00+09:00","rejectionReason":null,"animalSummary":""" + ANIMAL_SUMMARY + """}],"page":0,"size":20,"totalElements":2,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "status·animalSeq·page·size 타입 불일치(없는 상태값 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "타입 불일치", value = STATUS_TYPE_MISMATCH)],
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
            description = "SHELTER 계정으로 호출. 보호소는 12.9를 쓴다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping("/adoption-applications")
    @PreAuthorize("hasRole('USER')")
    fun getMine(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "상태 필터. 생략하면 전체. DRAFT·SUBMITTED·APPROVED·REJECTED·CANCELED·ADOPTED", example = "SUBMITTED")
        @RequestParam(required = false) status: ApplicationStatus?,
        @Parameter(description = "보호동물 번호 필터. 생략하면 전체", example = "700")
        @RequestParam(required = false) animalSeq: Int?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(adoptionUseCase.getMyApplications(user.userSeq, status, animalSeq, PageRequest.of(pageable.pageNumber, pageable.pageSize)))

    /** 12.9 소속 보호소 수신 신청 */
    @Operation(
        summary = "보호소 수신 입양 신청 목록",
        description = "- SHELTER만\n" +
            "- 토큰의 소속 보호소에 제출된 신청만 준다. 상태는 SUBMITTED(심사 대기)·APPROVED·ADOPTED·REJECTED만 나온다. DRAFT·CANCELED는 노출하지 않는다.\n" +
            "- `status`에 DRAFT·CANCELED를 주면 400. `animalSeq`가 다른 보호소 동물이면 빈 목록.\n" +
            "- sort 허용 필드: `submittedDttm`(기본, 내림차순), `regDttm`. size 기본 20.\n" +
            "- 탭별 건수는 `GET /api/core/v1/adoption-applications/received/status-counts`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수신 신청 목록. 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ReceivedAdoptionApplicationPageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "수신 신청 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"applicationSeq":901,"status":"SUBMITTED","applicantName":"김가람","submittedDttm":"2026-10-10T10:00:00+09:00","animalSummary":""" + ANIMAL_SUMMARY + """}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "status에 DRAFT·CANCELED 지정, 허용 밖 정렬 필드, 또는 파라미터 타입 불일치",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "조회할 수 없는 상태",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"SUBMITTED, APPROVED, ADOPTED, REJECTED만 조회할 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "정렬 필드 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: applicantName (허용: submittedDttm, regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "타입 불일치", value = STATUS_TYPE_MISMATCH),
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
            description = "USER 계정으로 호출",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping("/adoption-applications/received")
    @PreAuthorize("hasRole('SHELTER')")
    fun getReceived(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "상태 필터. SUBMITTED·APPROVED·ADOPTED·REJECTED만. 생략하면 네 상태 전체", example = "SUBMITTED")
        @RequestParam(required = false) status: ApplicationStatus?,
        @Parameter(description = "보호동물 번호 필터. 생략하면 소속 보호소 전체", example = "700")
        @RequestParam(required = false) animalSeq: Int?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(adoptionUseCase.getReceived(user, status, animalSeq, pageable))

    /** 12.16 상태별 건수 */
    @Operation(
        summary = "보호소 수신 입양 신청 상태별 건수",
        description = "- SHELTER만\n" +
            "- 소속 보호소 신청 목록 탭(전체·심사 대기·서류 승인·최종 승인·거절)의 숫자. 키는 `total`, `SUBMITTED`, `APPROVED`, `ADOPTED`, `REJECTED` 5개로 고정이고 없으면 0.\n" +
            "- DRAFT·CANCELED는 세지 않는다. `total`은 네 값의 합.\n" +
            "- `animalSeq`를 주면 그 동물만 센다. 다른 보호소 동물이거나 삭제된 동물이면 404 `ANIMAL_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "상태별 건수",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionStatusCountsSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "상태별 건수",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"total":23,"SUBMITTED":3,"APPROVED":16,"ADOPTED":2,"REJECTED":2},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "animalSeq 타입 불일치",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "타입 불일치", value = ANIMAL_SEQ_TYPE_MISMATCH)],
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
            description = "USER 계정으로 호출",
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
            description = "animalSeq가 없거나 삭제됐거나 다른 보호소 소속",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/adoption-applications/received/status-counts")
    @PreAuthorize("hasRole('SHELTER')")
    fun countReceived(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "보호동물 번호. 생략하면 소속 보호소 전체", example = "700")
        @RequestParam(required = false) animalSeq: Int?,
    ) = adoptionUseCase.countReceived(user, animalSeq)

    @Operation(
        summary = "입양 신청서 상세",
        description = "- 로그인 필요. 신청자 본인 또는 담당 보호소(SHELTER)만\n" +
            "- 신청서 전체(고정 필드 4개 + 문항 답변 + 현재 문항 목록 + 신청자 정보 + 심사 결과)를 한 번에 준다.\n" +
            "- `applicant`: 제출 전(DRAFT)은 현재 회원 정보, 제출 후는 제출 시점 스냅샷.\n" +
            "- `reviewer`: 심사 전이거나 자동 거절이면 null. `displayName`은 보호소명.\n" +
            "- 남의 신청서는 404로 존재를 감춘다. SHELTER는 소속 보호소의 제출된 신청서만 보이고 DRAFT·CANCELED는 404.\n" +
            "- 앱은 `status`로 하단 버튼을 그린다: DRAFT는 이어 쓰기·삭제(12.7), SUBMITTED는 제출 취소(12.8), REJECTED는 `rejectionReason` 표시.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "신청서 상세",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionApplicationDetailSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "제출된 신청서",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"userSeq":17,"status":"SUBMITTED","animalSummary":""" + ANIMAL_SUMMARY + ""","careAddress":"경상북도 구미시 구미로 123길 45-6","careAddressDetail":"구미아파트 7층 809호","email":"example@email.com","visitHopeDate":"2026-11-20","answers":{"1":"아파트","2":"가능"},"questions":[{"questionSeq":1,"questionText":"주거 형태","questionType":"SINGLE_CHOICE","displayOrder":1},{"questionSeq":2,"questionText":"반려동물 동거 가능 여부","questionType":"SINGLE_CHOICE","displayOrder":2}],"applicant":{"name":"김가람","birthDate":"1997-05-12","phone":"01012345678"},"submittedDttm":"2026-10-29T17:50:00+09:00","reviewedDttm":null,"reviewer":null,"rejectionReason":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "거절된 신청서",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"userSeq":17,"status":"REJECTED","animalSummary":""" + ANIMAL_SUMMARY + ""","careAddress":"경상북도 구미시 구미로 123길 45-6","careAddressDetail":null,"email":null,"visitHopeDate":"2026-11-20","answers":{"1":"아파트"},"questions":[{"questionSeq":1,"questionText":"주거 형태","questionType":"SINGLE_CHOICE","displayOrder":1}],"applicant":{"name":"김가람","birthDate":"1997-05-12","phone":"01012345678"},"submittedDttm":"2026-10-29T17:50:00+09:00","reviewedDttm":"2026-10-30T11:00:00+09:00","reviewer":{"userSeq":55,"displayName":"구미보호소"},"rejectionReason":"현재 보호 환경이 입양 조건에 부합하지 않습니다."},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "applicationSeq 타입 불일치",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH)],
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
            description = "없음, 남의 신청서, 다른 보호소 신청서, 또는 SHELTER가 DRAFT·CANCELED 조회(취소된 신청의 알림을 누른 경우 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/adoption-applications/{applicationSeq}")
    fun get(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
    ) = adoptionUseCase.getApplication(user, applicationSeq)

    /** 12.4 DRAFT 생성 */
    @Operation(
        summary = "입양 신청서 임시저장 생성(DRAFT)",
        description = "- USER만\n" +
            "- 이 동물의 첫 \"임시저장\"에서 호출한다. 이미 DRAFT가 있으면 12.5(PATCH)로 저장한다.\n" +
            "- 보호동물이 보호 중(PROTECTING)이고 입양 가능(AVAILABLE)일 때만 만든다. 아니면 409 `ADOPTION_ANIMAL_NOT_AVAILABLE`.\n" +
            "- 같은 회원·동물의 진행 중 신청(DRAFT·SUBMITTED·APPROVED)은 1건만. 있으면 409 `ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS`.\n" +
            "- 빈 `answers`·빈 고정 필드를 허용한다. 필수·희망일·이메일 형식은 제출(12.6) 때만 검사한다.\n" +
            "- `answers`: 키는 문자열 questionSeq. 없는 문항 키나 유형에 맞지 않는 값은 400 `ADOPTION_INVALID_ANSWER`. null 값과 숨김 문항 답변은 버린다.\n" +
            "- 길이 제한: careAddress 200자, careAddressDetail 100자, email 254자(앞뒤 공백 제거 후).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "DRAFT 생성",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionDraftCreatedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "DRAFT 생성",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"DRAFT","animalSeq":700},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "animalSeq 누락, 답변 키·형식 오류, 길이 초과, 또는 본문을 읽을 수 없음(visitHopeDate 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "animalSeq 누락", value = ANIMAL_SEQ_NULL),
                        ExampleObject(name = "ADOPTION_INVALID_ANSWER", value = INVALID_ANSWER),
                        ExampleObject(name = "길이 초과", value = LENGTH_EXCEEDED),
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
            description = "정지된 계정, 또는 SHELTER 계정으로 호출. 재시도하지 않는다",
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
            description = "보호동물이 없거나 삭제됨",
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
            description = "입양 신청을 받지 않는 동물, 또는 이 동물에 진행 중 신청(DRAFT·SUBMITTED·APPROVED)이 이미 있음. 12.2로 기존 신청을 찾는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "ADOPTION_ANIMAL_NOT_AVAILABLE", value = ANIMAL_NOT_AVAILABLE),
                        ExampleObject(name = "ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS", value = ACTIVE_APPLICATION_ALREADY_EXISTS),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/adoption-applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    fun createDraft(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: ApplicationFormRequest) =
        adoptionUseCase.createDraft(user.userSeq, request.animalSeq!!, request.toInput())

    /** 12.6 최종 저장 + 제출(animalSeq 기준, Q458) */
    @Operation(
        summary = "입양 신청서 제출",
        description = "- USER만\n" +
            "- 최종 내용 전체와 `animalSeq`를 보내면 한 트랜잭션으로 저장과 제출을 끝낸다. DRAFT 유무와 관계없이 항상 이 API 1회.\n" +
            "- 이 동물에 DRAFT가 있으면 그 행을 덮어써 SUBMITTED로, 없으면 새 행을 바로 SUBMITTED로 만든다. SUBMITTED·APPROVED가 이미 있으면 409.\n" +
            "- 보호동물이 PROTECTING + AVAILABLE이 아니면 409 `ADOPTION_ANIMAL_NOT_AVAILABLE`(기존 DRAFT는 남고, 다시 AVAILABLE이 되면 제출할 수 있다).\n" +
            "- 검사 순서: 보호동물 → 진행 중 신청 → 답변 키·형식(`ADOPTION_INVALID_ANSWER`) → 길이(`COMMON_INVALID_INPUT`) → 필수 누락(`ADOPTION_REQUIRED_ANSWER_MISSING`) → 희망일·이메일(`ADOPTION_INVALID_ANSWER`).\n" +
            "- 필수: 보이는 필수 문항(조건부 문항은 조건이 맞을 때 `required_when_matched`), `careAddress`, `visitHopeDate`. 누락 항목은 `errors[]`에 모두 담는다.\n" +
            "- `visitHopeDate`는 오늘(KST) 이후(오늘 포함). `email`은 비어 있으면 검사하지 않는다.\n" +
            "- 신청자 실명·생년월일·전화번호를 제출 시점으로 스냅샷한다. 제출 후 내용은 바꿀 수 없다.\n" +
            "- 커밋 후 담당 보호소 계정에 ADOPTION_SUBMITTED 알림. 버튼 중복 탭의 두 번째 요청은 409.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "제출 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionSubmittedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "제출 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"SUBMITTED","submittedDttm":"2026-10-29T17:50:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "animalSeq 누락, 답변 키·형식 오류, 길이 초과, 필수 누락, 과거 희망일·이메일 형식 오류, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "animalSeq 누락", value = ANIMAL_SEQ_NULL),
                        ExampleObject(name = "답변 키·형식 오류", value = INVALID_ANSWER),
                        ExampleObject(name = "길이 초과", value = LENGTH_EXCEEDED),
                        ExampleObject(
                            name = "ADOPTION_REQUIRED_ANSWER_MISSING",
                            value = """{"status":400,"code":"ADOPTION_REQUIRED_ANSWER_MISSING","message":"필수 항목이 비어 있습니다.","data":null,"errors":[{"field":"answers.3","reason":"필수 문항입니다."},{"field":"careAddress","reason":"필수 입력값입니다."},{"field":"visitHopeDate","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "희망일·이메일 오류",
                            value = """{"status":400,"code":"ADOPTION_INVALID_ANSWER","message":"답변 형식이 올바르지 않습니다.","data":null,"errors":[{"field":"visitHopeDate","reason":"방문 희망일은 오늘 이후여야 합니다."},{"field":"email","reason":"이메일 형식이 아닙니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
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
            description = "정지된 계정, 또는 SHELTER 계정으로 호출. 재시도하지 않는다",
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
            description = "보호동물이 없거나 삭제됨",
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
            description = "입양 신청을 받지 않는 동물(UNAVAILABLE·보호 종료), 또는 이미 SUBMITTED·APPROVED 신청이 있음(중복 탭 포함). 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "ADOPTION_ANIMAL_NOT_AVAILABLE", value = ANIMAL_NOT_AVAILABLE),
                        ExampleObject(name = "ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS", value = ACTIVE_APPLICATION_ALREADY_EXISTS),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/adoption-applications/submit")
    @PreAuthorize("hasRole('USER')")
    fun submit(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: ApplicationFormRequest) =
        adoptionUseCase.submit(user.userSeq, request.animalSeq!!, request.toInput())

    /** 12.5 DRAFT 저장 */
    @Operation(
        summary = "입양 신청서 임시저장(DRAFT 수정)",
        description = "- USER. 본인 신청서만\n" +
            "- DRAFT만 저장한다. 다른 상태면 409 `ADOPTION_APPLICATION_NOT_DRAFT`.\n" +
            "- `answers`: 보내면 객체 전체 교체, 생략하거나 null이면 기존 답변 유지. 모두 지우려면 `{}`.\n" +
            "- 고정 필드(careAddress·careAddressDetail·email·visitHopeDate): 생략하면 유지, `null`이면 비운다.\n" +
            "- 동물이 입양 불가로 바뀌어도 DRAFT 저장은 된다(제출만 막힘).\n" +
            "- 필수·희망일·이메일 형식은 검사하지 않는다(제출 때 검사). 답변 키·형식과 길이(200·100·254자)만 검사한다.\n" +
            "- \"다음\" 버튼은 저장하지 않는다. \"임시저장\"·\"프로필 편집\" 버튼에서만 호출한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "저장 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionDraftSavedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "저장 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"DRAFT","savedQuestionCount":2},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "applicationSeq 타입 불일치, 답변 키·형식 오류, 길이 초과, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "ADOPTION_INVALID_ANSWER", value = INVALID_ANSWER),
                        ExampleObject(name = "길이 초과", value = LENGTH_EXCEEDED),
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
            description = "정지된 계정, 또는 SHELTER 계정으로 호출. 재시도하지 않는다",
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
            description = "없음 또는 남의 신청서",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "DRAFT가 아님(이미 제출·취소·거절 등). 12.3으로 현재 상태를 다시 읽는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_DRAFT", value = APPLICATION_NOT_DRAFT)],
                ),
            ],
        ),
    )
    @PatchMapping("/adoption-applications/{applicationSeq}")
    @PreAuthorize("hasRole('USER')")
    fun saveDraft(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
        @RequestBody request: SaveDraftRequest,
    ) = adoptionUseCase.saveDraft(user.userSeq, applicationSeq, request.toPatch())

    /** 12.7 DRAFT 물리 삭제 */
    @Operation(
        summary = "입양 신청서 임시저장 삭제",
        description = "- USER. 본인 신청서만\n" +
            "- DRAFT만 DB에서 물리 삭제한다(답변 포함). 다른 상태면 409 `ADOPTION_APPLICATION_NOT_DRAFT`.\n" +
            "- 멱등 아님: 이미 삭제한 번호를 다시 보내면 404.\n" +
            "- 제출한 신청을 거두려면 12.8(제출 취소)을 쓴다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "applicationSeq 타입 불일치",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH)],
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
            description = "정지된 계정, 또는 SHELTER 계정으로 호출. 재시도하지 않는다",
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
            description = "없음(이미 삭제됨 포함) 또는 남의 신청서",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "DRAFT가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_DRAFT", value = APPLICATION_NOT_DRAFT)],
                ),
            ],
        ),
    )
    @DeleteMapping("/adoption-applications/{applicationSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    fun deleteDraft(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
    ) {
        adoptionUseCase.deleteDraft(user.userSeq, applicationSeq)
    }

    /** 12.8 제출 취소 */
    @Operation(
        summary = "입양 신청 제출 취소",
        description = "- USER. 본인 신청서만. 요청 본문 없음\n" +
            "- SUBMITTED에서만 CANCELED로 바꾼다. DRAFT·APPROVED·REJECTED·CANCELED·ADOPTED는 409 `ADOPTION_APPLICATION_NOT_SUBMITTED`(승인 후 직접 취소는 없다).\n" +
            "- 멱등 아님: 이미 취소한 신청을 다시 취소하면 409.\n" +
            "- 취소한 신청은 보호소 목록·상세·건수에서 사라진다. 같은 동물에 다시 신청하면 새 신청서가 된다.\n" +
            "- 알림은 보내지 않는다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "취소 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionCanceledSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "취소 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"CANCELED"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "applicationSeq 타입 불일치",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH)],
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
            description = "정지된 계정, 또는 SHELTER 계정으로 호출. 재시도하지 않는다",
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
            description = "없음 또는 남의 신청서",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "SUBMITTED가 아님(이미 심사됐거나 취소됨). 12.3으로 현재 상태를 다시 읽는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_SUBMITTED", value = APPLICATION_NOT_SUBMITTED)],
                ),
            ],
        ),
    )
    @PostMapping("/adoption-applications/{applicationSeq}/cancel")
    @PreAuthorize("hasRole('USER')")
    fun cancel(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
    ) = adoptionUseCase.cancel(user.userSeq, applicationSeq)

    /** 12.10 서류 심사 */
    @Operation(
        summary = "입양 신청 서류 심사",
        description = "- SHELTER만. 소속 보호소 신청서만\n" +
            "- SUBMITTED에서만 심사한다. `APPROVE`면 APPROVED, `REJECT`면 REJECTED. 다른 상태면 409 `ADOPTION_APPLICATION_NOT_SUBMITTED`.\n" +
            "- `REJECT`는 공백이 아닌 `rejectionReason` 필수(앞뒤 공백 제거 후 저장). `APPROVE`면 `rejectionReason`은 무시한다.\n" +
            "- 검사 순서: decision 값 → 신청서(404) → 상태(409) → 거절 사유(400).\n" +
            "- 여러 신청서를 동시에 승인할 수 있다. 승인만으로 보호동물을 ADOPTED로 바꾸지 않는다(최종 승인은 12.11).\n" +
            "- 신청자에게 ADOPTION_APPROVED 또는 ADOPTION_REJECTED 알림. 승인 후 거절은 12.13을 쓴다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "심사 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionReviewedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "승인",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"APPROVED","reviewedDttm":"2026-10-29T17:50:00+09:00","reviewedUsrSeq":55},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "거절",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"REJECTED","reviewedDttm":"2026-10-29T17:50:00+09:00","reviewedUsrSeq":55},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "decision 누락·허용 밖 값, REJECT인데 거절 사유 없음, applicationSeq 타입 불일치, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "decision 누락",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"decision","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "decision 허용 밖 값",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"decision","reason":"허용되지 않는 값입니다: HOLD"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "ADOPTION_REJECTION_REASON_REQUIRED", value = REJECTION_REASON_REQUIRED),
                        ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH),
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
            description = "정지된 계정, 또는 USER 계정으로 호출. 재시도하지 않는다",
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
            description = "없음, 다른 보호소 신청서, 또는 DRAFT·CANCELED 신청서",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "SUBMITTED가 아님(이미 심사됨). 12.3으로 현재 상태를 다시 읽는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_SUBMITTED", value = APPLICATION_NOT_SUBMITTED)],
                ),
            ],
        ),
    )
    @PostMapping("/adoption-applications/{applicationSeq}/review")
    @PreAuthorize("hasRole('SHELTER')")
    fun review(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
        @Valid @RequestBody request: ReviewRequest,
    ) = adoptionUseCase.review(user, applicationSeq, request.decision.toEnum<ReviewDecision>("decision")!!, request.rejectionReason)

    /** 12.11 실제 입양 완료 */
    @Operation(
        summary = "실제 입양 완료(최종 승인)",
        description = "- SHELTER만. 소속 보호소 신청서만. 요청 본문 없음\n" +
            "- APPROVED 신청서만 채택한다. 다른 상태면 409 `ADOPTION_APPLICATION_NOT_APPROVED`.\n" +
            "- 한 트랜잭션: 신청서 ADOPTED → 보호동물 보호 상태·입양 상태 ADOPTED, 보호 종료 처리, 채택 신청서 연결 → 같은 동물의 다른 진행 중 신청(DRAFT·SUBMITTED·APPROVED) 자동 거절.\n" +
            "- 보호동물이 11.6으로 먼저 ADOPTED가 됐어도 연결된 신청서가 없으면 채택할 수 있다. 이미 다른 신청서가 연결돼 있으면 409 `ADOPTION_ANIMAL_ALREADY_FINISHED`.\n" +
            "- 반환·보호 종료된 동물의 신청은 이미 자동 거절돼 409 `ADOPTION_APPLICATION_NOT_APPROVED`.\n" +
            "- 자동 거절된 수는 `otherApplicationsAutoRejected`. 채택 신청자에게 ADOPTION_COMPLETED, 자동 거절된 신청자 각각에게 ADOPTION_REJECTED 알림.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "입양 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionCompletedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "입양 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"applicationStatus":"ADOPTED","animalSeq":700,"protectionStatus":"ADOPTED","adoptionStatus":"ADOPTED","completedApplicationSeq":901,"otherApplicationsAutoRejected":3},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "applicationSeq 타입 불일치",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH)],
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
            description = "정지된 계정, 또는 USER 계정으로 호출. 재시도하지 않는다",
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
            description = "신청서 없음·다른 보호소·DRAFT·CANCELED, 또는 대상 보호동물이 삭제됨",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND),
                        ExampleObject(name = "ANIMAL_NOT_FOUND", value = ANIMAL_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "APPROVED가 아님(반환·보호 종료로 자동 거절된 경우 포함), 또는 보호동물에 이미 다른 신청서가 채택돼 있음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "ADOPTION_APPLICATION_NOT_APPROVED", value = APPLICATION_NOT_APPROVED),
                        ExampleObject(
                            name = "ADOPTION_ANIMAL_ALREADY_FINISHED",
                            value = """{"status":409,"code":"ADOPTION_ANIMAL_ALREADY_FINISHED","message":"이미 다른 신청서로 입양이 완료된 보호동물입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/adoption-applications/{applicationSeq}/complete")
    @PreAuthorize("hasRole('SHELTER')")
    fun complete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "채택할 입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
    ) = adoptionUseCase.complete(user, applicationSeq)

    /** 12.13 승인 후 진행 실패 */
    @Operation(
        summary = "승인 후 입양 진행 실패",
        description = "- SHELTER만. 소속 보호소 신청서만\n" +
            "- APPROVED 신청서를 REJECTED로 바꾸고 `reviewedDttm`을 지금으로 갱신한다. 다른 상태면 409 `ADOPTION_APPLICATION_NOT_APPROVED`.\n" +
            "- 공백이 아닌 `rejectionReason` 필수(앞뒤 공백 제거 후 저장). 본문은 반드시 보낸다(본문이 없으면 400 `COMMON_INVALID_JSON`).\n" +
            "- 검사 순서: 신청서(404) → 상태(409) → 사유(400).\n" +
            "- 보호동물 상태는 바꾸지 않는다. 신청자에게 ADOPTION_REJECTED 알림.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "실패 처리 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AdoptionFailedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "실패 처리",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"applicationSeq":901,"status":"REJECTED","rejectionReason":"승인 이후 입양 진행 과정이 완료되지 못했습니다."},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "실패 사유 없음·공백, applicationSeq 타입 불일치, 또는 본문 없음·읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "ADOPTION_REJECTION_REASON_REQUIRED", value = REJECTION_REASON_REQUIRED),
                        ExampleObject(name = "타입 불일치", value = APPLICATION_SEQ_TYPE_MISMATCH),
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
            description = "정지된 계정, 또는 USER 계정으로 호출. 재시도하지 않는다",
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
            description = "없음, 다른 보호소 신청서, 또는 DRAFT·CANCELED 신청서",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "APPROVED가 아님(심사 전·이미 거절·입양 완료 등). 12.3으로 현재 상태를 다시 읽는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_APPROVED", value = APPLICATION_NOT_APPROVED)],
                ),
            ],
        ),
    )
    @PostMapping("/adoption-applications/{applicationSeq}/fail")
    @PreAuthorize("hasRole('SHELTER')")
    fun fail(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901")
        @PathVariable applicationSeq: Long,
        @RequestBody request: FailRequest,
    ) = adoptionUseCase.fail(user, applicationSeq, request.rejectionReason)

    private companion object {
        // 문서 예시 전용
        const val ANIMAL_SUMMARY =
            """{"animalSeq":700,"speciesName":"개","breedName":"믹스견","colorName":"갈색","gender":"FEMALE","age":"2026년생","weight":"3kg","isNeutered":true,"microchipNumber":"410987654321098","registrationNumber":null,"representativeImageUrl":"https://cdn.example.com/images/animal-700.jpg","shelterSeq":7,"shelterName":"구미보호소","shelterAddress":"경북 구미시 3공단 3로 302"}"""
        const val ANIMAL_SUMMARY_701 =
            """{"animalSeq":701,"speciesName":"고양이","breedName":"코리안숏헤어","colorName":"치즈","gender":"MALE","age":"2025년생","weight":"4kg","isNeutered":false,"microchipNumber":null,"registrationNumber":null,"representativeImageUrl":null,"shelterSeq":7,"shelterName":"구미보호소","shelterAddress":"경북 구미시 3공단 3로 302"}"""
        const val STATUS_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"animalSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val APPLICATION_SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"applicationSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_SEQ_NULL =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"animalSeq","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val LENGTH_EXCEEDED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"careAddress","reason":"200자 이하로 입력해 주세요."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_ANSWER =
            """{"status":400,"code":"ADOPTION_INVALID_ANSWER","message":"답변 형식이 올바르지 않습니다.","data":null,"errors":[{"field":"answers.1","reason":"답변 형식이 올바르지 않습니다."},{"field":"answers.99","reason":"없는 문항입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REJECTION_REASON_REQUIRED =
            """{"status":400,"code":"ADOPTION_REJECTION_REASON_REQUIRED","message":"거절 사유를 입력해 주세요.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ADOPTION_APPLICATION_NOT_FOUND =
            """{"status":404,"code":"ADOPTION_APPLICATION_NOT_FOUND","message":"입양 신청서를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_NOT_FOUND =
            """{"status":404,"code":"ANIMAL_NOT_FOUND","message":"보호동물을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ANIMAL_NOT_AVAILABLE =
            """{"status":409,"code":"ADOPTION_ANIMAL_NOT_AVAILABLE","message":"지금은 입양 신청을 받지 않는 보호동물입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ACTIVE_APPLICATION_ALREADY_EXISTS =
            """{"status":409,"code":"ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS","message":"이미 진행 중인 입양 신청이 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val APPLICATION_NOT_DRAFT =
            """{"status":409,"code":"ADOPTION_APPLICATION_NOT_DRAFT","message":"임시저장 상태의 신청서만 처리할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val APPLICATION_NOT_SUBMITTED =
            """{"status":409,"code":"ADOPTION_APPLICATION_NOT_SUBMITTED","message":"제출된 신청서만 처리할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val APPLICATION_NOT_APPROVED =
            """{"status":409,"code":"ADOPTION_APPLICATION_NOT_APPROVED","message":"승인된 신청서만 처리할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

/** 12.4·12.6 본문 */
data class ApplicationFormRequest(
    @field:Schema(description = "신청 대상 보호동물 번호", example = "700")
    @field:NotNull val animalSeq: Int? = null,
    @field:Schema(
        description = "문항 답변. 키는 문자열 questionSeq, 값은 문자열(TEXT·SINGLE_CHOICE·DATE \"YYYY-MM-DD\") 또는 문자열 배열(MULTI_CHOICE). " +
            "null 값과 숨김 문항 답변은 버린다. 생략하면 빈 객체",
        nullable = true,
        example = "{\"1\": \"아파트\", \"2\": \"가능\"}",
    )
    val answers: Map<String, Any?>? = null,
    @field:Schema(description = "양육 장소 주소. 최대 200자. 제출 때 필수", nullable = true, example = "경상북도 구미시 구미로 123길 45-6")
    val careAddress: String? = null,
    @field:Schema(description = "양육 장소 상세 주소. 최대 100자. 선택", nullable = true, example = "구미아파트 7층 809호")
    val careAddressDetail: String? = null,
    @field:Schema(description = "연락 이메일. 최대 254자. 선택이고 제출 때 값이 있으면 형식 검사", nullable = true, example = "example@email.com")
    val email: String? = null,
    @field:Schema(description = "방문 희망일(YYYY-MM-DD). 제출 때 필수이고 오늘(KST) 이후", nullable = true, example = "2026-11-20")
    val visitHopeDate: LocalDate? = null,
) {
    fun toInput() = FormInput(answers.orEmpty(), careAddress?.trim(), careAddressDetail?.trim(), email?.trim(), visitHopeDate)
}

/** 12.5: 필드 미전달은 null(유지), 명시적 null은 Optional.empty()(비움) */
data class SaveDraftRequest(
    @field:Schema(
        description = "문항 답변 전체. 보내면 객체 전체 교체, 생략하거나 null이면 기존 답변 유지. 키·값 규칙은 12.4와 같다",
        nullable = true,
        example = "{\"1\": \"아파트\", \"2\": \"가능\"}",
    )
    val answers: Map<String, Any?>? = null,
    @field:Schema(description = "양육 장소 주소. 최대 200자. 생략하면 유지, null이면 비움", nullable = true, example = "경상북도 구미시 구미로 123길 45-6")
    val careAddress: Optional<String>? = null,
    @field:Schema(description = "양육 장소 상세 주소. 최대 100자. 생략하면 유지, null이면 비움", nullable = true, example = "구미아파트 7층 809호")
    val careAddressDetail: Optional<String>? = null,
    @field:Schema(description = "연락 이메일. 최대 254자. 생략하면 유지, null이면 비움", nullable = true, example = "example@email.com")
    val email: Optional<String>? = null,
    @field:Schema(description = "방문 희망일(YYYY-MM-DD). 생략하면 유지, null이면 비움", nullable = true, example = "2026-11-20")
    val visitHopeDate: Optional<LocalDate>? = null,
) {
    fun toPatch() = FormPatch(answers, careAddress, careAddressDetail, email, visitHopeDate)
}

data class ReviewRequest(
    @field:Schema(description = "심사 결과. APPROVE: 서류 승인, REJECT: 서류 거절", example = "REJECT", allowableValues = ["APPROVE", "REJECT"])
    @field:NotNull val decision: String? = null,
    @field:Schema(description = "거절 사유(자연어). REJECT면 공백이 아닌 값 필수, APPROVE면 무시", nullable = true, example = "현재 보호 환경이 입양 조건에 부합하지 않습니다.")
    val rejectionReason: String? = null,
)

data class FailRequest(
    @field:Schema(description = "실패 사유(자연어). 공백이 아닌 값 필수", example = "승인 이후 입양 진행 과정이 완료되지 못했습니다.")
    val rejectionReason: String? = null,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class AdoptionQuestionListSuccessSchema : SuccessResponseSchema<QuestionList>()
abstract class MyAdoptionApplicationPageSuccessSchema : SuccessResponseSchema<PageResponse<MyApplicationItem>>()
abstract class ReceivedAdoptionApplicationPageSuccessSchema : SuccessResponseSchema<PageResponse<ReceivedItem>>()
abstract class AdoptionStatusCountsSuccessSchema : SuccessResponseSchema<Map<String, Long>>()
abstract class AdoptionApplicationDetailSuccessSchema : SuccessResponseSchema<ApplicationDetail>()
abstract class AdoptionDraftCreatedSuccessSchema : SuccessResponseSchema<DraftCreated>()
abstract class AdoptionDraftSavedSuccessSchema : SuccessResponseSchema<DraftSaved>()
abstract class AdoptionSubmittedSuccessSchema : SuccessResponseSchema<Submitted>()
abstract class AdoptionCanceledSuccessSchema : SuccessResponseSchema<StatusChanged>()
abstract class AdoptionReviewedSuccessSchema : SuccessResponseSchema<Reviewed>()
abstract class AdoptionCompletedSuccessSchema : SuccessResponseSchema<Completed>()
abstract class AdoptionFailedSuccessSchema : SuccessResponseSchema<Failed>()
