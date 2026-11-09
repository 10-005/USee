package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.application.port.`in`.InventoryItemView
import com.ssafy.animal.simulation.application.port.`in`.InventoryQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.InventoryView
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = SHOP_TAG, description = SHOP_TAG_DESC)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class InventoryController(
    private val inventoryQueryUseCase: InventoryQueryUseCase,
) {

    /** 18.10 보유 물품 + 잔액. 본인만 보며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @Operation(
        summary = "인벤토리(보유 물품·잔액) 조회",
        description = "- 본인 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`\n" +
            "- 잔액·예상 비용·보유 기구·보유 물품 전체를 준다. 종료된 세션도 조회할 수 있다.\n" +
            "- 다 쓴 소모품도 `quantity: 0`으로 남는다(앱이 \"다 썼음\" 표시).\n" +
            "- `quantity`는 남은 사용 횟수(사료 1개를 사면 12). 기구는 항상 1.\n" +
            "- 정렬은 펫샵 표시 순서 고정. 보유 물품이 없으면 `items`는 빈 배열.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = InventorySuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = INVENTORY_SUCCESS)],
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
            description = "없는 세션·남의 세션(보호소 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/inventory")
    fun getInventory(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
    ) = InventoryResponse.from(inventoryQueryUseCase.getInventory(user.userSeq, sessionSeq))

    private companion object {
        // 문서 예시 전용
        const val INVENTORY_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"balance":118000,"expectedCost":150000,""" +
                """"equipment":{"foodBowl":true,"waterBowl":false,"litterBox":false,"toy":false,"leash":false},"items":[""" +
                """{"itemSeq":1,"itemName":"밥그릇","category":"FOOD_BOWL","isConsumable":false,"quantity":1,"imageUrl":null},""" +
                """{"itemSeq":22,"itemName":"균형 잡힌 사료","category":"FOOD","isConsumable":true,"quantity":11,"imageUrl":null},""" +
                """{"itemSeq":23,"itemName":"바삭한 미니 비스킷","category":"SNACK","isConsumable":true,"quantity":0,"imageUrl":null}""" +
                """]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class InventoryResponse(
    @field:Schema(description = "현재 잔액(원). 음수가 될 수 있다", example = "118000")
    val balance: Int,
    @field:Schema(description = "시작 시 입력한 일주일 예상 비용(원)", example = "150000")
    val expectedCost: Int,
    @field:Schema(description = "보유 기구(메인 화면 오브젝트 활성 여부)")
    val equipment: Equipment,
    @field:Schema(description = "보유 물품(다 쓴 소모품 포함). 펫샵 표시 순서")
    val items: List<InventoryItemResponse>,
) {
    companion object {
        fun from(view: InventoryView) = InventoryResponse(
            balance = view.session.balance,
            expectedCost = view.session.expectedCost,
            equipment = view.equipment,
            items = view.items.map(InventoryItemResponse::from),
        )
    }
}

data class InventoryItemResponse(
    @field:Schema(description = "물품 번호", example = "22")
    val itemSeq: Long,
    @field:Schema(description = "물품 이름", example = "균형 잡힌 사료")
    val itemName: String,
    @field:Schema(description = ITEM_CATEGORY_DESC, example = "FOOD")
    val category: ItemCategory,
    @get:Schema(name = "isConsumable", description = "소모품 여부. false면 기구", example = "true")
    val isConsumable: Boolean,
    @field:Schema(description = "남은 수량(묶음 물품은 남은 사용 횟수). 다 쓴 소모품은 0, 기구는 1", example = "11")
    val quantity: Int,
    @field:Schema(description = "사진 URL. 아직 없어 null", nullable = true, example = "null")
    val imageUrl: String?,
) {
    companion object {
        fun from(view: InventoryItemView) = with(view.entry) {
            InventoryItemResponse(
                itemSeq = shopItem.itemSeq,
                itemName = shopItem.itemName,
                category = shopItem.category,
                isConsumable = shopItem.isConsumable,
                quantity = inventoryItem.quantity,
                imageUrl = view.imageUrl,
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class InventorySuccessSchema : SuccessResponseSchema<InventoryResponse>()
