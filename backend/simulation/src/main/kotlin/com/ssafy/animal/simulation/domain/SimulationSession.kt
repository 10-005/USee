package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * DB 명세 35. simulation_sessions. user_seq·breed_seq는 core 스키마를 가리키는 FK 없는 논리 참조(Q420·Q482).
 * 종·성격은 시작 시 품종에서 복사한 스냅샷이라 이후 품종 값이 바뀌어도 그대로다.
 */
@Entity
@Table(name = "simulation_sessions")
class SimulationSession(
    userSeq: Int,
    breedSeq: Int,
    animalName: String,
    speciesType: SpeciesType,
    personality: Personality,
    expectedCost: Int,
    startedDttm: OffsetDateTime,
    endsDttm: OffsetDateTime,
    stats: Stats,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var sessionSeq: Long? = null
        protected set

    @Column(nullable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false)
    var breedSeq: Int = breedSeq
        protected set

    @Column(nullable = false, length = 6)
    var animalName: String = animalName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var speciesType: SpeciesType = speciesType
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var personality: Personality = personality
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SessionStatus = SessionStatus.IN_PROGRESS
        protected set

    @Column(nullable = false)
    var expectedCost: Int = expectedCost
        protected set

    /** 음수 허용(M5). 시작값 = expectedCost */
    @Column(nullable = false)
    var balance: Int = expectedCost
        protected set

    @Column(nullable = false)
    var startedDttm: OffsetDateTime = startedDttm
        protected set

    @Column(nullable = false)
    var endsDttm: OffsetDateTime = endsDttm
        protected set

    var endedDttm: OffsetDateTime? = null
        protected set

    // 스탯 컬럼은 SMALLINT(DB 명세 35). 밖에서는 stats()로 묶어서 쓴다
    @Column(nullable = false)
    protected var satiety: Short = stats.satiety.toShort()

    @Column(nullable = false)
    protected var hydration: Short = stats.hydration.toShort()

    @Column(nullable = false)
    protected var cleanliness: Short = stats.cleanliness.toShort()

    @Column(nullable = false)
    protected var bond: Short = stats.bond.toShort()

    @Column(nullable = false)
    protected var calmness: Short = stats.calmness.toShort()

    @Column(nullable = false)
    protected var health: Short = stats.health.toShort()

    @Column(nullable = false)
    protected var energy: Short = stats.energy.toShort()

    /** 스탯 7종의 마지막 지연 계산 기준 시각(M2). 시작 시 = startedDttm */
    @Column(nullable = false)
    var statsCalculatedDttm: OffsetDateTime = startedDttm
        protected set

    /** 마지막 CARE·WALK 행동 시각. SYSTEM 로그는 갱신하지 않는다 */
    var lastActionDttm: OffsetDateTime? = null
        protected set

    /** 잔액에서 amount를 뺀다. 잔액은 음수가 되어도 허용한다(M5). 차감 후 잔액을 돌려준다 */
    fun spend(amount: Int): Int {
        check(status == SessionStatus.IN_PROGRESS) { "진행 중인 세션만 돈을 씁니다: $status" }
        require(amount >= 0) { "지출액은 0 이상이어야 합니다: $amount" }
        balance = Math.subtractExact(balance, amount)
        return balance
    }

    /** 지연 계산 결과를 반영한다(M2). calculatedAt은 계산기가 돌려준 tick 경계 시각 */
    fun applyStats(stats: Stats, calculatedAt: OffsetDateTime) {
        check(status == SessionStatus.IN_PROGRESS) { "진행 중인 세션만 스탯을 갱신합니다: $status" }
        require(!calculatedAt.isBefore(statsCalculatedDttm)) { "계산 시각은 뒤로 갈 수 없습니다." }
        writeStats(stats)
        statsCalculatedDttm = calculatedAt
    }

    /**
     * 행동·이벤트 효과를 지금 스탯에 더한다(0~100 클램프). 지연 계산 기준 시각은 그대로 둔다.
     * 부르는 쪽이 먼저 지연 계산을 끝낸 뒤 쓴다(명세 21.5의 3).
     */
    fun applyEffect(effect: StatEffect): EffectResult {
        check(status == SessionStatus.IN_PROGRESS) { "진행 중인 세션만 스탯을 갱신합니다: $status" }
        val result = stats().apply(effect)
        writeStats(result.after)
        return result
    }

    /** CARE·WALK 행동 시각을 남긴다(표시·통계용, Q481). SYSTEM·EVENT_EXPIRE 로그는 부르지 않는다 */
    fun recordAction(at: OffsetDateTime) {
        check(status == SessionStatus.IN_PROGRESS) { "진행 중인 세션만 행동합니다: $status" }
        lastActionDttm = at
    }

    private fun writeStats(stats: Stats) {
        satiety = stats.satiety.toShort()
        hydration = stats.hydration.toShort()
        cleanliness = stats.cleanliness.toShort()
        bond = stats.bond.toShort()
        calmness = stats.calmness.toShort()
        health = stats.health.toShort()
        energy = stats.energy.toShort()
    }

    fun stats() = Stats(
        satiety = satiety.toInt(),
        hydration = hydration.toInt(),
        cleanliness = cleanliness.toInt(),
        bond = bond.toInt(),
        calmness = calmness.toInt(),
        health = health.toInt(),
        energy = energy.toInt(),
    )

    companion object {
        const val ANIMAL_NAME_MAX_LENGTH = 6

        /** 18.1 시작. 잔액 = 예상 비용, 예정 종료 = 시작 + 세션 길이, 스탯 계산 시각 = 시작 */
        fun start(
            userSeq: Int,
            breedSeq: Int,
            animalName: String,
            speciesType: SpeciesType,
            personality: Personality,
            expectedCost: Int,
            startedAt: OffsetDateTime,
            endsAt: OffsetDateTime,
            initialStats: Stats,
        ): SimulationSession {
            require(animalName.length in 1..ANIMAL_NAME_MAX_LENGTH) { "animalName은 1~${ANIMAL_NAME_MAX_LENGTH}자여야 합니다." }
            require(expectedCost >= 0) { "expectedCost는 0 이상이어야 합니다." }
            require(endsAt.isAfter(startedAt)) { "예정 종료는 시작보다 뒤여야 합니다." }
            return SimulationSession(userSeq, breedSeq, animalName, speciesType, personality, expectedCost, startedAt, endsAt, initialStats)
        }
    }
}
