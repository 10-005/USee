package com.ssafy.animal.core.notification.adapter.out.persistence

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.notification.application.port.out.AiMatchRepository
import com.ssafy.animal.core.notification.application.port.out.FoundRegion
import com.ssafy.animal.core.notification.domain.AiMatchNotification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface AiMatchNotificationJpaRepository : JpaRepository<AiMatchNotification, Long> {
    // ix_notification_ai_match를 탄다. is_deleted는 거르지 않는다(삭제된 알림도 재생성 금지, 17.2~17.5)
    fun existsByNotificationTypeAndMissingReportSeqAndTargetTypeAndTargetSeq(
        notificationType: NotificationType,
        missingReportSeq: Int,
        targetType: TargetType,
        targetSeq: Long,
    ): Boolean

    // 신고·보호동물 엔티티는 각 도메인 몫이라 네이티브로 읽는다
    @Query(
        nativeQuery = true,
        value = """
        select r.user_seq from missing_reports r join users u on u.user_seq = r.user_seq
        where r.missing_report_seq = :seq and r.status = 'ACTIVE' and r.is_deleted = false and u.is_deleted = false
        for update of r
        """,
    )
    fun lockEligibleMissingAuthor(@Param("seq") seq: Long): Int?

    @Query(
        nativeQuery = true,
        value = """
        select d.sido_name as sidoName, d.sigungu_name as sigunguName
        from found_reports r join users u on u.user_seq = r.user_seq
        left join districts d on d.district_code = r.district_code
        where r.found_report_seq = :seq and r.status = 'ACTIVE' and r.is_deleted = false and u.is_deleted = false
        """,
    )
    fun findEligibleFoundRegion(@Param("seq") seq: Long): FoundRegion?

    @Query(
        nativeQuery = true,
        value = """
        select s.shelter_name from shelter_animals a join shelters s on s.shelter_seq = a.shelter_seq
        where a.animal_seq = :seq and a.protection_status = 'PROTECTING' and a.is_deleted = false
        """,
    )
    fun findEligibleShelterName(@Param("seq") seq: Long): String?
}

@Repository
class AiMatchPersistenceAdapter(
    private val jpaRepository: AiMatchNotificationJpaRepository,
) : AiMatchRepository {

    override fun lockEligibleMissingAuthor(missingReportSeq: Long) = jpaRepository.lockEligibleMissingAuthor(missingReportSeq)

    override fun findEligibleFoundRegion(foundReportSeq: Long) = jpaRepository.findEligibleFoundRegion(foundReportSeq)

    override fun findEligibleShelterName(animalSeq: Long) = jpaRepository.findEligibleShelterName(animalSeq)

    override fun existsAiMatch(missingReportSeq: Long, targetType: TargetType, targetSeq: Long) =
        jpaRepository.existsByNotificationTypeAndMissingReportSeqAndTargetTypeAndTargetSeq(
            NotificationType.AI_MATCH, Math.toIntExact(missingReportSeq), targetType, targetSeq,
        )

    override fun save(notification: AiMatchNotification): AiMatchNotification = jpaRepository.save(notification)
}
