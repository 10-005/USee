package com.ssafy.animal.notification.application.service

import com.ssafy.animal.notification.domain.NotificationSettingChange
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingServiceTest {

    private val settings = FakeNotificationSettingRepository()
    private val service = NotificationSettingService(settings)

    @Test
    fun `행이 없으면 기본값을 주고 저장하지 않는다`() {
        val result = service.get(17)

        assertTrue(result.simulationEnabled && result.chatEnabled && result.commentEnabled && result.aiMatchEnabled && result.adoptionEnabled)
        assertFalse(result.quietEnabled)
        assertEquals("22:00", result.quietStart)
        assertTrue(settings.rows.isEmpty())
    }

    @Test
    fun `첫 PATCH는 기본값 위에 보낸 값만 반영해 행을 만들고 이후 조회에 반영된다`() {
        val updated = service.update(17, NotificationSettingChange(commentEnabled = false))

        assertFalse(updated.commentEnabled)
        assertTrue(updated.chatEnabled)
        assertEquals(1, settings.rows.size)

        service.update(17, NotificationSettingChange(quietEnabled = true, quietStart = LocalTime.of(23, 0)))
        val read = service.get(17)
        assertFalse(read.commentEnabled)
        assertTrue(read.quietEnabled)
        assertEquals("23:00", read.quietStart)
        assertEquals("08:00", read.quietEnd)
    }
}
