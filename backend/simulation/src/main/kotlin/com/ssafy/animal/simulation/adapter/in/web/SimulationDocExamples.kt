package com.ssafy.animal.simulation.adapter.`in`.web

/**
 * Swagger 응답 예시(문서 전용, 런타임 미사용). 여러 simulation 컨트롤러가 같이 쓰는 것만 둔다.
 * 실패 예시의 code·message는 SimulationErrorCode와 같아야 한다(SimulationDocExamplesTest가 검사).
 */
internal object SimulationDocExamples {

    // ===== 공통 실패 =====
    const val SIMULATION_NOT_FOUND =
        """{"status":404,"code":"SIMULATION_NOT_FOUND","message":"모의 입양을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_NOT_IN_PROGRESS =
        """{"status":409,"code":"SIMULATION_NOT_IN_PROGRESS","message":"진행 중인 모의 입양이 아닙니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_ACTIVE_ALREADY_EXISTS =
        """{"status":409,"code":"SIMULATION_ACTIVE_ALREADY_EXISTS","message":"이미 진행 중인 모의 입양이 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_SPECIES_NOT_SUPPORTED =
        """{"status":409,"code":"SIMULATION_SPECIES_NOT_SUPPORTED","message":"모의 입양을 시작할 수 없는 종입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_BREED_NOT_FOUND =
        """{"status":404,"code":"SIMULATION_BREED_NOT_FOUND","message":"품종을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_SHOP_ITEM_NOT_FOUND =
        """{"status":404,"code":"SIMULATION_SHOP_ITEM_NOT_FOUND","message":"물품을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES =
        """{"status":409,"code":"SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES","message":"이 반려동물에게 쓸 수 없는 물품입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_EQUIPMENT_ALREADY_OWNED =
        """{"status":409,"code":"SIMULATION_EQUIPMENT_ALREADY_OWNED","message":"이미 보유한 기구입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_EQUIPMENT_MISSING =
        """{"status":409,"code":"SIMULATION_EQUIPMENT_MISSING","message":"이 행동에 필요한 기구가 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val SIMULATION_ITEM_NOT_IN_INVENTORY =
        """{"status":409,"code":"SIMULATION_ITEM_NOT_IN_INVENTORY","message":"보유하지 않았거나 다 쓴 물품입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""

    /** 경로의 세션 번호가 숫자가 아님 */
    const val SESSION_SEQ_TYPE_MISMATCH =
        """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sessionSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""

    /** regDttm만 정렬할 수 있는 목록(18.7·18.11)에 다른 필드를 보냄 */
    const val SORT_REG_DTTM_ONLY =
        """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: amount (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""

    // ===== 공통 성공 조각 =====
    const val BREED_JSON =
        """{"breedSeq":21,"breedName":"말티즈","speciesType":"DOG","representativeImageUrl":"https://storage.example.com/simulation/breeds/maltese.png"}"""
    const val STATS_JSON =
        """{"satiety":64,"hydration":58,"cleanliness":77,"bond":31,"calmness":46,"health":90,"energy":55,"calculatedDttm":"2026-11-01T17:00:00+09:00"}"""

    /** 세션 상세(18.1·18.2·18.4 data) */
    const val SESSION_DETAIL_JSON =
        """{"sessionSeq":3001,"userSeq":17,"animalName":"초코","breed":""" + BREED_JSON +
            ""","speciesType":"DOG","personality":"FRIENDLY","status":"IN_PROGRESS","expectedCost":150000,"balance":118000,""" +
            """"startedDttm":"2026-11-01T14:00:00+09:00","endsDttm":"2026-11-08T14:00:00+09:00","endedDttm":null,"stats":""" + STATS_JSON +
            ""","lastActionDttm":"2026-11-01T16:40:00+09:00","equipment":{"foodBowl":true,"waterBowl":true,"litterBox":false,"toy":false},""" +
            """"pendingEvent":null,"activeWalkSeq":null}"""
}
