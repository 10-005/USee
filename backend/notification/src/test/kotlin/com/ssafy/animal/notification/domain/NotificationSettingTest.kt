package com.ssafy.animal.notification.domain

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingTest {

    private fun seoul(hour: Int, minute: Int = 0) = OffsetDateTime.of(2026, 11, 10, hour, minute, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `기본값은 전부 켜짐, 방해금지 꺼짐 22시~8시`() {
        val s = NotificationSetting(17)

        assertTrue(NotificationType.entries.all(s::allows))
        assertTrue(s.chatEnabled)
        assertFalse(s.quietEnabled)
        assertEquals(LocalTime.of(22, 0), s.quietStart)
        assertEquals(LocalTime.of(8, 0), s.quietEnd)
        assertTrue(s.allowsSimulation(seoul(23)))
    }

    @Test
    fun `보낸 값만 바꾸고 나머지는 그대로 둔다`() {
        val s = NotificationSetting(17)
        s.change(NotificationSettingChange(chatEnabled = false))
        s.change(NotificationSettingChange(quietEnabled = true, quietEnd = LocalTime.of(7, 30)))

        assertFalse(s.chatEnabled)
        assertTrue(s.commentEnabled)
        assertTrue(s.quietEnabled)
        assertEquals(LocalTime.of(22, 0), s.quietStart)
        assertEquals(LocalTime.of(7, 30), s.quietEnd)
    }

    @Test
    fun `유형별 매핑 - SHELTER·SYSTEM은 모두 꺼도 허용`() {
        val s = NotificationSetting(17).apply { change(NotificationSettingChange(false, false, false, false, false)) }

        assertEquals(
            setOf(NotificationType.SHELTER, NotificationType.SYSTEM),
            NotificationType.entries.filter(s::allows).toSet(),
        )
        val adoptionOnly = NotificationSetting(18).apply { change(NotificationSettingChange(adoptionEnabled = false)) }
        assertEquals(
            setOf(
                NotificationType.ADOPTION_SUBMITTED, NotificationType.ADOPTION_APPROVED,
                NotificationType.ADOPTION_REJECTED, NotificationType.ADOPTION_COMPLETED,
            ),
            NotificationType.entries.filterNot(adoptionOnly::allows).toSet(),
        )
    }

    @Test
    fun `자정을 넘기는 구간은 시작 포함 끝 제외`() {
        val s = NotificationSetting(17).apply { change(NotificationSettingChange(quietEnabled = true)) }

        assertTrue(s.isQuiet(seoul(22, 0)))
        assertTrue(s.isQuiet(seoul(3)))
        assertTrue(s.isQuiet(seoul(7, 59)))
        assertFalse(s.isQuiet(seoul(8, 0)))
        assertFalse(s.isQuiet(seoul(21, 59)))
        assertFalse(s.allowsSimulation(seoul(23)))
        // UTC로 들어와도 서울 시각으로 판정한다(UTC 14:00 = 서울 23:00)
        assertTrue(s.isQuiet(OffsetDateTime.of(2026, 11, 10, 14, 0, 0, 0, ZoneOffset.UTC)))
    }

    @Test
    fun `같은 날 안의 구간`() {
        val s = NotificationSetting(17).apply {
            change(NotificationSettingChange(quietEnabled = true, quietStart = LocalTime.of(13, 0), quietEnd = LocalTime.of(18, 0)))
        }

        assertTrue(s.isQuiet(seoul(13)))
        assertFalse(s.isQuiet(seoul(18)))
        assertFalse(s.isQuiet(seoul(23)))
    }

    @Test
    fun `바꾼 결과 시작과 끝이 같으면 400이고 아무것도 바꾸지 않는다`() {
        val s = NotificationSetting(17)

        val e = assertFailsWith<BusinessException> {
            s.change(NotificationSettingChange(chatEnabled = false, quietEnd = LocalTime.of(22, 0)))
        }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, e.errorCode)
        assertEquals("quietEnd", e.errors.single().field)
        assertTrue(s.chatEnabled)
        assertEquals(LocalTime.of(8, 0), s.quietEnd)
    }

    @Test
    fun `초 단위 시각은 400`() {
        val e = assertFailsWith<BusinessException> {
            NotificationSetting(17).change(NotificationSettingChange(quietStart = LocalTime.of(23, 0, 30)))
        }
        assertEquals("quietStart", e.errors.single().field)
    }
}
