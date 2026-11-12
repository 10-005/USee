package com.ssafy.animal.common.event

import java.time.OffsetDateTime

// API 명세 21.6 서비스 간 이벤트. 발행은 EventPublisher.publish(이벤트)만 쓴다.
// RabbitMQ topic 교환기 animal.events에 라우팅 키 = 클래스 이름(예: UserDeleted)으로 나간다.
// 소비 서비스는 자기 큐({서비스}.{이벤트 이름})를 그 라우팅 키로 묶는다. 필드를 바꾸면 소비 쪽도 같이 바꾼다.

/** notifications.notification_type 9종(Q447). 앱 title은 이 값으로 notification이 정한다 */
enum class NotificationType {
    COMMENT, COMMENT_REPLY, AI_MATCH,
    ADOPTION_SUBMITTED, ADOPTION_APPROVED, ADOPTION_REJECTED, ADOPTION_COMPLETED,
    SHELTER, SYSTEM,
}

/** 다형성 대상 공통 코드(Q387). 테이블마다 허용 값은 DB CHECK가 더 좁힌다 */
enum class TargetType { USER, MISSING, FOUND, SHELTER_ANIMAL, ADOPTION, MISSING_COMMENT, FOUND_COMMENT }

/**
 * core-api → notification. notifications INSERT + FCM(COMMENT·COMMENT_REPLY·ADOPTION_*·SHELTER).
 * message는 발행 쪽이 완성한 본문(body)이며 DB 저장값과 FCM body가 같다(Q381). COMMENT 계열은 target=게시물(MISSING/FOUND)
 */
data class NotificationRequested(
    val recipientUserSeq: Long,
    val notificationType: NotificationType,
    val message: String,
    val targetType: TargetType?,
    val targetSeq: Long?,
    val commentSeq: Long? = null,
    val parentCommentSeq: Long? = null,
)

/** core-api → notification. core-api가 직접 INSERT한 AI_MATCH 행의 FCM 발송만(17.8 ⑥) */
data class NotificationCreated(
    val notificationSeq: Long,
    val recipientUserSeq: Long,
    val notificationType: NotificationType,
    val message: String,
    val targetType: TargetType?,
    val targetSeq: Long?,
)

/** core-api(13.5) → notification. (targetType, commentSeq)가 같은 COMMENT 계열 알림 message를 "삭제된 댓글입니다"로(Q448) */
data class CommentDeleted(
    val targetType: TargetType,
    val commentSeq: Long,
)

/** core-api(2.6) → notification·simulation. 활성 FCM 토큰 비활성화, 진행 중 세션 ABANDONED + 리포트 */
data class UserDeleted(
    val userSeq: Long,
)

/** core-api(2.4 fcmToken) → notification. 그 기기 토큰만 비활성화(Q419 A) */
data class DeviceTokenDeactivateRequested(
    val userSeq: Long,
    val fcmToken: String,
)

enum class SimulationPushType { EVENT, SESSION_END }

/** simulation → notification. 저장 없이 FCM만(Q481). EVENT는 발생 커밋 후, SESSION_END는 리포트 summary 저장 후(Q487) */
data class SimulationPushRequested(
    val userSeq: Long,
    val pushType: SimulationPushType,
    val title: String,
    val body: String,
)

/** chat → notification. 보낸 사람을 뺀 참여자에게 FCM(title·body 포함, Q516), 저장 없음(R9 C). 수신자는 chat이 계산(보호소는 담당 계정) */
data class ChatMessageSent(
    val chatRoomSeq: Long,
    val messageSeq: Long,
    val senderUserSeq: Long,
    val senderDisplayName: String,
    val preview: String,
    val recipientUserSeqs: List<Long>,
)

/** 명세의 매칭 요청 이벤트 4종. 페이로드가 같아 한 클래스로 보내고 이 값으로 구분한다 */
enum class MatchEventType { MISSING_REPORT_REGISTERED, FOUND_REPORT_REGISTERED, SHELTER_ANIMAL_REGISTERED, IMAGES_CHANGED }

/**
 * core-api → AI 워커(Q432·Q523). 워커는 서비스 데이터를 읽지 않고 이 페이로드와 S3 키, 자기 스키마(ai)의 벡터만 쓴다.
 * sourceType은 MISSING/FOUND/SHELTER_ANIMAL. candidates는 core-api가 추린 비교 상대(종·날짜·거리·작성자, Q523)이고
 * 비어 있어도 보낸다(워커가 source 벡터를 저장해 두어야 나중에 다른 신고의 후보가 된다)
 */
data class MatchRequested(
    val eventType: MatchEventType,
    val sourceType: TargetType,
    val sourceSeq: Long,
    val imageKeys: List<String>,
    /** DOG/CAT/ETC */
    val speciesCode: String,
    val occurredAt: OffsetDateTime,
    val candidates: List<MatchCandidate> = emptyList(),
)

/** 매칭 후보. source가 MISSING이면 type은 FOUND/SHELTER_ANIMAL, 그 밖이면 MISSING */
data class MatchCandidate(
    val type: TargetType,
    val seq: Long,
)

/** AI 워커 → core-api(Q432). core-api가 자격 재검사 후 최초 1회만 AI_MATCH 알림(17.8). targetType은 FOUND/SHELTER_ANIMAL */
data class AiMatchFound(
    val missingReportSeq: Long,
    val targetType: TargetType,
    val targetSeq: Long,
    val score: Double,
    val modelVersion: String,
)
