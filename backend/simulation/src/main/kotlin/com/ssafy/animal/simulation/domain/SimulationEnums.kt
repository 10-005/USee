package com.ssafy.animal.simulation.domain

/** 세션 종 스냅샷(DB 35 species_type). ETC 품종은 시작할 수 없어 세션에는 두 값만 있다(M8) */
enum class SpeciesType { DOG, CAT }

/** 품종 성격 4종(M7). 시작 시 품종에서 세션으로 스냅샷한다(Q482) */
enum class Personality { ACTIVE, TIMID, AGGRESSIVE, FRIENDLY }

/** 세션 상태(M6). 진행 중은 USER당 1개 */
enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED, FAILED }

/** 행동 로그 분류(DB 39 action_type) */
enum class ActionType { CARE, WALK, EVENT, SYSTEM }

/** 행동 로그 코드(DB 39 action_code). 분류마다 쓸 수 있는 코드가 정해져 있다(Q486) */
enum class ActionCode(val type: ActionType) {
    FEED(ActionType.CARE),
    WATER(ActionType.CARE),
    CLEAN_LITTER(ActionType.CARE),
    BATHE(ActionType.CARE),
    PLAY(ActionType.CARE),
    SNACK(ActionType.CARE),
    MEDICINE(ActionType.CARE),
    VET_VISIT(ActionType.CARE),

    /** 캣닢 주기(S15P31D106-246, 명세 18.6 행동 추가). action_code 컬럼은 CHECK가 없어 스키마 변경 없음 */
    GIVE_CATNIP(ActionType.CARE),
    WALK_START(ActionType.WALK),
    WALK_END(ActionType.WALK),
    GIVE_WATER(ActionType.WALK),
    GIVE_SNACK(ActionType.WALK),

    /** 산책 중 배변 치우기(S15P31D106-252, 사용자 결정). 배변봉투 1개를 쓰고 산책 배변 이벤트만 해결한다 */
    CLEAN_UP_POOP(ActionType.WALK),
    EVENT_EXPIRE(ActionType.EVENT),
    SESSION_START(ActionType.SYSTEM),
    SESSION_END(ActionType.SYSTEM),
}
