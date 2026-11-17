package com.ssafy.animal.core.notification.application.service

import com.ssafy.animal.common.event.AiMatchFound
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.NotificationCreated
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.notification.application.port.`in`.AiMatchUseCase
import com.ssafy.animal.core.notification.application.port.out.AiMatchRepository
import com.ssafy.animal.core.notification.application.port.out.FoundRegion
import com.ssafy.animal.core.notification.domain.AiMatchNotification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AiMatchService(
    private val aiMatchRepository: AiMatchRepository,
    private val eventPublisher: EventPublisher,
) : AiMatchUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun handle(event: AiMatchFound): Boolean {
        // ① 매칭 상대 자격(발견: ACTIVE·비삭제·비탈퇴, 보호동물: PROTECTING·비삭제, 입양 상태 무관)과 body 템플릿
        val message = when (event.targetType) {
            TargetType.FOUND -> aiMatchRepository.findEligibleFoundRegion(event.targetSeq)?.let(::foundMessage)
            TargetType.SHELTER_ANIMAL -> aiMatchRepository.findEligibleShelterName(event.targetSeq)?.let(::shelterAnimalMessage)
            else -> null
        } ?: return skip(event, "매칭 상대 자격 미달")

        // ② 실종 신고 행 잠금(자격 검사 포함) → ③ 같은 쌍 중복 확인. 잠금 덕분에 동시 수신도 한 건만 INSERT
        val recipient = aiMatchRepository.lockEligibleMissingAuthor(event.missingReportSeq)
            ?: return skip(event, "실종 신고 자격 미달")
        if (aiMatchRepository.existsAiMatch(event.missingReportSeq, event.targetType, event.targetSeq)) {
            return skip(event, "이미 보낸 쌍")
        }

        // ④ INSERT → ⑤ 커밋 → ⑥ NotificationCreated(트랜잭션 안의 publish는 커밋 후에만 나간다)
        val saved = aiMatchRepository.save(
            AiMatchNotification(recipient, message, event.targetType, event.targetSeq, Math.toIntExact(event.missingReportSeq)),
        )
        eventPublisher.publish(
            NotificationCreated(saved.notificationSeq!!, recipient.toLong(), saved.notificationType, message, event.targetType, event.targetSeq),
        )
        log.info("[ai-match] 알림 생성 notificationSeq={} score={} model={}", saved.notificationSeq, event.score, event.modelVersion)
        return true
    }

    private fun skip(event: AiMatchFound, reason: String): Boolean {
        log.info("[ai-match] 건너뜀({}) {}", reason, event)
        return false
    }

    companion object {
        /** 17.1 AI_MATCH body 템플릿 [가안] */
        fun foundMessage(region: FoundRegion): String =
            if (region.sidoName == null || region.sigunguName == null) {
                "근처에서 목격제보가 올라왔어요. 확인해보세요."
            } else {
                "${region.sidoName} ${region.sigunguName}에서 목격제보가 올라왔어요. 확인해보세요."
            }

        fun shelterAnimalMessage(shelterName: String) = "${shelterName}에서 비슷한 아이를 보호하고 있어요. 확인해보세요."
    }
}
