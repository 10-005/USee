package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 18장 실패 코드. simulation 모듈 자체 코드이며 core-api 코드를 빌리지 않는다(Q442) */
enum class SimulationErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    /** 타인 세션도 같은 코드로 존재를 감춘다(명세 1.3·18.0 권한) */
    SIMULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "모의 입양을 찾을 수 없습니다."),
    SIMULATION_ACTIVE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 모의 입양이 있습니다."),
    SIMULATION_SPECIES_NOT_SUPPORTED(HttpStatus.CONFLICT, "모의 입양을 시작할 수 없는 종입니다."),
    SIMULATION_BREED_NOT_FOUND(HttpStatus.NOT_FOUND, "품종을 찾을 수 없습니다."),
    /** 소모품이 0개이거나 보유하지 않은 물품을 쓰려 할 때(18.6·18.16) */
    SIMULATION_ITEM_NOT_IN_INVENTORY(HttpStatus.CONFLICT, "보유하지 않았거나 다 쓴 물품입니다."),
    /** 기구는 세션당 1개(18.9) */
    SIMULATION_EQUIPMENT_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 보유한 기구입니다."),
}
