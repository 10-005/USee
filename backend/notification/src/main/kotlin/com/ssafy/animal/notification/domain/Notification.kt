package com.ssafy.animal.notification.domain

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.persistence.BaseCreatedEntity
import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime

/** API 명세 17장·19장 NOTIFICATION_ 코드 */
enum class NotificationErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
}

/**
 * 1.1 Q433 쓰기 재확인의 정지 계정 403. 같은 코드가 core-api AuthErrorCode에 있지만 notification은 core-api를 의존하지 않아 따로 둔다.
 * (common CommonErrorCode로 옮기면 지운다 — 보고서 코디네이터 요청)
 */
enum class AccountErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다."),
}

/** 17.1 유형별 서버 고정 title(Q447, 문구 [가안]). DB에 저장하지 않아 문구를 바꾸면 과거 알림도 새 문구로 보인다 */
val NotificationType.title: String
    get() = when (this) {
        NotificationType.COMMENT -> "회원님의 게시글에 댓글이 달렸어요"
        NotificationType.COMMENT_REPLY -> "회원님의 댓글에 답글이 달렸어요"
        NotificationType.AI_MATCH -> "내 아이 관련 제보가 올라왔어요!"
        NotificationType.ADOPTION_SUBMITTED -> "새 입양 신청이 도착했어요"
        NotificationType.ADOPTION_APPROVED -> "입양 신청이 승인되었어요"
        NotificationType.ADOPTION_REJECTED -> "입양 신청이 거절되었어요"
        NotificationType.ADOPTION_COMPLETED -> "입양이 완료되었어요"
        NotificationType.SHELTER -> "담당 발견 신고가 배정되었어요"
        NotificationType.SYSTEM -> "알림"
    }

/** DB 명세 33. notifications(core 스키마, 쓰기는 notification. AI_MATCH 행만 core-api가 INSERT) */
@Entity
@Table(name = "notifications")
class Notification(
    recipientUserSeq: Int,
    notificationType: NotificationType,
    message: String,
    targetType: TargetType?,
    targetSeq: Long?,
    commentSeq: Long? = null,
    parentCommentSeq: Long? = null,
    missingReportSeq: Int? = null,
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
    var notificationType: NotificationType = notificationType
        protected set

    @Column(nullable = false)
    var message: String = message
        protected set

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 40)
    var targetType: TargetType? = targetType
        protected set

    @Column(updatable = false)
    var targetSeq: Long? = targetSeq
        protected set

    @Column(updatable = false)
    var missingReportSeq: Int? = missingReportSeq
        protected set

    @Column(updatable = false)
    var commentSeq: Long? = commentSeq
        protected set

    @Column(updatable = false)
    var parentCommentSeq: Long? = parentCommentSeq
        protected set

    @Column(nullable = false)
    var isRead: Boolean = false
        protected set

    var readDttm: OffsetDateTime? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    /** 이미 읽은 알림은 처음 읽은 시각을 유지한다 */
    fun markRead(now: OffsetDateTime) {
        if (isRead) return
        isRead = true
        readDttm = now
    }

    fun softDelete() {
        isDeleted = true
    }
}

/** DB 명세 34. device_tokens. 사용자당 활성 1개, 같은 토큰은 한 계정에서만 활성(부분 UNIQUE) */
@Entity
@Table(name = "device_tokens")
class DeviceToken(
    userSeq: Int,
    fcmToken: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var deviceTokenSeq: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false, updatable = false)
    var fcmToken: String = fcmToken
        protected set

    @Column(nullable = false)
    var isActive: Boolean = true
        protected set
}
