package com.ssafy.animal.core.adoption.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantSimulationReport
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantSimulationReportUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 12.15(= 18.21) 입양 신청자의 모의 입양 종료 리포트. 리포트는 simulation 스키마를 읽기 전용으로 조회한다(Q483) */
@Tag(name = "입양")
@RestController
@RequestMapping("/api/core/v1")
class ApplicantSimulationReportController(
    private val reportUseCase: ApplicantSimulationReportUseCase,
) {

    @Operation(
        summary = "입양 신청자의 모의 입양 리포트 목록",
        description = "- SHELTER만(일반 회원은 403 `AUTH_FORBIDDEN`)\n" +
            "- 소속 보호소의 제출된 신청서만. 없음·다른 보호소·DRAFT·CANCELED 신청서는 404 `ADOPTION_APPLICATION_NOT_FOUND`(존재를 감춤).\n" +
            "- 신청자가 끝낸 모의 입양(7일 완료·포기·돌봄 부족 중단)의 종료 리포트를 **동물·품종과 상관없이 전부** 최근 종료 순으로 준다. " +
            "진행 중인 모의 입양은 나오지 않는다. 신청자가 탈퇴했어도 남은 리포트는 준다.\n" +
            "- 페이지 없이 전량 배열이며 리포트가 없으면 빈 배열.\n" +
            "- 항목은 동물 정보(가상 이름·품종)·기간·총지출만. 지출 내역·스탯·요약은 `sessionSeq`로 모의 입양 리포트 조회(18.18)를 부른다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 리포트가 없으면 data가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApplicantSimulationReportListSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "리포트 있음", value = LIST_SUCCESS),
                        ExampleObject(name = "리포트 없음", value = EMPTY_SUCCESS),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "신청 번호가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "신청 번호 타입 오류", value = APPLICATION_SEQ_TYPE_MISMATCH)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 토큰 갱신 후 재시도",
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
            description = "일반 회원(USER) 계정. 재시도하지 않는다",
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
            description = "없는 신청서, 다른 보호소 신청서, DRAFT·CANCELED 신청서",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "ADOPTION_APPLICATION_NOT_FOUND", value = ADOPTION_APPLICATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/adoption-applications/{applicationSeq}/simulation-reports")
    @PreAuthorize("hasRole('SHELTER')")
    fun getApplicantReports(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "입양 신청 번호", example = "901") @PathVariable applicationSeq: Long,
    ): List<ApplicantSimulationReport> = reportUseCase.getApplicantReports(user, applicationSeq)

    private companion object {
        // 문서 예시 전용
        const val LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[""" +
                """{"sessionSeq":3001,"animalName":"초코","breed":{"breedSeq":21,"breedName":"말티즈","speciesType":"DOG","representativeImageUrl":"https://cdn.example.com/breeds/21.jpg"},""" +
                """"startedDttm":"2026-11-01T14:00:00+09:00","endedDttm":"2026-11-08T14:00:00+09:00","totalSpent":182000},""" +
                """{"sessionSeq":2950,"animalName":"나비","breed":{"breedSeq":31,"breedName":"코리안 숏헤어","speciesType":"CAT","representativeImageUrl":null},""" +
                """"startedDttm":"2026-10-20T10:00:00+09:00","endedDttm":"2026-10-22T09:30:00+09:00","totalSpent":45000}""" +
                """],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val EMPTY_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val APPLICATION_SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"applicationSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ADOPTION_APPLICATION_NOT_FOUND =
            """{"status":404,"code":"ADOPTION_APPLICATION_NOT_FOUND","message":"입양 신청서를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ApplicantSimulationReportListSuccessSchema : SuccessResponseSchema<List<ApplicantSimulationReport>>()
