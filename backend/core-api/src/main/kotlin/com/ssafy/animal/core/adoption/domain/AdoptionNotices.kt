package com.ssafy.animal.core.adoption.domain

import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType

/** 12.14 입양 알림. 본문은 17.1 표 문구, target은 신청서(ADOPTION, applicationSeq) */
object AdoptionNotices {

    /** 제출 → 담당 보호소 SHELTER 계정 */
    fun submitted(recipientUserSeq: Int, application: AdoptionApplication, breedName: String) = NotificationRequested(
        recipientUserSeq.toLong(), NotificationType.ADOPTION_SUBMITTED, "$breedName 입양 신청서가 제출되었어요.",
        TargetType.ADOPTION, application.applicationSeq,
    )

    /** 승인·거절·완료 → 신청자. 거절 사유는 넣지 않는다 */
    fun result(type: NotificationType, application: AdoptionApplication, shelterName: String?, breedName: String?) = NotificationRequested(
        application.userSeq.toLong(), type, "${shelterName.orEmpty()} ${breedName.orEmpty()} 입양 신청 결과를 확인해보세요.".trim(),
        TargetType.ADOPTION, application.applicationSeq,
    )
}
