package com.ssafy.animal.notification.domain

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.persistence.BaseTimeEntity
import com.ssafy.animal.common.response.ErrorDetail
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * DB 명세 v22 46번. user_notification_settings(core 스키마, 쓰기는 notification).
 * 행이 없으면 이 클래스의 기본값(전부 켜짐, 방해금지 꺼짐 22:00~08:00)으로 본다(Q511). 행은 첫 PATCH 때 만든다(Q512).
 * 설정은 FCM 발송 여부만 정하고 알림함 저장은 그대로 한다(Q510).
 */
@Entity
@Table(name = "user_notification_settings")
class NotificationSetting(
    userSeq: Int,
) : BaseTimeEntity() {

    @Id
    @Column(updatable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false)
    var simulationEnabled: Boolean = true
        protected set

    @Column(nullable = false)
    var chatEnabled: Boolean = true
        protected set

    @Column(nullable = false)
    var commentEnabled: Boolean = true
        protected set

    @Column(nullable = false)
    var aiMatchEnabled: Boolean = true
        protected set

    @Column(nullable = false)
    var adoptionEnabled: Boolean = true
        protected set

    @Column(nullable = false)
    var quietEnabled: Boolean = false
        protected set

    @Column(nullable = false)
    var quietStart: LocalTime = DEFAULT_QUIET_START
        protected set

    @Column(nullable = false)
    var quietEnd: LocalTime = DEFAULT_QUIET_END
        protected set

    /** 보낸 값만 바꾼다(null = 그대로). 방해금지 시각은 바꾼 결과로 검증한다 */
    fun change(change: NotificationSettingChange) {
        val start = change.quietStart ?: quietStart
        val end = change.quietEnd ?: quietEnd
        val errors = listOfNotNull(
            change.quietStart?.takeUnless(::isWholeMinute)?.let { ErrorDetail("quietStart", MINUTE_ONLY) },
            change.quietEnd?.takeUnless(::isWholeMinute)?.let { ErrorDetail("quietEnd", MINUTE_ONLY) },
        ).ifEmpty { if (start == end) listOf(ErrorDetail("quietEnd", SAME_START_END)) else emptyList() }
        if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = errors)

        change.simulationEnabled?.let { simulationEnabled = it }
        change.chatEnabled?.let { chatEnabled = it }
        change.commentEnabled?.let { commentEnabled = it }
        change.aiMatchEnabled?.let { aiMatchEnabled = it }
        change.adoptionEnabled?.let { adoptionEnabled = it }
        change.quietEnabled?.let { quietEnabled = it }
        quietStart = start
        quietEnd = end
    }

    /** 저장형 알림의 푸시 허용 여부. SHELTER·SYSTEM은 설정 대상이 아니라 항상 보낸다(Q513) */
    fun allows(type: NotificationType): Boolean = when (type) {
        NotificationType.COMMENT, NotificationType.COMMENT_REPLY -> commentEnabled
        NotificationType.AI_MATCH -> aiMatchEnabled
        NotificationType.ADOPTION_SUBMITTED, NotificationType.ADOPTION_APPROVED,
        NotificationType.ADOPTION_REJECTED, NotificationType.ADOPTION_COMPLETED -> adoptionEnabled
        NotificationType.SHELTER, NotificationType.SYSTEM -> true
    }

    /** 모의 입양 푸시. 방해금지는 모의 입양에만 적용하고 걸린 푸시는 버린다(Q514) */
    fun allowsSimulation(at: OffsetDateTime): Boolean = simulationEnabled && !(quietEnabled && isQuiet(at))

    /** 서울 시각 기준. 시작이 끝보다 늦으면 자정을 넘기는 구간이다(시작 포함, 끝 제외) */
    fun isQuiet(at: OffsetDateTime): Boolean {
        val time = at.atZoneSameInstant(SEOUL).toLocalTime()
        return if (quietStart < quietEnd) time >= quietStart && time < quietEnd else time >= quietStart || time < quietEnd
    }

    companion object {
        val DEFAULT_QUIET_START: LocalTime = LocalTime.of(22, 0)
        val DEFAULT_QUIET_END: LocalTime = LocalTime.of(8, 0)
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val MINUTE_ONLY = "시각은 HH:mm 형식으로 분 단위까지만 입력해 주세요."
        private const val SAME_START_END = "방해금지 시작과 끝 시각이 같을 수 없습니다."

        private fun isWholeMinute(time: LocalTime) = time.second == 0 && time.nano == 0
    }
}

/** PATCH 본문. null인 항목은 바꾸지 않는다 */
data class NotificationSettingChange(
    val simulationEnabled: Boolean? = null,
    val chatEnabled: Boolean? = null,
    val commentEnabled: Boolean? = null,
    val aiMatchEnabled: Boolean? = null,
    val adoptionEnabled: Boolean? = null,
    val quietEnabled: Boolean? = null,
    val quietStart: LocalTime? = null,
    val quietEnd: LocalTime? = null,
)
