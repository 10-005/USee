package com.ssafy.animal.core.user.adapter.out.persistence

import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.user.domain.UserErrorCode
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 사전 조회를 지난 경쟁 상태에서 DB 부분 UNIQUE 위반이 같은 409로 바뀌는지 */
class UserPersistenceAdapterTest {

    private fun violation(index: String) = DataIntegrityViolationException(
        "could not execute statement",
        RuntimeException("""ERROR: duplicate key value violates unique constraint "$index""""),
    )

    @Test
    fun `부분 UNIQUE 인덱스 이름으로 오류 코드를 고른다`() {
        assertEquals(UserErrorCode.USER_DUPLICATE_NICKNAME, UserPersistenceAdapter.translate(violation("uq_users_active_nickname"))?.errorCode)
        assertEquals(UserErrorCode.USER_DUPLICATE_PHONE, UserPersistenceAdapter.translate(violation("uq_users_active_phone"))?.errorCode)
        assertEquals(
            AuthErrorCode.AUTH_KAKAO_ACCOUNT_CONFLICT,
            UserPersistenceAdapter.translate(violation("uq_user_accounts_active_login"))?.errorCode,
        )
        assertNull(UserPersistenceAdapter.translate(violation("ck_users_user_profile")))
    }
}
