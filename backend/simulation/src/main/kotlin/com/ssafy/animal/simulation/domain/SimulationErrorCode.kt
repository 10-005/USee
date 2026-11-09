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
    /** 변경 계열(행동·구매·산책·포기)은 IN_PROGRESS일 때만(18.0 권한) */
    SIMULATION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 모의 입양이 아닙니다."),
    /** 판매 중단 물품 포함(18.9) */
    SIMULATION_SHOP_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "물품을 찾을 수 없습니다."),
    /** 물품 적용 종과 세션 종 불일치(Q442) */
    SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES(HttpStatus.CONFLICT, "이 반려동물에게 쓸 수 없는 물품입니다."),
    /** 행동에 필요한 기구(밥그릇·물그릇·화장실·위생 관리 키트 등)를 보유하지 않음(18.6) */
    SIMULATION_EQUIPMENT_MISSING(HttpStatus.CONFLICT, "이 행동에 필요한 기구가 없습니다."),
    /** 소모품이 0개이거나 보유하지 않은 물품을 쓰려 할 때(18.6·18.16) */
    SIMULATION_ITEM_NOT_IN_INVENTORY(HttpStatus.CONFLICT, "보유하지 않았거나 다 쓴 물품입니다."),
    /** 기구는 세션당 1개(18.9) */
    SIMULATION_EQUIPMENT_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 보유한 기구입니다."),
    /** 18.6 VET_VISIT인데 정답이 병원인 대기 이벤트가 없음(Q486). 성공·무변화 응답으로 바꾸지 않는다(Q489) */
    SIMULATION_VET_NOT_NEEDED(HttpStatus.CONFLICT, "지금은 병원에 갈 필요가 없습니다."),
    /** 18.14 산책은 개 세션만(Q488) */
    SIMULATION_WALK_NOT_FOR_SPECIES(HttpStatus.CONFLICT, "산책은 강아지만 할 수 있습니다."),
    SIMULATION_WALK_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 산책 중입니다."),
    /** 끝났거나 하트비트가 끊겨 자동 종료된 산책(18.15~18.17) */
    SIMULATION_WALK_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 산책이 아닙니다."),
    /** 없는 산책·다른 세션의 산책 */
    SIMULATION_WALK_NOT_FOUND(HttpStatus.NOT_FOUND, "산책을 찾을 수 없습니다."),
    /** 18.16 CLEAN_UP_POOP인데 이 산책의 배변 이벤트가 없음(252, 사용자 결정) */
    SIMULATION_POOP_NOT_FOUND(HttpStatus.CONFLICT, "치울 배변이 없습니다."),
}
