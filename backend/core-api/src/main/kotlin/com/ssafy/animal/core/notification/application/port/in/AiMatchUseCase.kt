package com.ssafy.animal.core.notification.application.port.`in`

import com.ssafy.animal.common.event.AiMatchFound

interface AiMatchUseCase {
    /**
     * 17.8 AI 매칭 결과 처리. 자격 재검사 → missing_reports 행 잠금 → 같은 쌍(삭제 포함) 중복 확인 → AI_MATCH INSERT →
     * 커밋 후 NotificationCreated. 자격 미달·중복이면 아무것도 하지 않고 false
     */
    fun handle(event: AiMatchFound): Boolean
}
