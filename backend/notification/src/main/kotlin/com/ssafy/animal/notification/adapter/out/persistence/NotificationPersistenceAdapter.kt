package com.ssafy.animal.notification.adapter.out.persistence

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.notification.application.port.out.DeviceTokenRepository
import com.ssafy.animal.notification.application.port.out.NotificationRepository
import com.ssafy.animal.notification.application.port.out.UserStatus
import com.ssafy.animal.notification.application.port.out.UserStatusReader
import com.ssafy.animal.notification.domain.DeviceToken
import com.ssafy.animal.notification.domain.Notification
import com.ssafy.animal.notification.domain.QNotification.Companion.notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

interface NotificationJpaRepository : JpaRepository<Notification, Long> {
    fun findByNotificationSeqAndRecipientUserSeqAndIsDeletedFalse(notificationSeq: Long, recipientUserSeq: Int): Notification?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update Notification n set n.isRead = true, n.readDttm = :now
        where n.recipientUserSeq = :userSeq and n.isDeleted = false and n.isRead = false
        """,
    )
    fun markAllRead(@Param("userSeq") userSeq: Int, @Param("now") now: OffsetDateTime): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.isDeleted = true where n.recipientUserSeq = :userSeq and n.isDeleted = false")
    fun softDeleteAll(@Param("userSeq") userSeq: Int): Int

    // ix_notification_comment(target_type, comment_seq)를 탄다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update Notification n set n.message = :message
        where n.targetType = :targetType and n.commentSeq = :commentSeq and n.notificationType in :types
        """,
    )
    fun replaceCommentMessage(
        @Param("targetType") targetType: TargetType,
        @Param("commentSeq") commentSeq: Long,
        @Param("message") message: String,
        @Param("types") types: Collection<NotificationType>,
    ): Int

    // users는 core-api 소유라 네이티브로 읽기만 한다(Q420 D)
    @Query(nativeQuery = true, value = "select is_deleted as deleted, account_status as accountStatus from users where user_seq = :userSeq")
    fun findUserStatus(@Param("userSeq") userSeq: Long): UserStatus?
}

interface DeviceTokenJpaRepository : JpaRepository<DeviceToken, Long> {
    fun findByUserSeqAndIsActiveTrue(userSeq: Int): DeviceToken?

    fun findByUserSeqInAndIsActiveTrue(userSeqs: Collection<Int>): List<DeviceToken>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DeviceToken d set d.isActive = false, d.modDttm = :now where d.userSeq = :userSeq and d.isActive = true")
    fun deactivateUser(@Param("userSeq") userSeq: Int, @Param("now") now: OffsetDateTime): Int

    @Transactional // FCM 발송 실패 처리(트랜잭션 밖)에서도 호출된다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DeviceToken d set d.isActive = false, d.modDttm = :now where d.fcmToken = :token and d.isActive = true")
    fun deactivateToken(@Param("token") token: String, @Param("now") now: OffsetDateTime): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update DeviceToken d set d.isActive = false, d.modDttm = :now
        where d.userSeq = :userSeq and d.fcmToken = :token and d.isActive = true
        """,
    )
    fun deactivateUserToken(@Param("userSeq") userSeq: Int, @Param("token") token: String, @Param("now") now: OffsetDateTime): Int
}

@Repository
class NotificationPersistenceAdapter(
    private val notificationJpaRepository: NotificationJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : NotificationRepository, UserStatusReader {

    private val sortPolicy = SortPolicy.regDttmOnly(notification.regDttm, notification.notificationSeq)

    override fun save(notification: Notification): Notification = notificationJpaRepository.save(notification)

    override fun findOwn(recipientUserSeq: Int, notificationSeq: Long) =
        notificationJpaRepository.findByNotificationSeqAndRecipientUserSeqAndIsDeletedFalse(notificationSeq, recipientUserSeq)

    override fun findPage(recipientUserSeq: Int, isRead: Boolean?, notificationType: NotificationType?, pageable: Pageable): Page<Notification> {
        val conditions = arrayOf<BooleanExpression?>(
            notification.recipientUserSeq.eq(recipientUserSeq),
            notification.isDeleted.isFalse,
            isRead?.let(notification.isRead::eq),
            notificationType?.let(notification.notificationType::eq),
        )
        val content = queryFactory.selectFrom(notification)
            .where(*conditions)
            .orderBy(*sortPolicy.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(notification.count()).from(notification).where(*conditions).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun markAllRead(recipientUserSeq: Int, now: OffsetDateTime) = notificationJpaRepository.markAllRead(recipientUserSeq, now)

    override fun softDeleteAll(recipientUserSeq: Int) = notificationJpaRepository.softDeleteAll(recipientUserSeq)

    override fun replaceCommentMessage(targetType: TargetType, commentSeq: Long, message: String) =
        notificationJpaRepository.replaceCommentMessage(targetType, commentSeq, message, COMMENT_TYPES)

    override fun find(userSeq: Long) = notificationJpaRepository.findUserStatus(userSeq)

    companion object {
        private val COMMENT_TYPES = listOf(NotificationType.COMMENT, NotificationType.COMMENT_REPLY)
    }
}

@Repository
class DeviceTokenPersistenceAdapter(
    private val deviceTokenJpaRepository: DeviceTokenJpaRepository,
) : DeviceTokenRepository {

    override fun findActive(userSeq: Int) = deviceTokenJpaRepository.findByUserSeqAndIsActiveTrue(userSeq)

    override fun findActive(userSeqs: Collection<Int>) =
        if (userSeqs.isEmpty()) emptyList() else deviceTokenJpaRepository.findByUserSeqInAndIsActiveTrue(userSeqs)

    override fun save(token: DeviceToken): DeviceToken = deviceTokenJpaRepository.save(token)

    override fun deactivateUser(userSeq: Int, now: OffsetDateTime) = deviceTokenJpaRepository.deactivateUser(userSeq, now)

    override fun deactivateToken(fcmToken: String, now: OffsetDateTime) = deviceTokenJpaRepository.deactivateToken(fcmToken, now)

    override fun deactivateUserToken(userSeq: Int, fcmToken: String, now: OffsetDateTime) =
        deviceTokenJpaRepository.deactivateUserToken(userSeq, fcmToken, now)
}
