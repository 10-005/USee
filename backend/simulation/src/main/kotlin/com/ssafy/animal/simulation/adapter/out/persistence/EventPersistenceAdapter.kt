package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.simulation.application.port.out.EventCounts
import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.QEventOccurrence.Companion.eventOccurrence
import com.ssafy.animal.simulation.domain.QEventScenario.Companion.eventScenario
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface EventScenarioJpaRepository : JpaRepository<EventScenario, Long>

interface EventOccurrenceJpaRepository : JpaRepository<EventOccurrence, Long>

/**
 * 시나리오·발생 이벤트. JPQL(QueryDSL) 조회는 같은 트랜잭션의 변경을 먼저 flush하므로
 * 같은 요청에서 만료·해결한 이벤트도 카운트에 바로 반영된다(그래서 JDBC 직접 쿼리에서 옮겼다).
 */
@Repository
class EventPersistenceAdapter(
    private val scenarioJpaRepository: EventScenarioJpaRepository,
    private val occurrenceJpaRepository: EventOccurrenceJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : EventScenarioRepository, EventOccurrenceRepository {

    override fun findByScenarioSeq(scenarioSeq: Long): EventScenario? = scenarioJpaRepository.findById(scenarioSeq).orElse(null)

    override fun findByScenarioSeqs(scenarioSeqs: Collection<Long>): Map<Long, EventScenario> =
        if (scenarioSeqs.isEmpty()) emptyMap() else scenarioJpaRepository.findAllById(scenarioSeqs.toSet()).associateBy { it.scenarioSeq }

    override fun findActive(context: TriggerContext, type: TriggerType): List<EventScenario> =
        queryFactory.selectFrom(eventScenario)
            .where(eventScenario.isActive.isTrue, eventScenario.triggerContext.eq(context), eventScenario.triggerType.eq(type))
            .orderBy(eventScenario.scenarioSeq.asc())
            .fetch()

    override fun save(occurrence: EventOccurrence): EventOccurrence = occurrenceJpaRepository.save(occurrence)

    override fun findPending(sessionSeq: Long): EventOccurrence? =
        queryFactory.selectFrom(eventOccurrence)
            .where(eventOccurrence.sessionSeq.eq(sessionSeq), eventOccurrence.status.eq(OccurrenceStatus.PENDING))
            .fetchFirst()

    override fun countOccurredSince(sessionSeq: Long, context: TriggerContext, type: TriggerType, since: OffsetDateTime): Int =
        (
            queryFactory.select(eventOccurrence.count())
                .from(eventOccurrence)
                .join(eventScenario).on(eventScenario.scenarioSeq.eq(eventOccurrence.scenarioSeq))
                .where(
                    eventOccurrence.sessionSeq.eq(sessionSeq),
                    eventOccurrence.occurredDttm.goe(since),
                    eventScenario.triggerContext.eq(context),
                    eventScenario.triggerType.eq(type),
                )
                .fetchOne() ?: 0L
            ).toInt()

    override fun findPage(sessionSeq: Long, status: OccurrenceStatus?, pageable: Pageable): Page<EventOccurrence> {
        val condition = eventOccurrence.sessionSeq.eq(sessionSeq)
            .let { base -> status?.let { base.and(eventOccurrence.status.eq(it)) } ?: base }
        val content = queryFactory.selectFrom(eventOccurrence)
            .where(condition)
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(eventOccurrence.count()).from(eventOccurrence).where(condition).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun countEvents(sessionSeq: Long): EventCounts {
        val statuses = queryFactory.select(eventOccurrence.status)
            .from(eventOccurrence)
            .where(eventOccurrence.sessionSeq.eq(sessionSeq), eventOccurrence.status.ne(OccurrenceStatus.PENDING))
            .fetch()
        return EventCounts(total = statuses.size, resolved = statuses.count { it == OccurrenceStatus.RESOLVED })
    }

    companion object {
        /** 18.12 정렬 화이트리스트는 occurredDttm뿐(기본 desc, 동률 PK desc) */
        val SORT_POLICY = SortPolicy(
            fields = mapOf("occurredDttm" to eventOccurrence.occurredDttm),
            default = listOf(eventOccurrence.occurredDttm.desc()),
            tieBreaker = eventOccurrence.occurrenceSeq.desc(),
        )
    }
}
