package com.ssafy.animal.common.persistence

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.ComparableExpressionBase
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import org.springframework.data.domain.Sort

/**
 * 목록 API의 sort 파라미터를 QueryDSL 정렬로 바꾼다(명세 Q417).
 * - fields: API 필드 이름 → Q경로. 여기에 없는 필드는 400 COMMON_INVALID_INPUT
 * - default: sort가 없을 때의 정렬
 * - tieBreaker: 동률 처리(보통 PK desc). 항상 마지막에 붙는다
 * 엔티티 필드명을 그대로 정렬에 쓰지 말고 API 이름으로 매핑한다.
 */
class SortPolicy(
    private val fields: Map<String, ComparableExpressionBase<*>>,
    private val default: List<OrderSpecifier<*>>,
    private val tieBreaker: OrderSpecifier<*>,
) {
    fun toOrderSpecifiers(sort: Sort): Array<OrderSpecifier<*>> {
        val orders = if (sort.isUnsorted) default else sort.map(::toOrderSpecifier).toList()
        return (orders + tieBreaker).toTypedArray()
    }

    private fun toOrderSpecifier(order: Sort.Order): OrderSpecifier<*> {
        val path = fields[order.property] ?: throw BusinessException(
            CommonErrorCode.COMMON_INVALID_INPUT,
            errors = listOf(ErrorDetail(SORT_PARAM, "정렬할 수 없는 필드입니다: ${order.property} (허용: ${fields.keys.joinToString()})")),
        )
        return if (order.isAscending) path.asc() else path.desc()
    }

    companion object {
        private const val SORT_PARAM = "sort"

        /** 명세에 화이트리스트가 따로 없는 목록의 기본 정책: regDttm만 허용, 기본 regDttm desc, 동률은 PK desc */
        fun regDttmOnly(regDttm: ComparableExpressionBase<*>, pk: ComparableExpressionBase<*>) = SortPolicy(
            fields = mapOf("regDttm" to regDttm),
            default = listOf(regDttm.desc()),
            tieBreaker = pk.desc(),
        )
    }
}
