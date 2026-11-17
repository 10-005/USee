package com.ssafy.animal.core.notification.application.service

import com.ssafy.animal.common.event.AiMatchFound
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.NotificationCreated
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.notification.application.port.out.AiMatchRepository
import com.ssafy.animal.core.notification.application.port.out.FoundRegion
import com.ssafy.animal.core.notification.domain.AiMatchNotification
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiMatchServiceTest {

    private val saved = mutableListOf<AiMatchNotification>()
    private val events = mutableListOf<Any>()
    private val calls = mutableListOf<String>()

    /** 자격 있는 실종 신고(seq → 작성자) */
    private val missing = mutableMapOf(101L to 17)
    private val found = mutableMapOf<Long, FoundRegion>(210L to region("대구광역시", "중구"), 211L to region(null, null))
    private val shelterAnimals = mapOf(5L to "김천시 동물보호센터")

    private fun region(sido: String?, sigungu: String?) = object : FoundRegion {
        override val sidoName = sido
        override val sigunguName = sigungu
    }

    private val repository = object : AiMatchRepository {
        override fun lockEligibleMissingAuthor(missingReportSeq: Long) = missing[missingReportSeq].also { calls += "lock" }
        override fun findEligibleFoundRegion(foundReportSeq: Long) = found[foundReportSeq]
        override fun findEligibleShelterName(animalSeq: Long) = shelterAnimals[animalSeq]
        override fun existsAiMatch(missingReportSeq: Long, targetType: TargetType, targetSeq: Long) =
            saved.any { it.missingReportSeq.toLong() == missingReportSeq && it.targetType == targetType && it.targetSeq == targetSeq }
                .also { calls += "exists" }
        override fun save(notification: AiMatchNotification) = notification.also {
            ReflectionTestUtils.setField(it, "notificationSeq", 800L + saved.size)
            saved += it
            calls += "insert"
        }
    }

    private val service = AiMatchService(repository, object : EventPublisher {
        override fun publish(event: Any) {
            events += event
        }
    })

    private fun match(missingSeq: Long = 101, type: TargetType = TargetType.FOUND, target: Long = 210) =
        service.handle(AiMatchFound(missingSeq, type, target, 0.91, "v1"))

    @Test
    fun `잠금 후 중복을 확인하고 최초 1회만 INSERT한 뒤 NotificationCreated를 발행한다`() {
        assertTrue(match())

        assertEquals(listOf("lock", "exists", "insert"), calls)
        val row = saved.single()
        assertEquals(17, row.recipientUserSeq)
        assertEquals(NotificationType.AI_MATCH, row.notificationType)
        assertEquals("대구광역시 중구에서 목격제보가 올라왔어요. 확인해보세요.", row.message)
        assertEquals(NotificationCreated(800, 17, NotificationType.AI_MATCH, row.message, TargetType.FOUND, 210), events.single())

        assertFalse(match())
        assertEquals(1, saved.size)
        assertEquals(1, events.size)
    }

    @Test
    fun `시군구가 없으면 근처 문구, 보호동물은 보호소명 문구`() {
        match(target = 211)
        match(type = TargetType.SHELTER_ANIMAL, target = 5)

        assertEquals("근처에서 목격제보가 올라왔어요. 확인해보세요.", saved[0].message)
        assertEquals("김천시 동물보호센터에서 비슷한 아이를 보호하고 있어요. 확인해보세요.", saved[1].message)
    }

    @Test
    fun `자격 미달이면 아무것도 하지 않는다`() {
        assertFalse(match(target = 999))
        assertFalse(match(type = TargetType.SHELTER_ANIMAL, target = 999))
        assertFalse(match(missingSeq = 999))
        assertFalse(match(type = TargetType.MISSING, target = 101))
        assertTrue(saved.isEmpty())
        assertTrue(events.isEmpty())
    }
}
