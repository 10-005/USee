package com.ssafy.animal.simulation.domain

/** 모의 입양 푸시 제목·본문(Q481·Q500). 저장하지 않고 FCM으로만 보낸다 */
data class PushMessage(
    val title: String,
    val body: String,
)

/**
 * 푸시 문구(S15P31D106-260, 사용자 결정 2026-11-09). 앱을 모의 입양 화면으로 이끄는 것까지가 목적이다.
 * EVENT는 시나리오 제목을 힌트로 넣고 정답 행동은 말하지 않는다. FAILED도 죽음 표현을 쓰지 않는다(M6).
 * 포기(ABANDONED)는 사용자가 직접 끝내 리포트를 바로 보므로 SESSION_END 푸시를 보내지 않는다(Q500).
 */
object SimulationPushMessages {

    /** 집 돌발 이벤트 발생. 예: "초코에게 무슨 일이 생겼어요" / "물그릇을 엎었어요. 들어와서 살펴봐 주세요." */
    fun event(animalName: String, scenarioTitle: String) = PushMessage(
        title = "${animalName}에게 무슨 일이 생겼어요",
        body = "$scenarioTitle. 들어와서 살펴봐 주세요.",
    )

    /** 세션 종료(요약 저장 후). 보내지 않는 종료(포기·진행 중)면 null */
    fun sessionEnd(status: SessionStatus, animalName: String): PushMessage? = when (status) {
        SessionStatus.COMPLETED -> PushMessage(
            title = "모의 입양이 끝났어요",
            body = "${animalName}${withParticle(animalName, "과", "와")}의 7일이 끝났어요. 리포트를 확인해 보세요.",
        )
        SessionStatus.FAILED -> PushMessage(
            title = "모의 입양이 중단됐어요",
            body = "돌봄이 부족해 ${animalName}의 체험이 중단됐어요. 리포트를 확인해 보세요.",
        )
        SessionStatus.ABANDONED, SessionStatus.IN_PROGRESS -> null
    }

    /** 마지막 글자에 받침이 있으면 withFinal, 없으면 withoutFinal. 한글이 아니면 받침 없는 쪽 */
    fun withParticle(word: String, withFinal: String, withoutFinal: String): String {
        val last = word.lastOrNull() ?: return withoutFinal
        if (last !in '가'..'힣') return withoutFinal
        return if ((last - '가') % 28 != 0) withFinal else withoutFinal
    }
}
