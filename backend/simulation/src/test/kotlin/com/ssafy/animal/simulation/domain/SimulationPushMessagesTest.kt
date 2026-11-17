package com.ssafy.animal.simulation.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimulationPushMessagesTest {

    @Test
    fun `EVENT 푸시는 이름과 시나리오 제목을 넣는다`() {
        assertEquals(
            PushMessage("초코에게 무슨 일이 생겼어요", "물그릇을 엎었어요. 들어와서 살펴봐 주세요."),
            SimulationPushMessages.event("초코", "물그릇을 엎었어요"),
        )
    }

    @Test
    fun `SESSION_END 푸시는 완료·돌봄 부족만 보내고 포기는 보내지 않는다`() {
        assertEquals(
            PushMessage("모의 입양이 끝났어요", "초코와의 7일이 끝났어요. 리포트를 확인해 보세요."),
            SimulationPushMessages.sessionEnd(SessionStatus.COMPLETED, "초코"),
        )
        assertEquals(
            PushMessage("모의 입양이 중단됐어요", "돌봄이 부족해 초코의 체험이 중단됐어요. 리포트를 확인해 보세요."),
            SimulationPushMessages.sessionEnd(SessionStatus.FAILED, "초코"),
        )
        assertNull(SimulationPushMessages.sessionEnd(SessionStatus.ABANDONED, "초코"))
        assertNull(SimulationPushMessages.sessionEnd(SessionStatus.IN_PROGRESS, "초코"))
    }

    @Test
    fun `받침에 따라 와·과를 고른다`() {
        assertEquals("콩과의 7일이 끝났어요. 리포트를 확인해 보세요.", SimulationPushMessages.sessionEnd(SessionStatus.COMPLETED, "콩")?.body)
        assertEquals("과", SimulationPushMessages.withParticle("별님", "과", "와"))
        assertEquals("와", SimulationPushMessages.withParticle("나비", "과", "와"))
        assertEquals("와", SimulationPushMessages.withParticle("Coco", "과", "와"))
    }
}
