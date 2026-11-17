package com.ssafy.animal.core.match.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchCandidate
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.match.application.port.out.MatchCandidateRepository
import com.ssafy.animal.core.match.application.port.out.MatchCriteria
import com.ssafy.animal.core.match.application.port.out.MatchRepublishMarker
import com.ssafy.animal.core.match.application.port.out.MatchSource
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchCandidateServiceTest {

    private val at = OffsetDateTime.parse("2026-11-02T17:50:00+09:00")
    private val criteriaSeen = mutableListOf<MatchCriteria>()
    private val events = mutableListOf<Any>()
    private val marks = mutableSetOf<String>()
    private var marker = MatchRepublishMarker { marks.add(it) }

    private val repository = object : MatchCandidateRepository {
        override fun findCandidates(sourceType: TargetType, sourceSeq: Long, criteria: MatchCriteria): List<MatchCandidate> {
            criteriaSeen += criteria
            return if (sourceType == TargetType.MISSING) listOf(MatchCandidate(TargetType.FOUND, 210)) else emptyList()
        }

        override fun findEligibleSources(type: TargetType) = when (type) {
            TargetType.MISSING -> listOf(MatchSource(101, "DOG", at, listOf("m.jpg"), userSeq = 147))
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
        { marker.tryMark(it) },
        MatchCandidateProperties(maxDistanceKm = 12.5, dateGrace = Duration.ofHours(6), limit = 7),
    )

    @Test
    fun `후보 조건은 설정값으로 넘긴다`() {
        assertEquals(listOf(MatchCandidate(TargetType.FOUND, 210)), service.findCandidates(TargetType.MISSING, 101))
        assertEquals(MatchCriteria(12.5, Duration.ofHours(6), 7), criteriaSeen.single())
    }

    @Test
    fun `일괄 재발행은 발견·보호동물 먼저 실종 나중, 후보가 없어도 보낸다`() {
        assertEquals(4, service.republishOnce("2026-11-12-first"))
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

    @Test
    fun `같은 실행 이름은 표식을 얻은 쪽만 보내고 이름을 바꾸면 다시 보낸다`() {
        assertEquals(4, service.republishOnce(" run-1 "))
        assertEquals(0, service.republishOnce("run-1"))
        assertEquals(4, events.size)
        assertEquals(4, service.republishOnce("run-2"))
        assertEquals(setOf("run-1", "run-2"), marks)
    }

    @Test
    fun `시연용 제외 회원의 실종신고는 일괄 재발행에서 빠진다`() {
        val excluding = MatchCandidateService(
            repository,
            object : EventPublisher {
                override fun publish(event: Any) {
                    events += event
                }
            },
            { marker.tryMark(it) },
            MatchCandidateProperties(),
            MatchExclusionProperties(excludedMissingUserSeqs = setOf(147)),
        )

        assertTrue(excluding.isMissingMatchExcluded(147))
        assertEquals(3, excluding.republishOnce("run-1"))
        assertTrue(events.none { it is MatchRequested && it.sourceType == TargetType.MISSING })
    }

    @Test
    fun `빈 값·false면 표식도 넣지 않고 실행하지 않는다`() {
        listOf("", "  ", "false", "FALSE").forEach { assertEquals(0, service.republishOnce(it)) }
        assertTrue(events.isEmpty())
        assertTrue(marks.isEmpty())
    }

    @Test
    fun `표식 저장소에 닿지 못하면 보내지 않는다`() {
        marker = MatchRepublishMarker { error("redis down") }
        assertEquals(0, service.republishOnce("run-1"))
        assertTrue(events.isEmpty())
    }
}
