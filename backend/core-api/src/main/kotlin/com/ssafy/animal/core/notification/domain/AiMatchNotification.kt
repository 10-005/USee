package com.ssafy.animal.core.notification.domain

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * notifications의 AI_MATCH 행. 테이블은 notification 서비스 소유지만 AI 중복 방지(17.8)가
 * missing_reports 행 잠금과 한 트랜잭션이어야 해서 이 한 유형만 core-api가 직접 INSERT한다(쓰기 소유권 예외)
 */
@Entity
@Table(name = "notifications")
class AiMatchNotification(
    recipientUserSeq: Int,
    message: String,
    targetType: TargetType,
    targetSeq: Long,
    missingReportSeq: Int,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var notificationSeq: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var recipientUserSeq: Int = recipientUserSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    var notificationType: NotificationType = NotificationType.AI_MATCH
        protected set

    @Column(nullable = false, updatable = false)
    var message: String = message
        protected set

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 40)
    var targetType: TargetType = targetType
        protected set

    @Column(updatable = false)
    var targetSeq: Long = targetSeq
        protected set

    @Column(updatable = false)
    var missingReportSeq: Int = missingReportSeq
        protected set

    @Column(nullable = false, updatable = false)
    var isRead: Boolean = false
        protected set

    @Column(nullable = false, updatable = false)
    var isDeleted: Boolean = false
        protected set
}
