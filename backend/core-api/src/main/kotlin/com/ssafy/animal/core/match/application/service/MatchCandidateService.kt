package com.ssafy.animal.core.match.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.match.application.port.`in`.MatchCandidateUseCase
import com.ssafy.animal.core.match.application.port.`in`.MatchRepublishUseCase
import com.ssafy.animal.core.match.application.port.out.MatchCandidateRepository
import com.ssafy.animal.core.match.application.port.out.MatchCriteria
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Duration

/** 후보 조건 [가안](Q523). match.candidate.* 로 바꾼다 */
@ConfigurationProperties("match.candidate")
data class MatchCandidateProperties(
    val maxDistanceKm: Double = 30.0,
    val dateGrace: Duration = Duration.ofDays(1),
    val limit: Int = 500,
)

@Service
@EnableConfigurationProperties(MatchCandidateProperties::class)
class MatchCandidateService(
    private val repository: MatchCandidateRepository,
    private val eventPublisher: EventPublisher,
    properties: MatchCandidateProperties,
) : MatchCandidateUseCase, MatchRepublishUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val criteria = MatchCriteria(properties.maxDistanceKm, properties.dateGrace, properties.limit)

    override fun findCandidates(sourceType: TargetType, sourceSeq: Long) = repository.findCandidates(sourceType, sourceSeq, criteria)

    // 트랜잭션 밖이라 한 건씩 바로 나간다. 실종 차례에는 앞서 보낸 발견·보호동물의 벡터가 워커에 이미 있다(큐는 순서대로 처리)
    override fun republishAll(): Int = REPUBLISH_ORDER.sumOf { (type, eventType) ->
        val sources = repository.findEligibleSources(type)
        sources.forEach {
            eventPublisher.publish(
                MatchRequested(eventType, type, it.seq, it.imageKeys, it.speciesCode, it.occurredAt, findCandidates(type, it.seq)),
            )
        }
        log.info("[match] 일괄 재발행 {} {}건", type, sources.size)
        sources.size
    }

    companion object {
        private val REPUBLISH_ORDER = listOf(
            TargetType.FOUND to MatchEventType.FOUND_REPORT_REGISTERED,
            TargetType.SHELTER_ANIMAL to MatchEventType.SHELTER_ANIMAL_REGISTERED,
            TargetType.MISSING to MatchEventType.MISSING_REPORT_REGISTERED,
        )
    }
}
