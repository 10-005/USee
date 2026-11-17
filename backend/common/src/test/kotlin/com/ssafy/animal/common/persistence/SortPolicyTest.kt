package com.ssafy.animal.common.persistence

import com.querydsl.core.types.dsl.PathBuilder
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SortPolicyTest {

    private val entity = PathBuilder(Any::class.java, "entity")
    private val regDttm = entity.getComparable("regDttm", OffsetDateTime::class.java)
    private val name = entity.getComparable("name", String::class.java)
    private val seq = entity.getComparable("entitySeq", java.lang.Long::class.java)

    private val policy = SortPolicy(
        fields = mapOf("regDttm" to regDttm, "name" to name),
        default = listOf(regDttm.desc()),
        tieBreaker = seq.desc(),
    )

    @Test
    fun `허용 필드는 요청한 방향으로 정렬하고 마지막에 PK desc를 붙인다`() {
        assertEquals(listOf(regDttm.asc(), seq.desc()), policy.toOrderSpecifiers(Sort.by(Sort.Direction.ASC, "regDttm")).toList())
        assertEquals(listOf(regDttm.desc(), seq.desc()), policy.toOrderSpecifiers(Sort.by(Sort.Direction.DESC, "regDttm")).toList())
    }

    @Test
    fun `sort가 없으면 기본 정렬`() {
        assertEquals(listOf(regDttm.desc(), seq.desc()), policy.toOrderSpecifiers(Sort.unsorted()).toList())
    }

    @Test
    fun `여러 필드는 요청 순서대로 정렬한다`() {
        val sort = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("regDttm"))
        assertEquals(listOf(name.asc(), regDttm.desc(), seq.desc()), policy.toOrderSpecifiers(sort).toList())
    }

    @Test
    fun `허용 안 된 필드는 COMMON_INVALID_INPUT과 sort 필드 사유`() {
        val e = assertFailsWith<BusinessException> { policy.toOrderSpecifiers(Sort.by("entitySeq")) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, e.errorCode)
        assertEquals("sort", e.errors.single().field)
    }

    @Test
    fun `regDttmOnly는 regDttm만 허용하고 기본 regDttm desc, 동률은 PK desc`() {
        val defaultPolicy = SortPolicy.regDttmOnly(regDttm, seq)
        assertEquals(listOf(regDttm.desc(), seq.desc()), defaultPolicy.toOrderSpecifiers(Sort.unsorted()).toList())
        assertEquals(listOf(regDttm.asc(), seq.desc()), defaultPolicy.toOrderSpecifiers(Sort.by("regDttm")).toList())
        assertFailsWith<BusinessException> { defaultPolicy.toOrderSpecifiers(Sort.by("name")) }
    }
}
