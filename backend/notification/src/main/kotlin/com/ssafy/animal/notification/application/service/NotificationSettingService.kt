package com.ssafy.animal.notification.application.service

import com.ssafy.animal.notification.application.port.`in`.NotificationSettingResult
import com.ssafy.animal.notification.application.port.`in`.NotificationSettingUseCase
import com.ssafy.animal.notification.application.port.out.NotificationSettingRepository
import com.ssafy.animal.notification.domain.NotificationSetting
import com.ssafy.animal.notification.domain.NotificationSettingChange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSettingService(
    private val settingRepository: NotificationSettingRepository,
) : NotificationSettingUseCase {

    /** 행이 없으면 저장하지 않고 기본값을 돌려준다(Q511) */
    @Transactional(readOnly = true)
    override fun get(userSeq: Long): NotificationSettingResult {
        val user = Math.toIntExact(userSeq)
        return NotificationSettingResult.from(settingRepository.find(user) ?: NotificationSetting(user))
    }

    /** 행을 잠가 바꾸므로 토글 여러 개가 동시에 와도 서로 덮어쓰지 않는다 */
    @Transactional
    override fun update(userSeq: Long, change: NotificationSettingChange): NotificationSettingResult {
        val setting = settingRepository.getOrCreateForUpdate(Math.toIntExact(userSeq))
        setting.change(change)
        return NotificationSettingResult.from(setting)
    }
}
