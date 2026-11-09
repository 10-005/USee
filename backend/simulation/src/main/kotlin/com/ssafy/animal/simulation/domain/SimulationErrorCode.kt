package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 18장 실패 코드. simulation 모듈 자체 코드이며 core-api 코드를 빌리지 않는다(Q442) */
enum class SimulationErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    SIMULATION_ACTIVE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 모의 입양이 있습니다."),
    SIMULATION_SPECIES_NOT_SUPPORTED(HttpStatus.CONFLICT, "모의 입양을 시작할 수 없는 종입니다."),
    SIMULATION_BREED_NOT_FOUND(HttpStatus.NOT_FOUND, "품종을 찾을 수 없습니다."),
}
