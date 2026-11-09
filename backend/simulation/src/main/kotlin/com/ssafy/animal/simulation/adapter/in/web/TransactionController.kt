package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SORT_REG_DTTM_ONLY
import com.ssafy.animal.simulation.application.port.`in`.TransactionQueryUseCase
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.TransactionType
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

private const val TRANSACTION_TYPE_DESC = "지출 유형. PURCHASE: 펫샵 구매, EVENT: 동물병원 진료비"

@Tag(name = SHOP_TAG, description = SHOP_TAG_DESC)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class TransactionController(
    private val transactionQueryUseCase: TransactionQueryUseCase,
) {

    /** 18.11 지출 내역. 본인만 보며 그 외(보호소 포함)는 404로 감추므로 역할 제한을 두지 않는다(Q483). 정렬 regDttm만 */
    @Operation(
        summary = "지출 내역 조회",
        description = "- 본인 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`\n" +
            "- 펫샵 구매(PURCHASE)와 동물병원 진료비(EVENT)를 준다. `transactionType`으로 거를 수 있다(생략 시 전체).\n" +
            "- 구매면 `item`(물품), 진료비면 `occurrenceSeq`(비용이 생긴 돌발 이벤트)가 채워지고 나머지는 null.\n" +
            "- `balanceAfter`는 그 지출 직후 잔액(음수 가능). 종료된 세션도 조회할 수 있다.\n" +
            "- size 기본 50. sort 허용 필드는 `regDttm`만(기본 `regDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 지출이 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TransactionPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = PAGE_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "없는 지출 유형 값, 허용 밖 정렬 필드, 세션 번호 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "지출 유형 값 오류", value = TYPE_MISMATCH),
                        ExampleObject(name = "정렬 필드 오류", value = SORT_REG_DTTM_ONLY),
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
    @GetMapping("/transactions")
    fun getTransactions(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Parameter(description = "$TRANSACTION_TYPE_DESC. 생략하면 전체", example = "PURCHASE")
        @RequestParam(required = false) transactionType: TransactionType?,
        @PageableDefault(size = 50) pageable: Pageable,
    ) = PageResponse.of(
        transactionQueryUseCase.getTransactions(user.userSeq, sessionSeq, transactionType, pageable).map(TransactionResponse::from),
    )

    private companion object {
        // 문서 예시 전용
        const val PAGE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[""" +
                """{"transactionSeq":70002,"transactionType":"EVENT","item":null,"occurrenceSeq":90001,"quantity":1,"amount":30000,"balanceAfter":98800,"description":"동물병원 진료비 · 축 처져 있어요","regDttm":"2026-11-02T10:12:00+09:00"},""" +
                """{"transactionSeq":70001,"transactionType":"PURCHASE","item":{"itemSeq":22,"itemName":"균형 잡힌 사료","category":"FOOD"},"occurrenceSeq":null,"quantity":2,"amount":21200,"balanceAfter":128800,"description":"균형 잡힌 사료 × 2","regDttm":"2026-11-01T15:10:00+09:00"}""" +
                """],"page":0,"size":50,"totalElements":2,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"transactionType","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class TransactionResponse(
    @field:Schema(description = "지출 내역 번호", example = "70001")
    val transactionSeq: Long,
    @field:Schema(description = TRANSACTION_TYPE_DESC, example = "PURCHASE")
    val transactionType: TransactionType,
    @field:Schema(description = "구매 물품. 진료비(EVENT)면 null", nullable = true)
    val item: TransactionItem?,
    @field:Schema(description = "비용이 생긴 돌발 이벤트 번호. 구매(PURCHASE)면 null", nullable = true, example = "null")
    val occurrenceSeq: Long?,
    @field:Schema(description = "수량. 진료비는 1", example = "2")
    val quantity: Int,
    @field:Schema(description = "지출액(원)", example = "21200")
    val amount: Int,
    @field:Schema(description = "지출 직후 잔액(원). 음수가 될 수 있다", example = "128800")
    val balanceAfter: Int,
    @field:Schema(description = "표시 문구. 구매는 \"물품명 × 수량\", 진료비는 \"동물병원 진료비 · 이벤트 제목\"", nullable = true, example = "균형 잡힌 사료 × 2")
    val description: String?,
    @field:Schema(description = "지출 시각", example = "2026-11-01T15:10:00+09:00")
    val regDttm: OffsetDateTime?,
) {
    data class TransactionItem(
        @field:Schema(description = "물품 번호", example = "22")
        val itemSeq: Long,
        @field:Schema(description = "물품 이름", example = "균형 잡힌 사료")
        val itemName: String,
        @field:Schema(description = ITEM_CATEGORY_DESC, example = "FOOD")
        val category: ItemCategory,
    )

    companion object {
        fun from(entry: TransactionEntry) = with(entry.transaction) {
            TransactionResponse(
                transactionSeq = checkNotNull(transactionSeq),
                transactionType = transactionType,
                item = entry.item?.let { TransactionItem(it.itemSeq, it.itemName, it.category) },
                occurrenceSeq = occurrenceSeq,
                quantity = quantity,
                amount = amount,
                balanceAfter = balanceAfter,
                description = description,
                regDttm = regDttm,
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class TransactionPageSuccessSchema : SuccessResponseSchema<PageResponse<TransactionResponse>>()
