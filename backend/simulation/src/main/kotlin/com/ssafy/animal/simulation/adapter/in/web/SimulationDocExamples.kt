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

    const val SIMULATION_VET_NOT_NEEDED =
        """{"status":409,"code":"SIMULATION_VET_NOT_NEEDED","message":"지금은 병원에 갈 필요가 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""

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

    /** 포기로 끝난 세션의 저장 리포트(18.5 data.report·18.18 data). 종료 직후라 summary는 null */
    const val ABANDONED_REPORT_JSON =
        """{"sessionSeq":3001,"status":"ABANDONED","isFinal":true,"animalName":"초코","breed":""" + BREED_JSON +
            ""","speciesType":"DOG","personality":"FRIENDLY","startedDttm":"2026-11-01T14:00:00+09:00","endedDttm":"2026-11-03T10:20:00+09:00",""" +
            """"expectedCost":150000,"totalSpent":52800,"difference":97200,""" +
            """"spentByCategory":{"FOOD_BOWL":10000,"WATER_BOWL":10000,"LITTER_BOX":0,"TOY":0,"FURNITURE":0,"LEASH":0,"GROOMING_KIT":0,""" +
            """"FOOD":21200,"SNACK":0,"LITTER":0,"SHAMPOO":0,"MEDICINE":0,"POOP_BAG":0,"CATNIP":0,"EVENT":11600},""" +
            """"dailySpent":{"1":31200,"2":21600,"3":0,"4":0,"5":0,"6":0,"7":0},""" +
            """"finalStats":{"satiety":55,"hydration":60,"cleanliness":70,"bond":42,"calmness":52,"health":84,"energy":58},""" +
            """"actionCount":12,"eventCount":0,"eventResolvedCount":0,"walkCount":0,"summary":null}"""

    /** 세션 상세(18.1·18.2·18.4 data) */
    const val SESSION_DETAIL_JSON =
        """{"sessionSeq":3001,"userSeq":17,"animalName":"초코","breed":""" + BREED_JSON +
            ""","speciesType":"DOG","personality":"FRIENDLY","status":"IN_PROGRESS","expectedCost":150000,"balance":118000,""" +
            """"startedDttm":"2026-11-01T14:00:00+09:00","endsDttm":"2026-11-08T14:00:00+09:00","endedDttm":null,"stats":""" + STATS_JSON +
            ""","lastActionDttm":"2026-11-01T16:40:00+09:00","equipment":{"foodBowl":true,"waterBowl":true,"litterBox":false,"toy":false,"leash":false},""" +
            """"pendingEvent":null,"activeWalkSeq":null}"""
}
