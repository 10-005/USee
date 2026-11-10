package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_EQUIPMENT_ALREADY_OWNED
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_IN_PROGRESS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_SHOP_ITEM_NOT_FOUND
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseLine
import com.ssafy.animal.simulation.application.port.`in`.PurchaseResult
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
import com.ssafy.animal.simulation.application.port.`in`.PurchasedLine
import com.ssafy.animal.simulation.domain.ItemCategory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = SHOP_TAG, description = SHOP_TAG_DESC)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class PurchaseController(
    private val purchaseUseCase: PurchaseUseCase,
) {

    /** 18.9 펫샵 장바구니 구매(Q503). 본인만 사며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @Operation(
        summary = "펫샵 장바구니 구매",
        description = "- 장바구니의 여러 물품을 한 번에 산다. **전부 사거나 하나도 사지 않는다**(한 물품이라도 오류면 돈·보유·지출 변화 없음).\n" +
            "- 본인 진행 중 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`, 진행 중이 아니면 409\n" +
            "- 사기 전에 세션을 지금 시각까지 진행시켜, 건강 0·예정 종료 경과면 그 자리에서 끝내고(종료는 저장됨) 409 `SIMULATION_NOT_IN_PROGRESS`.\n" +
            "- `items`는 1~50줄, 같은 `itemSeq`를 두 줄로 담으면 400(수량을 합쳐 한 줄로 보낸다).\n" +
            "- 물품마다 금액 = 가격 × 수량을 잔액에서 뺀다. 잔액이 모자라도 사고 잔액은 음수가 된다.\n" +
            "- 소모품은 수량 × 묶음 수만큼 인벤토리가 늘어난다(사료 2개 = 24끼). 다 쓴 뒤 다시 살 수 있다.\n" +
            "- 기구는 수량 1만, 이미 보유했으면 409 `SIMULATION_EQUIPMENT_ALREADY_OWNED`.\n" +
            "- 물품 적용 종이 세션 종과 다르면 409, 판매 중단 물품은 404. 오류는 장바구니 앞쪽 물품부터 보고 첫 오류를 준다.\n" +
            "- 수량은 1~99. 물품마다 지출 내역(PURCHASE)이 1건씩 요청 순서대로 남고, `purchases[].balanceAfter`도 그 순서로 줄어든다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "구매 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PurchaseSuccessSchema::class),
                    examples = [ExampleObject(name = "구매 성공", value = PURCHASE_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "장바구니 비었음·50줄 초과, 필수값 누락·수량 범위 밖, 같은 물품 중복, 기구를 2개 이상 구매, 세션 번호 타입 오류, 본문 형식 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "빈 장바구니", value = EMPTY_CART),
                        ExampleObject(name = "수량 범위 밖", value = QUANTITY_OUT_OF_RANGE),
                        ExampleObject(name = "같은 물품 중복", value = DUPLICATE_ITEM),
                        ExampleObject(name = "기구 2개 이상", value = EQUIPMENT_QUANTITY),
                        ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "COMMON_INVALID_JSON", value = FailExamples.COMMON_INVALID_JSON),
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
            description = "없는 세션·남의 세션(보호소 포함), 장바구니에 없는 물품·판매 중단 물품",
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
            description = "진행 중이 아닌 세션, 세션 종과 맞지 않는 물품, 이미 보유한 기구",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS),
                        ExampleObject(name = "SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES", value = SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES),
                        ExampleObject(name = "SIMULATION_EQUIPMENT_ALREADY_OWNED", value = SIMULATION_EQUIPMENT_ALREADY_OWNED),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    fun purchase(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Valid @RequestBody request: PurchaseRequest,
    ) = PurchaseResponse.from(purchaseUseCase.purchase(user.userSeq, sessionSeq, request.toCommand()))

    private companion object {
        // 문서 예시 전용
        const val PURCHASE_SUCCESS =
            """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"purchases":[""" +
                """{"transactionSeq":70001,"item":{"itemSeq":22,"itemName":"균형 잡힌 사료","category":"FOOD"},"quantity":2,"amount":21200,"balanceAfter":128800,"inventoryQuantity":24},""" +
                """{"transactionSeq":70002,"item":{"itemSeq":1,"itemName":"밥그릇","category":"FOOD_BOWL"},"quantity":1,"amount":10000,"balanceAfter":118800,"inventoryQuantity":1}""" +
                """],"totalAmount":31200,"balanceAfter":118800},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val EMPTY_CART =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"items","reason":"크기가 1에서 50 사이여야 합니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val QUANTITY_OUT_OF_RANGE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"items[0].quantity","reason":"1 이상이어야 합니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DUPLICATE_ITEM =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"items[1].itemSeq","reason":"같은 물품을 두 번 담을 수 없습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val EQUIPMENT_QUANTITY =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"items[1].quantity","reason":"기구는 1개만 살 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

/** 누락도 400 COMMON_INVALID_INPUT이 되도록 nullable로 받아 검증한다 */
data class PurchaseRequest(
    @field:ArraySchema(
        arraySchema = Schema(description = "장바구니(1~50줄). 같은 물품은 한 줄로 담는다. 이 순서로 지출이 기록된다"),
        schema = Schema(implementation = PurchaseLineRequest::class),
    )
    @field:NotNull
    @field:Size(min = 1, max = MAX_LINES)
    @field:Valid
    val items: List<PurchaseLineRequest?>?,
) {
    fun toCommand() = PurchaseCommand(
        items!!.mapIndexed { index, line ->
            // [null] 같은 빈 줄은 Bean Validation이 원소까지 보지 않으므로 여기서 400으로 막는다
            line ?: throw BusinessException(
                CommonErrorCode.COMMON_INVALID_INPUT,
                errors = listOf(ErrorDetail("items[$index]", "비어 있을 수 없습니다")),
            )
            PurchaseLine(line.itemSeq!!, line.quantity!!)
        },
    )

    companion object {
        /** 판매 물품이 35종이라 넉넉히 둔 한 번에 담을 수 있는 줄 수 */
        const val MAX_LINES = 50
    }
}

data class PurchaseLineRequest(
    @field:Schema(description = "물품 번호(펫샵 목록 값)", example = "22")
    @field:NotNull
    val itemSeq: Long?,
    @field:Schema(description = "구매 수량 1~99. 기구는 1만. 묶음 물품은 묶음 개수", example = "2")
    @field:NotNull
    @field:Min(1)
    // 명세에 상한은 없지만 금액·잔액 정수 범위를 넘지 않도록 한 번에 살 수 있는 수를 막아 둔다
    @field:Max(MAX_QUANTITY)
    val quantity: Int?,
) {
    companion object {
        const val MAX_QUANTITY = 99L
    }
}

data class PurchaseResponse(
    @field:Schema(description = "요청 순서대로 산 물품별 결과")
    val purchases: List<PurchasedLineResponse>,
    @field:Schema(description = "이번 구매 총액(원)", example = "31200")
    val totalAmount: Int,
    @field:Schema(description = "모두 산 뒤 잔액(원). 음수가 될 수 있다", example = "118800")
    val balanceAfter: Int,
) {
    companion object {
        fun from(result: PurchaseResult) = PurchaseResponse(
            purchases = result.lines.map(PurchasedLineResponse::from),
            totalAmount = result.totalAmount,
            balanceAfter = result.balanceAfter,
        )
    }
}

data class PurchasedLineResponse(
    @field:Schema(description = "지출 내역 번호", example = "70001")
    val transactionSeq: Long,
    @field:Schema(description = "산 물품")
    val item: PurchasedItem,
    @field:Schema(description = "구매 수량", example = "2")
    val quantity: Int,
    @field:Schema(description = "지출액(원) = 가격 × 수량", example = "21200")
    val amount: Int,
    @field:Schema(description = "이 물품을 산 직후 잔액(원). 음수가 될 수 있다", example = "128800")
    val balanceAfter: Int,
    @field:Schema(description = "구매 후 보유 수량(묶음 물품은 남은 사용 횟수)", example = "24")
    val inventoryQuantity: Int,
) {
    data class PurchasedItem(
        @field:Schema(description = "물품 번호", example = "22")
        val itemSeq: Long,
        @field:Schema(description = "물품 이름", example = "균형 잡힌 사료")
        val itemName: String,
        @field:Schema(description = ITEM_CATEGORY_DESC, example = "FOOD")
        val category: ItemCategory,
    )

    companion object {
        fun from(line: PurchasedLine) = PurchasedLineResponse(
            transactionSeq = checkNotNull(line.transaction.transactionSeq),
            item = PurchasedItem(line.item.itemSeq, line.item.itemName, line.item.category),
            quantity = line.transaction.quantity,
            amount = line.transaction.amount,
            balanceAfter = line.transaction.balanceAfter,
            inventoryQuantity = line.inventoryQuantity,
        )
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class PurchaseSuccessSchema : SuccessResponseSchema<PurchaseResponse>()
