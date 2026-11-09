package com.ssafy.animal.simulation.application.port.`in`

/** 세션 상태 관리(232). 스케줄러·회원 탈퇴 이벤트가 부른다 */
interface SessionMaintenanceUseCase {
    /** 진행 중 세션 번호 전체(스케줄러가 한 건씩 advance를 부른다) */
    fun inProgressSessionSeqs(): List<Long>

    /** 세션 한 건을 지금 시각까지 진행시킨다(스탯 계산·이벤트 만료, 건강 0·예정 종료면 종료, 진행 중이면 CONDITION → RANDOM 이벤트 판정). 세션마다 별도 트랜잭션 */
    fun advance(sessionSeq: Long)

    /** 회원 탈퇴(UserDeleted): 진행 중 세션이 있으면 ABANDONED로 끝내고 리포트를 만든다. 없으면 아무것도 안 함(재전달에도 안전) */
    fun abandonForWithdrawal(userSeq: Long)
}
