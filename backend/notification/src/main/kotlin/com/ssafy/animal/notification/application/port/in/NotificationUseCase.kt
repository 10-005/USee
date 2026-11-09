package com.ssafy.animal.notification.application.port.`in`

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.response.PageResponse
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

/** 17.1~17.7 REST */
interface NotificationUseCase {
    fun list(userSeq: Long, isRead: Boolean?, notificationType: NotificationType?, pageable: Pageable): PageResponse<NotificationItem>

    /** 17.2 타인·삭제된 알림은 404 NOTIFICATION_NOT_FOUND */
    fun read(userSeq: Long, notificationSeq: Long): ReadResult

    /** 17.3 읽지 않은 비삭제 알림 전부 */
    fun readAll(userSeq: Long): ReadAllResult

    /** 17.4 소프트 삭제. 타인·삭제된 알림은 404 */
    fun delete(userSeq: Long, notificationSeq: Long)

    /** 17.5 전체 소프트 삭제 */
    fun deleteAll(userSeq: Long)

    /** 17.6 upsert(Q415 예외·Q439 양도). 409 없음 */
    fun registerDeviceToken(userSeq: Long, fcmToken: String): DeviceTokenResult

    /** 17.7 이 계정의 그 토큰만 비활성화. 없어도 성공 */
    fun deactivateDeviceToken(userSeq: Long, fcmToken: String)
}

data class NotificationItem(
    @field:Schema(description = "알림 번호", example = "805")
    val notificationSeq: Long,
    @field:Schema(
        description = "알림 유형. COMMENT=내 게시글에 댓글, COMMENT_REPLY=내 댓글에 답글, AI_MATCH=내 실종 신고와 비슷한 제보, " +
            "ADOPTION_SUBMITTED=입양 신청 도착(보호소), ADOPTION_APPROVED=입양 신청 승인, ADOPTION_REJECTED=입양 신청 거절, " +
            "ADOPTION_COMPLETED=입양 완료, SHELTER=담당 발견 신고 배정(보호소), SYSTEM=운영 공지(예약, 현재 발송 없음)",
        example = "AI_MATCH",
    )
    val notificationType: NotificationType,
    @field:Schema(description = "제목. 유형별 서버 고정 문구라 문구가 바뀌면 과거 알림도 새 문구로 보인다", example = "내 아이 관련 제보가 올라왔어요!")
    val title: String,
    @field:Schema(description = "본문. 생성 시점 스냅샷. 원 댓글이 삭제되면 \"삭제된 댓글입니다\"로 바뀐다", example = "대구광역시 중구에서 목격제보가 올라왔어요. 확인해보세요.")
    val message: String,
    @field:Schema(
        description = "탭했을 때 이동할 대상 종류. COMMENT·COMMENT_REPLY=MISSING·FOUND(댓글이 달린 게시글), AI_MATCH=FOUND·SHELTER_ANIMAL(매칭 상대), " +
            "ADOPTION_*=ADOPTION(입양 신청), SHELTER=FOUND(배정된 발견 신고). 대상이 없는 알림은 null",
        nullable = true,
        example = "FOUND",
    )
    val targetType: TargetType?,
    @field:Schema(description = "이동할 대상 번호. targetType과 짝", nullable = true, example = "210")
    val targetSeq: Long?,
    @field:Schema(description = "알림을 만든 댓글·답글 번호. COMMENT·COMMENT_REPLY만, 그 외 null", nullable = true, example = "null")
    val commentSeq: Long?,
    @field:Schema(description = "답글의 루트 댓글 번호. COMMENT_REPLY만, 그 외 null", nullable = true, example = "null")
    val parentCommentSeq: Long?,
    @field:Schema(description = "매칭의 원본 실종 신고 번호. AI_MATCH만, 그 외 null", nullable = true, example = "101")
    val missingReportSeq: Long?,
    @get:Schema(name = "isRead", description = "읽음 여부", example = "false")
    val isRead: Boolean,
    @field:Schema(description = "처음 읽은 시각. 안 읽었으면 null", nullable = true, example = "null")
    val readDttm: OffsetDateTime?,
    @field:Schema(description = "생성 시각", example = "2026-10-29T17:50:00+09:00")
    val regDttm: OffsetDateTime?,
)

data class ReadResult(
    @field:Schema(description = "알림 번호", example = "805")
    val notificationSeq: Long,
    @get:Schema(name = "isRead", description = "읽음 여부. 항상 true", example = "true")
    val isRead: Boolean,
    @field:Schema(description = "처음 읽은 시각. 이미 읽은 알림이면 처음 읽은 시각을 유지한다", example = "2026-10-29T18:00:00+09:00")
    val readDttm: OffsetDateTime?,
)

data class ReadAllResult(
    @field:Schema(description = "이번에 읽음으로 바뀐 알림 수. 이미 읽은 알림은 세지 않는다", example = "5")
    val updatedCount: Int,
)

data class DeviceTokenResult(
    @field:Schema(description = "활성 여부. 항상 true", example = "true")
    val active: Boolean,
)
