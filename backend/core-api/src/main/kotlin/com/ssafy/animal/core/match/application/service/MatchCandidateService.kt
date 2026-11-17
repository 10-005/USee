package com.ssafy.animal.core.match.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.match.application.port.`in`.MatchCandidateUseCase
import com.ssafy.animal.core.match.application.port.`in`.MatchRepublishUseCase
import com.ssafy.animal.core.match.application.port.out.MatchCandidateRepository
import com.ssafy.animal.core.match.application.port.out.MatchCriteria
import com.ssafy.animal.core.match.application.port.out.MatchRepublishMarker
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

/** 시연용 조정. 여기 있는 회원의 실종신고는 등록·사진 변경·재개·일괄 재발행 때 MatchRequested를 보내지 않는다 */
@ConfigurationProperties("match")
data class MatchExclusionProperties(
    val excludedMissingUserSeqs: Set<Long> = emptySet(),
)

@Service
@EnableConfigurationProperties(MatchCandidateProperties::class, MatchExclusionProperties::class)
class MatchCandidateService(
    private val repository: MatchCandidateRepository,
    private val eventPublisher: EventPublisher,
    private val republishMarker: MatchRepublishMarker,
    properties: MatchCandidateProperties,
    private val exclusion: MatchExclusionProperties = MatchExclusionProperties(),
) : MatchCandidateUseCase, MatchRepublishUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val criteria = MatchCriteria(properties.maxDistanceKm, properties.dateGrace, properties.limit)

    override fun findCandidates(sourceType: TargetType, sourceSeq: Long) = repository.findCandidates(sourceType, sourceSeq, criteria)

    override fun isMissingMatchExcluded(userSeq: Long) = userSeq in exclusion.excludedMissingUserSeqs

    /**
     * 표식은 발행 전에 넣는다. 발행 도중 프로세스가 죽으면 그 이름은 끝난 것으로 남으므로, 다시 하려면 이름을 바꾼다.
     * Redis에 닿지 못하면 확인 없이 보내지 않는다(기동은 계속)
     */
    override fun republishOnce(runName: String): Int {
        val name = runName.trim()
        if (name.isEmpty() || name.equals("false", ignoreCase = true)) return 0
        val marked = try {
            republishMarker.tryMark(name)
        } catch (e: Exception) {
            log.error("[match] 일괄 재발행 '{}' 표식 실패, 보내지 않습니다", name, e)
            return 0
        }
        if (!marked) {
            log.info("[match] 일괄 재발행 '{}' 이미 실행됨, 건너뜁니다. 다시 하려면 이름을 바꾸세요", name)
            return 0
        }
        log.warn("[match] 일괄 재발행 '{}' 시작. 실제 AI_MATCH 알림이 갈 수 있습니다", name)
        return republishAll().also { log.warn("[match] 일괄 재발행 '{}' 끝 {}건", name, it) }
    }

    // 트랜잭션 밖이라 한 건씩 바로 나간다. 실종 차례에는 앞서 보낸 발견·보호동물의 벡터가 워커에 이미 있다(큐는 순서대로 처리)
    private fun republishAll(): Int = REPUBLISH_ORDER.sumOf { (type, eventType) ->
        val sources = repository.findEligibleSources(type)
            .filterNot { type == TargetType.MISSING && it.userSeq?.let(::isMissingMatchExcluded) == true }
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
