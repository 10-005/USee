package com.ssafy.animal.simulation.adapter.out.core

import com.ssafy.animal.simulation.application.port.out.ImageUrlResolver
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.application.port.out.UserReader
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

/**
 * core.users와 프로필 사진(core.images)을 읽기 전용 JOIN으로 조회한다(Q420 D, 18.20 ActorSummary).
 * 소유하지 않는 테이블이라 엔티티로 매핑하지 않고 잠그지도 않는다. 탈퇴 회원도 읽어 표시 이름만 바꾼다.
 */
@Component
class CoreUserReader(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val imageUrlResolver: ImageUrlResolver,
) : UserReader {

    private val rowMapper = RowMapper { rs, _ ->
        UserInfo(
            userSeq = rs.getInt("user_seq"),
            nickname = rs.getString("nickname"),
            role = rs.getString("role"),
            isDeleted = rs.getBoolean("is_deleted"),
            profileImageSeq = rs.getObject("profile_image_seq", Long::class.javaObjectType),
            profileImageUrl = rs.getString("image_key")?.let(imageUrlResolver::publicUrl),
        )
    }

    override fun findUsers(userSeqs: Collection<Int>): Map<Int, UserInfo> {
        if (userSeqs.isEmpty()) return emptyMap()
        return jdbcTemplate.query(SELECT_USERS, mapOf("userSeqs" to userSeqs.toSet()), rowMapper).associateBy { it.userSeq }
    }

    companion object {
        private val SELECT_USERS = """
            SELECT u.user_seq, u.nickname, u.role, u.is_deleted, u.profile_image_seq, i.image_key
            FROM core.users u
            LEFT JOIN core.images i ON i.image_seq = u.profile_image_seq
            WHERE u.user_seq IN (:userSeqs)
        """.trimIndent()
    }
}
