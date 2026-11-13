package com.ssafy.animal.core.adoption.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantSimulationReport
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantSimulationReportUseCase
import com.ssafy.animal.core.adoption.application.port.`in`.SimulationBreed
import com.ssafy.animal.core.adoption.application.port.out.AdoptionApplicationRepository
import com.ssafy.animal.core.adoption.application.port.out.SimulationReportReader
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApplicantSimulationReportService(
    private val applicationRepository: AdoptionApplicationRepository,
    private val reportReader: SimulationReportReader,
    private val imageUseCase: ImageUseCase,
) : ApplicantSimulationReportUseCase {

    @Transactional(readOnly = true)
    override fun getApplicantReports(shelter: AuthUser, applicationSeq: Long): List<ApplicantSimulationReport> {
        // 12.3 상세와 같은 공개 범위: 소속 보호소의 제출된 신청만(DRAFT·CANCELED·타 보호소는 존재를 감춤, Q422·Q434)
        val application = applicationRepository.find(applicationSeq)
            ?.takeIf { it.shelterSeq.toLong() == shelter.shelterSeq && it.isVisibleToShelter }
            ?: throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND)
        val rows = reportReader.findEndedReports(application.userSeq)
        val imageUrls = imageUseCase.findSummaries(rows.mapNotNull { it.representativeImageSeq }).mapValues { it.value.imageUrl }
        return rows.map {
            ApplicantSimulationReport(
                sessionSeq = it.sessionSeq,
                animalName = it.animalName,
                breed = SimulationBreed(it.breedSeq, it.breedName, SpeciesCode.valueOf(it.speciesCode), it.representativeImageSeq?.let(imageUrls::get)),
                startedDttm = it.startedDttm,
                endedDttm = it.endedDttm,
                totalSpent = it.totalSpent,
            )
        }
    }
}
