package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_EQUIPMENT_MISSING
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_ITEM_NOT_IN_INVENTORY
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_IN_PROGRESS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_VET_NOT_NEEDED
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_SHOP_ITEM_NOT_FOUND
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.CareActionResult
import com.ssafy.animal.simulation.application.port.`in`.CareActionUseCase
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

internal const val CARE_TAG = "모의 입양 돌봄"
internal const val CARE_TAG_DESC = "밥·물·배변·목욕 등 돌봄 행동과 행동 이력. 행동마다 필요한 기구를 보유해야 하고, 소모품은 1개씩 쓴다"
internal const val ACTION_TYPE_DESC = "행동 분류. CARE: 돌봄, WALK: 산책, EVENT: 이벤트 만료, SYSTEM: 세션 시작·종료"
internal const val ACTION_CODE_DESC = "행동 코드. CARE: FEED·WATER·CLEAN_LITTER·BATHE·PLAY·SNACK·MEDICINE·GIVE_CATNIP·VET_VISIT, " +
    "WALK: WALK_START·WALK_END·GIVE_WATER·GIVE_SNACK, EVENT: EVENT_EXPIRE, SYSTEM: SESSION_START·SESSION_END"

@Tag(name = CARE_TAG, description = CARE_TAG_DESC)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class CareActionController(
    private val careActionUseCase: CareActionUseCase,
) {

    /** 18.6 돌봄 행동. 본인만 하며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @Operation(
        summary = "돌봄 행동",
        description = "- 본인 진행 중 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`, 진행 중이 아니면 409\n" +
            "- 행동 전에 지난 시간만큼 스탯을 먼저 계산하고, 그다음 효과를 더한다(0~100으로 자름).\n" +
            "- 계산해 보니 건강 0·예정 종료 경과면 세션을 그 자리에서 끝내고(종료는 저장됨) 409 `SIMULATION_NOT_IN_PROGRESS`. 앱은 진행 중 조회를 다시 불러 리포트로 이동한다.\n" +
            "- 지원 행동과 조건:\n" +
            "  - `FEED`: 밥그릇 보유 + 사료(FOOD) 1개, `itemSeq` 필수\n" +
            "  - `WATER`: 물그릇 보유, 물은 무료(수분 +50, 가안), `itemSeq` 생략\n" +
            "  - `CLEAN_LITTER`: 화장실(고양이 화장실·강아지 배변판) 보유 + 배변용품(LITTER) 1개, `itemSeq` 필수\n" +
            "  - `BATHE`: 위생 관리 키트 보유, 소모 없이 무제한(키트 효과), `itemSeq` 생략\n" +
            "  - `SNACK`: 기구 없이 간식(SNACK) 1개, `itemSeq` 필수\n" +
            "  - `MEDICINE`: 기구 없이 약(MEDICINE) 1개, `itemSeq` 필수\n" +
            "  - `PLAY`: 장난감 1개 이상 보유, 소모 없이 무제한. `itemSeq`로 보유한 장난감을 고르면 그 효과, 생략하면 보유한 첫 번째 장난감 효과\n" +
            "  - `GIVE_CATNIP`: 기구 없이 캣닢(CATNIP, 고양이 전용) 1개, `itemSeq` 필수. 평소 효과는 활력 +15·친밀도 +3·안정감 -5(가안). " +
            "직전 캣닢 뒤 6시간 안에 또 주면 남용으로 보고 캣닢 효과 대신 안정감 -20만 들어간다(가안, 캣닢은 그래도 1개 쓴다)\n" +
            "  - `VET_VISIT`: 병원 진료. 기구·소모품 없음, `itemSeq` 생략. **정답이 병원인 대기 이벤트가 있을 때만** 된다" +
            "(앱은 대기 이벤트에 `vetVisit`가 있을 때만 병원 버튼을 켠다). 병원 자체 효과는 없고 이벤트 해결 효과가 들어가며, " +
            "진료비(`vetVisit.cost`)를 잔액에서 빼고 지출 내역에 EVENT로 남긴다(잔액 음수 허용). 그런 이벤트가 없으면 409 `SIMULATION_VET_NOT_NEEDED`" +
            "(행동 이력·마지막 행동 시각은 바뀌지 않음. 화면을 띄운 사이 시한이 지나 만료된 경우도 여기 해당)\n" +
            "- 산책·시스템 코드는 400.\n" +
            "- `statDelta`는 실제로 바뀐 양만 담는다(포만감 90에 +30이면 +10, 이미 100이면 빠짐).\n" +
            "- 병원 진료비 외에는 돈을 쓰지 않는다. 마지막 행동 시각이 갱신되고 행동 이력(CARE)이 1건 남는다.\n" +
            "- 대기 중인 집 돌발 이벤트가 있고 이 행동이 정답이면 해결 효과를 더하고 `resolvedEvent`에 결과를 준다. " +
            "`statDelta`는 물품 효과 + 해결 효과 합산, `resolvedEvent.eventStatDelta`는 그중 이벤트 몫. " +
            "해결 효과에도 대가가 있을 수 있다(예: 놀아 주면 안정감은 오르지만 포만감·활력이 준다).\n" +
            "- 오답이거나 대기 이벤트가 없으면 `resolvedEvent`는 null이고 이벤트는 그대로다(오답 패널티 없음, 시한이 지나면 방치 효과).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "행동 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CareActionSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "밥 주기(사료 사용)", value = FEED_SUCCESS),
                        ExampleObject(name = "물 주기(소모품 없음)", value = WATER_SUCCESS),
                        ExampleObject(name = "캣닢 남용(6시간 안에 또 줌)", value = CATNIP_OVERUSE_SUCCESS),
                        ExampleObject(name = "돌발 이벤트 해결(놀아 주기, 대가 포함)", value = PLAY_RESOLVED_SUCCESS),
                        ExampleObject(name = "병원 진료(진료비 차감)", value = VET_VISIT_SUCCESS),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "actionCode 누락·지원하지 않는 행동, 소모품 행동에 itemSeq 누락·행동과 맞지 않는 물품(PLAY에 장난감이 아닌 물품 포함), " +
                "없는 코드(본문 형식 오류), 세션 번호 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "지원하지 않는 행동", value = UNSUPPORTED_ACTION),
                        ExampleObject(name = "itemSeq 누락", value = ITEM_REQUIRED),
                        ExampleObject(name = "행동과 맞지 않는 물품", value = ITEM_MISMATCH),
                        ExampleObject(name = "없는 행동 코드(COMMON_INVALID_JSON)", value = FailExamples.COMMON_INVALID_JSON),
                        ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH),
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
            responseCode = "404",
            description = "없는 세션·남의 세션(보호소 포함), 없는 물품",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND),
                        ExampleObject(name = "SIMULATION_SHOP_ITEM_NOT_FOUND", value = SIMULATION_SHOP_ITEM_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "진행 중이 아닌 세션, 필요 기구 없음(펫샵에서 구매 안내), 소모품 미보유·다 씀·고른 장난감 미보유, 세션 종과 맞지 않는 물품, " +
                "병원에 갈 이벤트가 없음(앱은 진행 중 조회로 이벤트 상태를 다시 받는다)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS),
                        ExampleObject(name = "SIMULATION_EQUIPMENT_MISSING", value = SIMULATION_EQUIPMENT_MISSING),
                        ExampleObject(name = "SIMULATION_ITEM_NOT_IN_INVENTORY", value = SIMULATION_ITEM_NOT_IN_INVENTORY),
                        ExampleObject(name = "SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES", value = SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES),
                        ExampleObject(name = "SIMULATION_VET_NOT_NEEDED", value = SIMULATION_VET_NOT_NEEDED),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/actions")
    fun act(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Valid @RequestBody request: CareActionRequest,
    ) = CareActionResponse.from(careActionUseCase.act(user.userSeq, sessionSeq, request.toCommand()))

    private companion object {
        // 문서 예시 전용
        const val FEED_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50001,"actionType":"CARE","actionCode":"FEED",""" +
                """"consumedItem":{"itemSeq":22,"itemName":"균형 잡힌 사료","remainingQuantity":11},"statDelta":{"satiety":30,"bond":1},""" +
                """"stats":{"satiety":94,"hydration":58,"cleanliness":77,"bond":32,"calmness":46,"health":90,"energy":55,"calculatedDttm":"2026-11-01T17:00:00+09:00"},""" +
                """"balance":118000,"resolvedEvent":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val WATER_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50002,"actionType":"CARE","actionCode":"WATER",""" +
                """"consumedItem":null,"statDelta":{"hydration":42},""" +
                """"stats":{"satiety":64,"hydration":100,"cleanliness":77,"bond":31,"calmness":46,"health":90,"energy":55,"calculatedDttm":"2026-11-01T17:00:00+09:00"},""" +
                """"balance":118000,"resolvedEvent":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATNIP_OVERUSE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50003,"actionType":"CARE","actionCode":"GIVE_CATNIP",""" +
                """"consumedItem":{"itemSeq":33,"itemName":"향긋한 캣닢","remainingQuantity":2},"statDelta":{"calmness":-20},""" +
                """"stats":{"satiety":64,"hydration":58,"cleanliness":77,"bond":34,"calmness":21,"health":90,"energy":70,"calculatedDttm":"2026-11-01T19:00:00+09:00"},""" +
                """"balance":118000,"resolvedEvent":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val PLAY_RESOLVED_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50004,"actionType":"CARE","actionCode":"PLAY",""" +
                """"consumedItem":null,"statDelta":{"satiety":-5,"bond":9,"calmness":13,"energy":-10},""" +
                """"stats":{"satiety":59,"hydration":58,"cleanliness":77,"bond":40,"calmness":59,"health":90,"energy":45,"calculatedDttm":"2026-11-01T17:00:00+09:00"},""" +
                """"balance":118000,"resolvedEvent":{"occurrenceSeq":90001,"scenarioTitle":"쿠션을 물어뜯어요",""" +
                """"resultText":"신나게 놀고 나니 쿠션에는 관심이 없어졌어요. 배가 좀 고파 보여요.",""" +
                """"eventStatDelta":{"satiety":-5,"bond":5,"calmness":10,"energy":-10},"eventCost":0}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val VET_VISIT_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50005,"actionType":"CARE","actionCode":"VET_VISIT",""" +
                """"consumedItem":null,"statDelta":{"calmness":-10,"health":30},""" +
                """"stats":{"satiety":40,"hydration":35,"cleanliness":60,"bond":35,"calmness":30,"health":68,"energy":40,"calculatedDttm":"2026-11-03T10:00:00+09:00"},""" +
                """"balance":88000,"resolvedEvent":{"occurrenceSeq":90003,"scenarioTitle":"축 처져 있어요",""" +
                """"resultText":"진단서: 가벼운 장염 소견. 처방식과 휴식을 권장합니다. 병원에 다녀와 조금 지쳤어요.",""" +
                """"eventStatDelta":{"calmness":-10,"health":30},"eventCost":30000}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UNSUPPORTED_ACTION =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"actionCode","reason":"지원하지 않는 돌봄 행동입니다: WALK_START"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ITEM_REQUIRED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"itemSeq","reason":"이 행동에는 사용할 물품이 필요합니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ITEM_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"itemSeq","reason":"FEED에는 FOOD 물품을 써야 합니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

/** actionCode는 DB action_code 값(FEED 등). 응답·18.7의 actionType(CARE 등 분류)과 다른 필드다 */
data class CareActionRequest(
    @field:Schema(description = "돌봄 행동 코드. FEED·WATER·CLEAN_LITTER·BATHE·SNACK·MEDICINE·PLAY·GIVE_CATNIP·VET_VISIT", example = "FEED")
    @field:NotNull
    val actionCode: ActionCode?,
    @field:Schema(
        description = "쓸 물품 번호(인벤토리 값). FEED·CLEAN_LITTER·SNACK·MEDICINE·GIVE_CATNIP은 소모품 필수, PLAY는 고를 장난감(선택, 생략 시 첫 번째), " +
            "WATER·BATHE는 생략(보내도 무시)",
        nullable = true,
        example = "22",
    )
    val itemSeq: Long?,
) {
    fun toCommand() = CareActionCommand(actionCode!!, itemSeq)
}

data class CareActionResponse(
    @field:Schema(description = "행동 이력 번호", example = "50001")
    val actionLogSeq: Long,
    @field:Schema(description = ACTION_TYPE_DESC, example = "CARE")
    val actionType: ActionType,
    @field:Schema(description = ACTION_CODE_DESC, example = "FEED")
    val actionCode: ActionCode,
    @field:Schema(description = "쓴 소모품. 소모품이 없는 행동(WATER·BATHE)이면 null", nullable = true)
    val consumedItem: ConsumedItemResponse?,
    @field:Schema(description = "실제로 바뀐 스탯(키는 스탯 이름). 0~100으로 자른 뒤의 값이고 변화 없는 스탯은 빠진다", example = """{"satiety":30,"bond":1}""")
    val statDelta: Map<String, Int>,
    @field:Schema(description = "행동 직후 스탯")
    val stats: StatsResponse,
    @field:Schema(description = "현재 잔액(원). 병원 진료비 외에는 돌봄 행동이 돈을 쓰지 않는다", example = "118000")
    val balance: Int,
    @field:Schema(description = "이 행동이 대기 중 돌발 이벤트의 정답이었으면 해결 결과, 아니면 null", nullable = true)
    val resolvedEvent: ResolvedEventResponse?,
) {
    data class ResolvedEventResponse(
        @field:Schema(description = "해결한 이벤트 번호", example = "90001")
        val occurrenceSeq: Long,
        @field:Schema(description = "시나리오 제목", example = "물그릇을 엎었어요")
        val scenarioTitle: String,
        @field:Schema(description = "해결 결과 문구(병원이면 진단서)", nullable = true, example = "새 물을 받아 주니 꿀꺽꿀꺽 시원하게 마셨어요.")
        val resultText: String?,
        @field:Schema(description = "statDelta 중 이벤트 해결 효과 몫(실제 변화)", example = """{"hydration":10,"calmness":5}""")
        val eventStatDelta: Map<String, Int>,
        @field:Schema(description = "해결 비용(원). 병원 진료비만 0보다 크다", example = "0")
        val eventCost: Int,
    )

    data class ConsumedItemResponse(
        @field:Schema(description = "물품 번호", example = "22")
        val itemSeq: Long,
        @field:Schema(description = "물품 이름", example = "균형 잡힌 사료")
        val itemName: String,
        @field:Schema(description = "쓰고 남은 수량", example = "11")
        val remainingQuantity: Int,
    )

    companion object {
        fun from(result: CareActionResult) = CareActionResponse(
            actionLogSeq = checkNotNull(result.log.actionLogSeq),
            actionType = result.log.actionType,
            actionCode = result.log.actionCode,
            consumedItem = result.consumedItem?.let { ConsumedItemResponse(it.item.itemSeq, it.item.itemName, it.remainingQuantity) },
            statDelta = result.statDelta.entries.associate { (stat, value) -> stat.key to value },
            stats = StatsResponse.of(result.session.stats(), result.session.statsCalculatedDttm),
            balance = result.session.balance,
            resolvedEvent = result.resolvedEvent?.let {
                ResolvedEventResponse(it.occurrenceSeq, it.scenarioTitle, it.resultText, it.eventStatDelta.entries.associate { (stat, value) -> stat.key to value }, it.eventCost)
            },
        )
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class CareActionSuccessSchema : SuccessResponseSchema<CareActionResponse>()
