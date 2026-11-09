package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils

/** 서비스 테스트용 메모리 저장소. 포트 계약만 흉내 낸다 */
class FakeSimulationStore : SimulationSessionRepository, SimulationActionLogRepository, BreedReader, SessionDetailReader {

    val sessions = mutableListOf<SimulationSession>()
    val logs = mutableListOf<SimulationActionLog>()
    val breeds = mutableMapOf<Int, BreedInfo>()
    val breedLookups = mutableListOf<Int>()
    val equipment = mutableMapOf<Long, Equipment>()
    val activeWalks = mutableMapOf<Long, Long>()
    val reported = mutableSetOf<Long>()

    override fun findActiveForUpdate(userSeq: Int) =
        sessions.firstOrNull { it.userSeq == userSeq && it.status == SessionStatus.IN_PROGRESS }

    override fun findByIdForUpdate(sessionSeq: Long) = sessions.firstOrNull { it.sessionSeq == sessionSeq }

    /** 정렬은 기본값(startedDttm desc, PK desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPageByUser(userSeq: Int, status: SessionStatus?, pageable: Pageable): Page<SimulationSession> {
        val all = sessions.filter { it.userSeq == userSeq && (status == null || it.status == status) }
            .sortedWith(compareByDescending<SimulationSession> { it.startedDttm }.thenByDescending { it.sessionSeq })
        val content = all.drop(pageable.offset.toInt()).take(pageable.pageSize)
        return PageImpl(content, pageable, all.size.toLong())
    }

    override fun save(session: SimulationSession) = session.also {
        if (it.sessionSeq == null) {
            ReflectionTestUtils.setField(it, "sessionSeq", 3000L + sessions.size + 1)
            sessions += it
        }
    }

    override fun save(log: SimulationActionLog) = log.also { logs += it }

    override fun findBreed(breedSeq: Int): BreedInfo? {
        breedLookups += breedSeq
        return breeds[breedSeq]
    }

    override fun findBreeds(breedSeqs: Collection<Int>) = breedSeqs.mapNotNull { breeds[it] }.associateBy { it.breedSeq }

    override fun findEquipment(sessionSeq: Long) = equipment[sessionSeq] ?: Equipment.NONE

    override fun findActiveWalkSeq(sessionSeq: Long) = activeWalks[sessionSeq]

    override fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>) = sessionSeqs.filterTo(mutableSetOf()) { it in reported }

    fun assembler() = SimulationSessionDetailAssembler(this, this)

    companion object {
        val MALTESE = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")
        val KOREAN_SHORTHAIR = BreedInfo(31, "코리안 숏헤어", CatalogSpeciesCode.CAT, Personality.TIMID, null)
        val ETC = BreedInfo(90, "기타", CatalogSpeciesCode.ETC, Personality.TIMID, null)
    }
}
