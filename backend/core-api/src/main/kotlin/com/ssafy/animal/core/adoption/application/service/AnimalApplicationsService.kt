package com.ssafy.animal.core.adoption.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.core.adoption.application.port.out.AdoptionApplicationRepository
import com.ssafy.animal.core.adoption.domain.AdoptionNotices
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.animal.application.port.`in`.AdoptionTarget
import com.ssafy.animal.core.animal.application.port.out.AnimalApplicationsPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/** animal 패키지의 AnimalApplicationsPort 구현. 신청서 엔티티·알림은 adoption이 소유하므로 여기서 처리한다 */
@Service
class AnimalApplicationsService(
    private val applicationRepository: AdoptionApplicationRepository,
    private val eventPublisher: EventPublisher,
) : AnimalApplicationsPort {

    @Transactional(readOnly = true)
    override fun hasActive(animalSeq: Int) = applicationRepository.existsActiveByAnimal(animalSeq)

    @Transactional(readOnly = true)
    override fun countReceived(animalSeq: Int) = toCounts(applicationRepository.countReceived(null, animalSeq))

    @Transactional(readOnly = true)
    override fun findApplicantName(applicationSeq: Long) = applicationRepository.find(applicationSeq)?.applicantName

    @Transactional
    override fun rejectActive(target: AdoptionTarget, reason: String, exceptApplicationSeq: Long?): Int {
        val now = OffsetDateTime.now()
        val rejected = applicationRepository.findActiveByAnimalForUpdate(target.animalSeq)
            .filter { it.applicationSeq != exceptApplicationSeq }
        rejected.forEach {
            it.reject(reason, now, null)
            eventPublisher.publish(AdoptionNotices.result(NotificationType.ADOPTION_REJECTED, it, target.shelterName, target.breedName))
        }
        return rejected.size
    }

    companion object {
        /** 12.16·11.10 건수: total은 네 상태의 합(DRAFT·CANCELED 제외) */
        fun toCounts(counts: Map<ApplicationStatus, Long>): Map<String, Long> {
            val result = linkedMapOf("total" to ApplicationStatus.RECEIVED.sumOf { counts[it] ?: 0L })
            ApplicationStatus.RECEIVED.forEach { result[it.name] = counts[it] ?: 0L }
            return result
        }
    }
}
