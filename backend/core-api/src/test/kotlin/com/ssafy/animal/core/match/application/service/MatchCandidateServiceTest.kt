package com.ssafy.animal.core.match.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchCandidate
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.match.application.port.out.MatchCandidateRepository
import com.ssafy.animal.core.match.application.port.out.MatchCriteria
import com.ssafy.animal.core.match.application.port.out.MatchSource
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchCandidateServiceTest {

    private val at = OffsetDateTime.parse("2026-11-02T17:50:00+09:00")
    private val criteriaSeen = mutableListOf<MatchCriteria>()
    private val events = mutableListOf<Any>()

    private val repository = object : MatchCandidateRepository {
        override fun findCandidates(sourceType: TargetType, sourceSeq: Long, criteria: MatchCriteria): List<MatchCandidate> {
            criteriaSeen += criteria
            return if (sourceType == TargetType.MISSING) listOf(MatchCandidate(TargetType.FOUND, 210)) else emptyList()
        }

        override fun findEligibleSources(type: TargetType) = when (type) {
            TargetType.MISSING -> listOf(MatchSource(101, "DOG", at, listOf("m.jpg")))
            TargetType.FOUND -> listOf(MatchSource(210, "DOG", at, listOf("f1.jpg", "f2.jpg")), MatchSource(211, "ETC", at, emptyList()))
            TargetType.SHELTER_ANIMAL -> listOf(MatchSource(35, "CAT", at, listOf("s.jpg")))
            else -> emptyList()
        }
    }

    private val service = MatchCandidateService(
        repository,
        object : EventPublisher {
            override fun publish(event: Any) {
                events += event
            }
        },
        MatchCandidateProperties(maxDistanceKm = 12.5, dateGrace = Duration.ofHours(6), limit = 7),
    )

    @Test
    fun `후보 조건은 설정값으로 넘긴다`() {
        assertEquals(listOf(MatchCandidate(TargetType.FOUND, 210)), service.findCandidates(TargetType.MISSING, 101))
        assertEquals(MatchCriteria(12.5, Duration.ofHours(6), 7), criteriaSeen.single())
    }

    @Test
    fun `일괄 재발행은 발견·보호동물 먼저 실종 나중, 후보가 없어도 보낸다`() {
        assertEquals(4, service.republishAll())
        assertEquals<List<Any>>(
            listOf(
                MatchRequested(MatchEventType.FOUND_REPORT_REGISTERED, TargetType.FOUND, 210, listOf("f1.jpg", "f2.jpg"), "DOG", at),
                MatchRequested(MatchEventType.FOUND_REPORT_REGISTERED, TargetType.FOUND, 211, emptyList(), "ETC", at),
                MatchRequested(MatchEventType.SHELTER_ANIMAL_REGISTERED, TargetType.SHELTER_ANIMAL, 35, listOf("s.jpg"), "CAT", at),
                MatchRequested(
                    MatchEventType.MISSING_REPORT_REGISTERED, TargetType.MISSING, 101, listOf("m.jpg"), "DOG", at,
                    listOf(MatchCandidate(TargetType.FOUND, 210)),
                ),
            ),
            events,
        )
    }
}
