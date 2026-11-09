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
