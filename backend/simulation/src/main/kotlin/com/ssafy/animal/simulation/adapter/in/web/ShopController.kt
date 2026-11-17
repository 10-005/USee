package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.simulation.application.port.`in`.ShopItemView
import com.ssafy.animal.simulation.application.port.`in`.ShopQueryUseCase
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

internal const val SHOP_TAG = "모의 입양 펫샵"
internal const val SHOP_TAG_DESC = "펫샵 물품 목록·구매, 세션별 보유 물품(인벤토리)·지출 내역. 돈은 구매와 동물병원 진료비에만 쓰며 잔액은 음수가 될 수 있다"
internal const val ITEM_CATEGORY_DESC = "물품 분류. 기구(한 번 사면 영구): FOOD_BOWL 밥그릇, WATER_BOWL 물그릇, LITTER_BOX 화장실, TOY 장난감, " +
    "FURNITURE 가구, LEASH 하네스와 리드줄, GROOMING_KIT 위생 관리 키트 / 소모품: FOOD 사료, SNACK 간식, LITTER 배변용품, " +
    "SHAMPOO 샴푸(판매 중단), MEDICINE 약, POOP_BAG 배변봉투, CATNIP 캣닢"

@Tag(name = SHOP_TAG, description = SHOP_TAG_DESC)
@RestController
@RequestMapping("/api/simulation/v1/simulations/shop")
class ShopController(
    private val shopQueryUseCase: ShopQueryUseCase,
) {

    /** 18.8 펫샵 물품 목록(로그인만 필요, 역할 무관). 페이지 없이 전량, display_order 순 */
    @Operation(
        summary = "펫샵 물품 목록",
        description = "- 로그인 필요(USER·SHELTER 모두)\n" +
            "- 판매 중인 물품 전부를 페이지 없이 배열로 준다. 정렬은 표시 순서 고정.\n" +
            "- `speciesType`을 주면 그 종 전용 물품과 공용(ALL) 물품만. 생략하면 전체.\n" +
            "- 기구(`isConsumable=false`)는 세션당 1개만 살 수 있고, 소모품은 쓸 때마다 1씩 줄어든다.\n" +
            "- `usesPerPurchase`: 1개를 사면 늘어나는 사용 횟수(예: 사료 1개 = 12끼). 기구·단품은 1.\n" +
            "- `effects`: 사용 시 스탯 변화(가안). 기구는 null. 장난감·위생 관리 키트는 놀아 주기·목욕할 때의 효과.\n" +
            "- 사진이 아직 없어 `imageUrl`은 모두 null.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShopItemListSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = LIST_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "speciesType이 DOG·CAT이 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "종 값 오류", value = SPECIES_TYPE_MISMATCH)],
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
    )
    @GetMapping("/items")
    fun getItems(
        @Parameter(description = "세션 종. DOG: 개, CAT: 고양이. 생략하면 전체", example = "DOG")
        @RequestParam(required = false) speciesType: SpeciesType?,
    ) = shopQueryUseCase.getItems(speciesType).map(ShopItemResponse::from)

    private companion object {
        // 문서 예시 전용
        const val LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[""" +
                """{"itemSeq":1,"itemName":"밥그릇","category":"FOOD_BOWL","isConsumable":false,"price":10000,"description":"사료를 담는 그릇입니다. 있어야 밥을 줄 수 있어요.","imageUrl":null,"speciesScope":"ALL","effects":null,"usesPerPurchase":1},""" +
                """{"itemSeq":22,"itemName":"균형 잡힌 사료","category":"FOOD","isConsumable":true,"price":10600,"description":"1개에 12끼입니다. 밥을 줄 때마다 1끼씩 줄어요.","imageUrl":null,"speciesScope":"ALL","effects":{"satiety":30,"bond":1},"usesPerPurchase":12}""" +
                """],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SPECIES_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"speciesType","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class ShopItemResponse(
    @field:Schema(description = "물품 번호", example = "22")
    val itemSeq: Long,
    @field:Schema(description = "물품 이름", example = "균형 잡힌 사료")
    val itemName: String,
    @field:Schema(description = ITEM_CATEGORY_DESC, example = "FOOD")
    val category: ItemCategory,
    @get:Schema(name = "isConsumable", description = "소모품 여부. false면 기구(세션당 1개, 영구)", example = "true")
    val isConsumable: Boolean,
    @field:Schema(description = "가격(원). 묶음 물품은 1묶음 가격", example = "10600")
    val price: Int,
    @field:Schema(description = "상세 설명", nullable = true, example = "1개에 12끼입니다. 밥을 줄 때마다 1끼씩 줄어요.")
    val description: String?,
    @field:Schema(description = "사진 URL. 아직 없어 null", nullable = true, example = "null")
    val imageUrl: String?,
    @field:Schema(description = "적용 종. DOG·CAT 전용 또는 ALL(공용)", example = "ALL")
    val speciesScope: SpeciesScope,
    @field:Schema(description = "사용 시 스탯 변화(가안, 키는 스탯 이름·양수가 좋은 방향). 기구는 null", nullable = true, example = """{"satiety":30,"bond":1}""")
    val effects: Map<String, Int>?,
    @field:Schema(description = "1개 구매 시 늘어나는 사용 횟수(묶음 물품). 기구·단품은 1", example = "12")
    val usesPerPurchase: Int,
) {
    companion object {
        fun from(view: ShopItemView) = with(view.item) {
            ShopItemResponse(
                itemSeq = itemSeq,
                itemName = itemName,
                category = category,
                isConsumable = isConsumable,
                price = price,
                description = description,
                imageUrl = view.imageUrl,
                speciesScope = speciesScope,
                effects = effectsJsonb,
                usesPerPurchase = view.usesPerPurchase,
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ShopItemListSuccessSchema : SuccessResponseSchema<List<ShopItemResponse>>()
