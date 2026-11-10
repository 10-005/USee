package com.ssafy.animal.notification.application.port.`in`

import com.ssafy.animal.common.event.ChatMessageSent
import com.ssafy.animal.common.event.CommentDeleted
import com.ssafy.animal.common.event.DeviceTokenDeactivateRequested
import com.ssafy.animal.common.event.NotificationCreated
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.SimulationPushRequested
import com.ssafy.animal.common.event.UserDeleted

/** 21.6 notification이 소비하는 이벤트 7종. FCM 실패는 로그만 남기고 재시도하지 않는다(17.8) */
interface NotificationEventUseCase {
    /** INSERT + FCM(title=유형 고정 문구, body=message, data=이동 정보) */
    fun onNotificationRequested(event: NotificationRequested)

    /** core-api가 이미 INSERT한 AI_MATCH. FCM만 */
    fun onNotificationCreated(event: NotificationCreated)

    /** (targetType, commentSeq)가 같은 COMMENT 계열 message를 "삭제된 댓글입니다"로(Q448) */
    fun onCommentDeleted(event: CommentDeleted)

    /** 저장 없이 FCM만(Q481). data {target: SIMULATION} */
    fun onSimulationPushRequested(event: SimulationPushRequested)

    /** 활성 토큰 전부 비활성화 */
    fun onUserDeleted(event: UserDeleted)

    /** 로그아웃 기기 토큰만 비활성화(Q419 A) */
    fun onDeviceTokenDeactivateRequested(event: DeviceTokenDeactivateRequested)

    /** 수신자별 FCM(title=보낸 사람, body=미리보기, data.notificationType=CHAT), 저장 없음(R9 C·Q516) */
    fun onChatMessageSent(event: ChatMessageSent)
}
