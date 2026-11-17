package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.ABANDONED_REPORT_JSON
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.application.port.`in`.SimulationReportQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import com.ssafy.animal.common.response.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 18.18~18.20 리포트. 본인은 종료·중간 리포트와 내 목록, 보호소는 종료 리포트와 전체 목록(Q483) */
@Tag(
    name = "모의 입양 리포트",
    description = "세션이 끝나면 한 번 만들어 영구 보존하는 종료 리포트(지출·최종 스탯·행동·이벤트 수·요약). " +
        "본인은 진행 중 세션의 중간 리포트도 볼 수 있고, 보호소는 끝난 세션의 리포트를 누구 것이든 본다",
)
@RestController
@RequestMapping("/api/simulation/v1/simulations")
class SimulationReportController(
    private val reportQueryUseCase: SimulationReportQueryUseCase,
) {

    /** 18.18 리포트. 역할에 따라 볼 수 있는 범위가 달라 역할 제한을 두지 않고 서비스에서 404로 감춘다 */
    @Operation(
        summary = "모의 입양 리포트",
        description = "- 본인 세션: 끝났으면 저장된 종료 리포트(`isFinal: true`), 진행 중이면 지금까지로 계산한 중간 리포트" +
            "(`isFinal: false`, `endedDttm`·`summary`는 null, 저장하지 않음).\n" +
            "- 진행 중이면 조회 시점까지 스탯을 계산해 저장하고, 건강 0·예정 종료 경과면 그 자리에서 FAILED·COMPLETED로 끝내고 종료 리포트를 준다.\n" +
            "- 보호소: 끝난 세션의 리포트를 누구 것이든 본다. 진행 중 세션은 404.\n" +
            "- 그 외(남의 세션·없는 세션): 404 `SIMULATION_NOT_FOUND`(존재를 감춤).\n" +
            "- `summary`는 종료 직후 null이며 잠시 뒤 채워진다. 앱은 \"요약 준비 중\"으로 표시하다가 다시 조회한다. " +
            "종료 푸시는 요약이 채워진 뒤 보내므로 푸시로 들어오면 완성된 리포트를 본다.\n" +
            "- `spentByCategory`는 물품 분류 14종 + 진료비 `EVENT` 15개 키, `dailySpent`는 `\"1\"`~`\"7\"` 일차 키를 모두 채운다(0 포함).\n" +
            "- `actionCount`는 돌봄·산책 행동 수, `eventCount`는 발생한 돌발 이벤트 수(해결 + 만료), `walkCount`는 고양이 세션이면 0.\n" +
            "- `summary`는 일반 텍스트이고 문단은 빈 줄로 나뉜다(결과·잘한 점·아쉬운 점·실제 입양 조언 네 문단이 기본, 빈 문단은 빠질 수 있음, Q521). 줄바꿈을 살려 그리고 문단 수를 가정하지 않는다.\n" +
            "- `evaluatedDays`·`careGuideline`·`dailyCare`(Q517)는 저장하지 않고 조회할 때 행동 이력에서 계산한다. 표는 `careGuideline` 키를 행, " +
            "`\"1\"`~`\"7\"`을 열로 그리고 일차 > `evaluatedDays`면 빈칸, 횟수 ≥ 권장이면 충족, 0이면 없음, 그 사이는 부족. 고양이 세션은 `WALK` 키가 없다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SimulationReportSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "종료 리포트(요약 생성 후)", value = FINAL_SUCCESS),
                        ExampleObject(name = "종료 리포트(요약 준비 중)", value = FINAL_PENDING_SUMMARY),
                        ExampleObject(name = "진행 중 중간 리포트", value = INTERIM_SUCCESS),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "세션 번호가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH)],
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
            responseCode = "404",
            description = "없는 세션·남의 세션(USER), 진행 중 세션(보호소)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{sessionSeq}/report")
    fun getReport(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
    ) = SimulationReportResponse.from(reportQueryUseCase.getReport(user.userSeq, user.role, sessionSeq))

    /** 18.19 내 종료 리포트 목록 */
    @Operation(
        summary = "내 모의 입양 리포트 목록",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 끝난 세션(COMPLETED·ABANDONED·FAILED)의 종료 리포트만 준다. 진행 중 세션은 리포트가 없어 나오지 않는다(진행 중은 진행 중 조회·리포트 조회로 본다).\n" +
            "- 항목은 요약 정보만이고, 지출 내역·스탯·요약 문구는 리포트 조회로 본다.\n" +
            "- size 기본 20. sort 허용 필드는 `endedDttm`뿐(기본 `endedDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 리포트가 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ReportListPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = MY_LIST_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "정렬 필드 오류", value = SORT_ENDED_DTTM_ONLY)],
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
            description = "보호소(SHELTER) 계정. 보호소는 보호소용 리포트 목록을 쓴다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping("/reports")
    @PreAuthorize("hasRole('USER')")
    fun getMyReports(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(reportQueryUseCase.getMyReports(user.userSeq, pageable).map(ReportListItemResponse::from))

    /** 18.20 보호소용 종료 리포트 전체 목록(Q483) */
    @Operation(
        summary = "보호소용 모의 입양 리포트 목록",
        description = "- SHELTER만(일반 회원은 403 `AUTH_FORBIDDEN`)\n" +
            "- 끝난 세션의 종료 리포트를 누구 것이든 준다(소속·보호동물과 무관). 진행 중 세션은 나오지 않는다.\n" +
            "- `userSeq`를 주면 그 회원의 리포트만(입양 신청자 확인용). 생략하면 전체.\n" +
            "- `user`는 회원 표시 정보. 탈퇴한 회원의 리포트도 남아 있으며 이때 `displayName`은 `(탈퇴한 사용자)`, `profileImage`는 null.\n" +
            "- 상세(지출·스탯·요약)는 리포트 조회로 본다.\n" +
            "- size 기본 20. sort 허용 필드는 `endedDttm`뿐(기본 `endedDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 리포트가 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShelterReportListPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = SHELTER_LIST_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "회원 번호가 숫자가 아님, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "회원 번호 타입 오류", value = USER_SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "정렬 필드 오류", value = SORT_ENDED_DTTM_ONLY),
                    ],
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
    )
    @GetMapping("/reports/shelter")
    @PreAuthorize("hasRole('SHELTER')")
    fun getReportsForShelter(
        @Parameter(description = "회원 번호. 주면 그 회원의 리포트만, 생략하면 전체", example = "17")
        @RequestParam(required = false) userSeq: Long?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(reportQueryUseCase.getReportsForShelter(userSeq, pageable).map(ShelterReportListItemResponse::from))

    private companion object {
        // 문서 예시 전용
        const val LIST_ITEM_FIELDS =
            """"animalName":"초코","breed":""" + SimulationDocExamples.BREED_JSON +
                ""","status":"COMPLETED","startedDttm":"2026-11-01T14:00:00+09:00","endedDttm":"2026-11-08T14:00:00+09:00",""" +
                """"expectedCost":150000,"totalSpent":182000,"difference":-32000"""
        const val MY_LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"sessionSeq":3001,""" + LIST_ITEM_FIELDS + """}],""" +
                """"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SHELTER_LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[""" +
                """{"sessionSeq":3001,"user":{"userSeq":17,"displayName":"반려가족","role":"USER",""" +
                """"profileImage":{"imageSeq":1301,"imageUrl":"https://storage.example.com/images/2026/11/3f1c2a9e.jpg"}},""" + LIST_ITEM_FIELDS + """},""" +
                """{"sessionSeq":2950,"user":{"userSeq":18,"displayName":"(탈퇴한 사용자)","role":"USER","profileImage":null},""" +
                """"animalName":"나비","breed":{"breedSeq":31,"breedName":"코리안 숏헤어","speciesType":"CAT","representativeImageUrl":null},""" +
                """"status":"ABANDONED","startedDttm":"2026-10-20T10:00:00+09:00","endedDttm":"2026-10-22T09:30:00+09:00",""" +
                """"expectedCost":120000,"totalSpent":45000,"difference":75000}],""" +
                """"page":0,"size":20,"totalElements":2,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SORT_ENDED_DTTM_ONLY =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: startedDttm (허용: endedDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val USER_SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"userSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""

        const val SUMMARY_TEXT =
            "2일째에 초코의 돌봄을 그만뒀어요. 첫날 기구를 갖추고 밥을 세 번 챙겼어요.\\n\\n" +
                "1일차에 밥 3번을 권장 횟수대로 줬고 사료를 미리 사 둬서 예산도 여유 있게 썼어요.\\n\\n" +
                "2일차에는 물과 놀이가 권장보다 적었어요. 실제로는 물이 부족하면 금방 지치고 소변 건강에도 좋지 않아요.\\n\\n" +
                "실제 입양에서는 물을 하루 종일 언제든 마실 수 있게 두는 게 기본이에요. 물을 하루 3회 챙겨 보세요."
        const val FINAL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":""" +
                """{"sessionSeq":3001,"status":"ABANDONED","isFinal":true,"animalName":"초코","breed":""" + SimulationDocExamples.BREED_JSON +
                ""","speciesType":"DOG","personality":"FRIENDLY","startedDttm":"2026-11-01T14:00:00+09:00","endedDttm":"2026-11-03T10:20:00+09:00",""" +
                """"expectedCost":150000,"totalSpent":52800,"difference":97200,""" +
                """"spentByCategory":{"FOOD_BOWL":10000,"WATER_BOWL":10000,"LITTER_BOX":0,"TOY":0,"FURNITURE":0,"LEASH":0,"GROOMING_KIT":0,""" +
                """"FOOD":21200,"SNACK":0,"LITTER":0,"SHAMPOO":0,"MEDICINE":0,"POOP_BAG":0,"CATNIP":0,"EVENT":11600},""" +
                """"dailySpent":{"1":31200,"2":21600,"3":0,"4":0,"5":0,"6":0,"7":0},""" +
                """"finalStats":{"satiety":55,"hydration":60,"cleanliness":70,"bond":42,"calmness":52,"health":84,"energy":58},""" +
                """"actionCount":12,"eventCount":0,"eventResolvedCount":0,"walkCount":0,"summary":"""" + SUMMARY_TEXT + """",""" +
                SimulationDocExamples.ABANDONED_CARE_JSON + "}" +
                ""","errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val FINAL_PENDING_SUMMARY =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":""" + ABANDONED_REPORT_JSON +
                ""","errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INTERIM_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":""" +
                """{"sessionSeq":3002,"status":"IN_PROGRESS","isFinal":false,"animalName":"보리","breed":""" + SimulationDocExamples.BREED_JSON +
                ""","speciesType":"DOG","personality":"FRIENDLY","startedDttm":"2026-11-05T09:00:00+09:00","endedDttm":null,""" +
                """"expectedCost":150000,"totalSpent":20000,"difference":130000,""" +
                """"spentByCategory":{"FOOD_BOWL":10000,"WATER_BOWL":10000,"LITTER_BOX":0,"TOY":0,"FURNITURE":0,"LEASH":0,"GROOMING_KIT":0,""" +
                """"FOOD":0,"SNACK":0,"LITTER":0,"SHAMPOO":0,"MEDICINE":0,"POOP_BAG":0,"CATNIP":0,"EVENT":0},""" +
                """"dailySpent":{"1":20000,"2":0,"3":0,"4":0,"5":0,"6":0,"7":0},""" +
                """"finalStats":{"satiety":62,"hydration":55,"cleanliness":76,"bond":29,"calmness":44,"health":90,"energy":51},""" +
                """"actionCount":3,"eventCount":0,"eventResolvedCount":0,"walkCount":0,"summary":null,""" +
                """"evaluatedDays":1,"careGuideline":{"FEED":3,"WATER":3,"CLEAN_LITTER":2,"PLAY":2,"WALK":1},"dailyCare":{"1":{"FEED":1,"WATER":1,"CLEAN_LITTER":0,"PLAY":1,"WALK":0},"2":{"FEED":0,"WATER":0,"CLEAN_LITTER":0,"PLAY":0,"WALK":0},"3":{"FEED":0,"WATER":0,"CLEAN_LITTER":0,"PLAY":0,"WALK":0},"4":{"FEED":0,"WATER":0,"CLEAN_LITTER":0,"PLAY":0,"WALK":0},"5":{"FEED":0,"WATER":0,"CLEAN_LITTER":0,"PLAY":0,"WALK":0},"6":{"FEED":0,"WATER":0,"CLEAN_LITTER":0,"PLAY":0,"WALK":0},"7":{"FEED":0,"WATER":0,"CLEAN_LITTER":0,"PLAY":0,"WALK":0}}}""" +
                ""","errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
