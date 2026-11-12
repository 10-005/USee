package com.ssafy.animal.core.match.application.port.`in`

import com.ssafy.animal.common.event.MatchCandidate
import com.ssafy.animal.common.event.TargetType

/** MatchRequested 발행 지점(실종·발견·보호동물)이 함께 쓰는 후보 추리기(Q523) */
fun interface MatchCandidateUseCase {
    /**
     * source와 비교할 후보. 실종이면 발견·보호동물, 그 밖이면 실종. 같은 종·날짜·거리·다른 작성자, 가까운 순 상한까지.
     * source가 ETC거나 없으면 빈 목록. 같은 트랜잭션에서 방금 바꾼 source도 반영한다
     */
    fun findCandidates(sourceType: TargetType, sourceSeq: Long): List<MatchCandidate>
}

/** 워커가 없던 동안 사라진 요청을 되살리는 일괄 재발행(Q523). 발견·보호동물 먼저, 실종 나중 */
fun interface MatchRepublishUseCase {
    /** 보낸 MatchRequested 수 */
    fun republishAll(): Int
}
