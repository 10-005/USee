package com.ssafy.animal.core.match.application.port.out

/** 일괄 재발행 실행 이름 표식(Q524). 여러 Pod·재시작 중 한 곳에서 한 번만 보내게 한다 */
fun interface MatchRepublishMarker {
    /** 처음 넣었으면 true, 이미 있으면 false. 저장소에 닿지 못하면 예외 */
    fun tryMark(runName: String): Boolean
}
