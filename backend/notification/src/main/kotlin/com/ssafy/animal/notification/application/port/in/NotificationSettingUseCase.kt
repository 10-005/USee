package com.ssafy.animal.notification.application.port.`in`

import com.ssafy.animal.notification.domain.NotificationSetting
import com.ssafy.animal.notification.domain.NotificationSettingChange
import io.swagger.v3.oas.annotations.media.Schema
import java.time.format.DateTimeFormatter

/** 17.9~17.10 알림 설정(USER만). 행이 없으면 기본값을 돌려주고, PATCH는 행이 없으면 만들고 보낸 값만 바꾼다 */
interface NotificationSettingUseCase {
    fun get(userSeq: Long): NotificationSettingResult

    fun update(userSeq: Long, change: NotificationSettingChange): NotificationSettingResult
}

data class NotificationSettingResult(
    @field:Schema(description = "모의 입양 알림(돌발 이벤트·세션 종료)", example = "true")
    val simulationEnabled: Boolean,
    @field:Schema(description = "채팅 알림", example = "true")
    val chatEnabled: Boolean,
    @field:Schema(description = "댓글 알림(COMMENT·COMMENT_REPLY)", example = "true")
    val commentEnabled: Boolean,
    @field:Schema(description = "AI 매칭 알림(AI_MATCH)", example = "true")
    val aiMatchEnabled: Boolean,
    @field:Schema(description = "입양 관련 알림(ADOPTION_SUBMITTED·APPROVED·REJECTED·COMPLETED)", example = "true")
    val adoptionEnabled: Boolean,
    @field:Schema(description = "방해금지 사용 여부. 모의 입양 알림에만 적용한다", example = "false")
    val quietEnabled: Boolean,
    @field:Schema(description = "방해금지 시작 시각(서울, HH:mm)", type = "string", example = "22:00")
    val quietStart: String,
    @field:Schema(description = "방해금지 끝 시각(서울, HH:mm, 이 시각부터 다시 보낸다). 시작보다 이르면 자정을 넘기는 구간", type = "string", example = "08:00")
    val quietEnd: String,
) {
    companion object {
        // Jackson 기본 직렬화는 초까지(22:00:00) 붙여 HH:mm으로 고정한다
        private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun from(s: NotificationSetting) = NotificationSettingResult(
            s.simulationEnabled, s.chatEnabled, s.commentEnabled, s.aiMatchEnabled, s.adoptionEnabled,
            s.quietEnabled, HH_MM.format(s.quietStart), HH_MM.format(s.quietEnd),
        )
    }
}
