package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal

/** 이벤트 발생 장소(DB 41 trigger_context). WALK는 산책 중(252) */
enum class TriggerContext { HOME, WALK }

/** 발생 유형(DB 41 trigger_type). RANDOM은 가중치 추첨(256), CONDITION은 스탯 조건 + 확률(259) */
enum class TriggerType { RANDOM, CONDITION }

/**
 * DB 명세 41. simulation_event_scenarios. Flyway 시드로만 넣고 관리 API가 없다(Q412).
 * 시나리오마다 정답 행동 1개(resolve_action_code)와 해결·방치 효과·문구를 가진다(Q486).
 * 효과에는 성격 배율을 곱하지 않는다(Q490).
 */
@Entity
@Immutable
@Table(name = "simulation_event_scenarios")
class EventScenario(
    scenarioSeq: Long,
    title: String,
    description: String,
    triggerContext: TriggerContext,
    triggerType: TriggerType,
    resolveActionCode: ActionCode,
    speciesScope: SpeciesScope = SpeciesScope.ALL,
    personalityScope: Personality? = null,
    weight: Int = 1,
    triggerCondition: Map<String, Any>? = null,
    cooldownHours: Int? = null,
    triggerProbability: BigDecimal = BigDecimal.ONE,
    resolveCost: Int = 0,
    resolveEffects: Map<String, Int>? = null,
    resultText: String? = null,
    neglectEffects: Map<String, Int>? = null,
    neglectText: String? = null,
    isActive: Boolean = true,
) : BaseTimeEntity() {

    @Id
    var scenarioSeq: Long = scenarioSeq
        protected set

    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    /** 상황 설명. 정답을 직접 말하지 않는 힌트(Q486) */
    @Column(nullable = false, columnDefinition = "text")
    var description: String = description
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var triggerContext: TriggerContext = triggerContext
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var triggerType: TriggerType = triggerType
        protected set

    /** CONDITION 조건식. 예 {"stat":"health","op":"<","value":40}(259) */
    @JdbcTypeCode(SqlTypes.JSON)
    var triggerConditionJsonb: Map<String, Any>? = triggerCondition
        protected set

    var cooldownHours: Short? = cooldownHours?.toShort()
        protected set

    /** CONDITION 판정 1회당 발생 확률(Q481). RANDOM은 1.00 */
    @Column(nullable = false, precision = 3, scale = 2)
    var triggerProbability: BigDecimal = triggerProbability
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var speciesScope: SpeciesScope = speciesScope
        protected set

    /** null이면 모든 성격 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var personalityScope: Personality? = personalityScope
        protected set

    /** RANDOM 추첨 가중치 */
    @Column(nullable = false)
    var weight: Short = weight.toShort()
        protected set

    /** 정답 행동 1개(HOME: 돌봄 행동·VET_VISIT, WALK: GIVE_WATER·GIVE_SNACK) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var resolveActionCode: ActionCode = resolveActionCode
        protected set

    /** 해결 비용(원). VET_VISIT만 0보다 크다 */
    @Column(nullable = false)
    var resolveCost: Int = resolveCost
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    var resolveEffectsJsonb: Map<String, Int>? = resolveEffects
        protected set

    /** 해결 결과 문구(진단서 등) */
    @Column(columnDefinition = "text")
    var resultText: String? = resultText
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    var neglectEffectsJsonb: Map<String, Int>? = neglectEffects
        protected set

    /** 만료 문구. 정답을 알려 준다 */
    @Column(columnDefinition = "text")
    var neglectText: String? = neglectText
        protected set

    @Column(nullable = false)
    var isActive: Boolean = isActive
        protected set

    fun resolveEffect(): StatEffect = StatEffect.fromJson(resolveEffectsJsonb)

    fun neglectEffect(): StatEffect = StatEffect.fromJson(neglectEffectsJsonb)

    /** CONDITION 조건식. RANDOM이면 null */
    fun condition(): TriggerCondition? = triggerConditionJsonb?.let(TriggerCondition::of)

    /** CONDITION 시나리오의 조건이 지금 스탯에 맞는지. 조건식이 없으면(RANDOM) false */
    fun conditionMatches(stats: Stats): Boolean = condition()?.matches(stats) ?: false

    /** 발생 확률(0 초과 1 이하) */
    val probability: Double get() = triggerProbability.toDouble()

    /** 세션의 종·성격에 나올 수 있는 시나리오인지 */
    fun appliesTo(speciesType: SpeciesType, personality: Personality): Boolean =
        speciesScope.includes(speciesType) && (personalityScope == null || personalityScope == personality)

    /** 정답이 병원이면 진료비를 미리 보여 준다(18.12 vetVisit) */
    val isVetVisit: Boolean get() = resolveActionCode == ActionCode.VET_VISIT
}
