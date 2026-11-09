package com.ssafy.animal.core.notification.application.port.out

import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.notification.domain.AiMatchNotification

interface AiMatchRepository {
    /** ACTIVE·비삭제·작성자 비탈퇴 실종 신고를 SELECT ... FOR UPDATE로 잠그고 작성자 userSeq. 자격 미달이면 null */
    fun lockEligibleMissingAuthor(missingReportSeq: Long): Int?

    /** ACTIVE·비삭제·작성자 비탈퇴 발견 신고의 시군구. 자격 미달이면 null, districtCode가 NULL이면 이름 둘 다 null */
    fun findEligibleFoundRegion(foundReportSeq: Long): FoundRegion?

    /** PROTECTING·비삭제 보호동물의 보호소명. 자격 미달이면 null */
    fun findEligibleShelterName(animalSeq: Long): String?

    /** is_deleted=true까지 포함한 같은 쌍의 AI_MATCH 존재 여부(쌍당 생애 1회) */
    fun existsAiMatch(missingReportSeq: Long, targetType: TargetType, targetSeq: Long): Boolean

    fun save(notification: AiMatchNotification): AiMatchNotification
}

interface FoundRegion {
    val sidoName: String?
    val sigunguName: String?
}
