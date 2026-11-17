package com.ssafy.animal.core.auth.application.port.out

import java.time.Duration

/** Redis auth:refresh:{userSeq}:{jti} */
interface RefreshTokenStore {
    fun save(userSeq: Long, jti: String, session: RefreshSession, ttl: Duration)

    /** 원자적으로 꺼내며 삭제(rotation). 없으면 null */
    fun take(userSeq: Long, jti: String): RefreshSession?
    fun delete(userSeq: Long, jti: String)
    fun deleteAll(userSeq: Long)
}

/** refresh 키에 저장하는 값. 갱신 때 shelter_members를 다시 읽지 않도록 shelterSeq도 함께 둔다. */
data class RefreshSession(val tokenHash: String, val shelterSeq: Long?)
