package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * DB 명세 39. simulation_action_logs. 한 번 쓰고 바꾸지 않는 이력이다.
 * 같은 스키마 FK(session·item·walk·occurrence)는 숫자 컬럼으로 둔다(core-api 기준정보 엔티티와 같은 방식).
 */
@Entity
@Table(name = "simulation_action_logs")
class SimulationActionLog(
    sessionSeq: Long,
    actionCode: ActionCode,
    statsAfter: Stats,
    statDelta: Map<String, Int>? = null,
    itemSeq: Long? = null,
    walkSeq: Long? = null,
    occurrenceSeq: Long? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var actionLogSeq: Long? = null
        protected set

    @Column(nullable = false)
    var sessionSeq: Long = sessionSeq
        protected set

    /** action_code가 정하는 분류. 따로 받지 않는다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var actionType: ActionType = actionCode.type
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var actionCode: ActionCode = actionCode
        protected set

    var itemSeq: Long? = itemSeq
        protected set

    var walkSeq: Long? = walkSeq
        protected set

    var occurrenceSeq: Long? = occurrenceSeq
        protected set

    /** 이 행동으로 바뀐 스탯(키는 스탯 컬럼명) */
    @JdbcTypeCode(SqlTypes.JSON)
    var statDeltaJsonb: Map<String, Int>? = statDelta
        protected set

    /** 행동 직후 스탯 7종 스냅샷 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var statsAfterJsonb: Map<String, Int> = statsAfter.toMap()
        protected set

    companion object {
        /** 18.6 돌봄 행동 로그. 실제로 바뀐 스탯과 행동 직후 스탯을 남긴다(변화가 없으면 statDelta는 빈 객체) */
        fun care(session: SimulationSession, actionCode: ActionCode, itemSeq: Long?, result: EffectResult): SimulationActionLog {
            require(actionCode.type == ActionType.CARE) { "CARE 코드가 아닙니다: $actionCode" }
            return SimulationActionLog(
                sessionSeq = checkNotNull(session.sessionSeq) { "저장 전 세션입니다." },
                actionCode = actionCode,
                statsAfter = result.after,
                statDelta = result.deltaJson(),
                itemSeq = itemSeq,
            )
        }

        /** SYSTEM 로그(SESSION_START·SESSION_END). 스탯 변화 없이 그 시점 스탯만 남긴다 */
        fun system(session: SimulationSession, actionCode: ActionCode): SimulationActionLog {
            require(actionCode.type == ActionType.SYSTEM) { "SYSTEM 코드가 아닙니다: $actionCode" }
            return SimulationActionLog(checkNotNull(session.sessionSeq) { "저장 전 세션입니다." }, actionCode, session.stats())
        }
    }
}
