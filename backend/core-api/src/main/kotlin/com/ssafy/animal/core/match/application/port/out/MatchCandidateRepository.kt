package com.ssafy.animal.core.match.application.port.out

import com.ssafy.animal.common.event.MatchCandidate
import com.ssafy.animal.common.event.TargetType
import java.time.Duration
import java.time.OffsetDateTime

interface MatchCandidateRepository {
    fun findCandidates(sourceType: TargetType, sourceSeq: Long, criteria: MatchCriteria): List<MatchCandidate>

    /** 매칭 자격이 있는 type 대상 전부(번호 순). 일괄 재발행용 */
    fun findEligibleSources(type: TargetType): List<MatchSource>
}

data class MatchCriteria(
    val maxDistanceKm: Double,
    /** 발견·구조 시각이 실종 시각보다 이만큼 넘게 앞서면 제외 */
    val dateGrace: Duration,
    val limit: Int,
)

data class MatchSource(
    val seq: Long,
    val speciesCode: String,
    val occurredAt: OffsetDateTime,
    val imageKeys: List<String>,
    /** 작성자. 보호동물은 null */
    val userSeq: Long? = null,
)
