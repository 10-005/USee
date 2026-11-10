package com.ssafy.animal.simulation.application.port.out

import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.SpendRecord
import com.ssafy.animal.simulation.domain.TransactionType
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

interface SimulationSessionRepository {
    /** 요청자의 진행 중 세션을 행 잠금(FOR UPDATE)으로 조회한다. 없으면 null */
    fun findActiveForUpdate(userSeq: Int): SimulationSession?

    /** 세션을 행 잠금(FOR UPDATE)으로 조회한다. 진행 중이면 지연 계산 결과를 쓰기 때문. 없으면 null */
    fun findByIdForUpdate(sessionSeq: Long): SimulationSession?

    /** 잠그지 않는 조회(스탯을 쓰지 않는 읽기 전용 API용). 없으면 null */
    fun findById(sessionSeq: Long): SimulationSession?

    /** 스케줄러가 돌 진행 중 세션 번호 전체 */
    fun findInProgressSessionSeqs(): List<Long>

    /** 18.3 사용자 세션 목록(종료 포함). status가 null이면 전체. 정렬은 startedDttm(기본)·endedDttm만 허용 */
    fun findPageByUser(userSeq: Int, status: SessionStatus?, pageable: Pageable): Page<SimulationSession>

    /**
     * 세션을 저장한다. 사전 조회를 지나 부분 UNIQUE(USER당 진행 중 1개)에 걸리면(경쟁 상태)
     * 사전 조회와 같은 409 SIMULATION_ACTIVE_ALREADY_EXISTS로 바꿔 던진다.
     */
    fun save(session: SimulationSession): SimulationSession
}

interface SimulationActionLogRepository {
    fun save(log: SimulationActionLog): SimulationActionLog

    /** 18.7 세션 행동 이력. type이 null이면 전체. 정렬은 regDttm만 허용(기본 desc, 동률 PK desc) */
    fun findPage(sessionSeq: Long, type: ActionType?, pageable: Pageable): Page<ActionLogEntry>

    /** 그 세션에서 그 행동을 마지막으로 한 시각(캣닢 남용 판정 등). 없으면 null */
    fun findLastActionDttm(sessionSeq: Long, actionCode: ActionCode): OffsetDateTime?

    /** 리포트 actionCount용. 그 분류들의 로그 수 */
    fun countByTypes(sessionSeq: Long, types: Collection<ActionType>): Int

    /** 리포트 일차별 돌봄 집계용(Q517). 그 분류들의 로그 전체, 오래된 순 */
    fun findByTypes(sessionSeq: Long, types: Collection<ActionType>): List<SimulationActionLog>
}

/** 종료 리포트(DB 44). 세션당 1건 */
interface ReportRepository {
    fun save(report: SimulationReport): SimulationReport

    fun findBySessionSeq(sessionSeq: Long): SimulationReport?

    /**
     * 18.19·18.20 종료 리포트 목록(리포트가 있는 = 끝난 세션만). userSeq가 null이면 전체.
     * 정렬은 endedDttm만 허용(기본 desc, 동률 세션 번호 desc)
     */
    fun findPage(userSeq: Int?, pageable: Pageable): Page<ReportListEntry>

    /** summary가 비어 있을 때만 채운다(UPDATE ... WHERE summary IS NULL, 자체 트랜잭션). 이번에 채웠으면 true */
    fun updateSummaryIfNull(sessionSeq: Long, summary: String): Boolean

    /** 요약이 아직 없고 리포트를 만든 지 createdBefore보다 오래된 세션 번호(고아 복구, Q487). 오래된 순 */
    fun findSessionSeqsWithoutSummary(createdBefore: OffsetDateTime): List<Long>
}

/**
 * 리포트 요약 문구 생성기. 규칙 문구 구현과 LLM(OpenAI) 구현이 있고, LLM 키가 있으면 LLM을 쓴다(Q444·Q517).
 * 실패하면 예외를 던진다(부르는 쪽이 1회 재시도 후 규칙 문구로 대체).
 */
interface ReportSummaryGenerator {
    fun generate(input: ReportSummaryInput): String
}

/** 리포트 목록 한 줄: 세션 + 저장된 리포트 */
data class ReportListEntry(
    val session: SimulationSession,
    val report: SimulationReport,
)

/** core.users 읽기 전용 조회(18.20 신청자 표시, Q420). 잠그지 않는다 */
interface UserReader {
    /** 없는 회원은 결과 맵에서 빠진다(탈퇴 회원은 isDeleted=true로 들어 있다) */
    fun findUsers(userSeqs: Collection<Int>): Map<Int, UserInfo>
}

/** 표시용 회원 정보. 프로필 사진 URL은 변환된 값(없으면 null) */
data class UserInfo(
    val userSeq: Int,
    val nickname: String?,
    val role: String,
    val isDeleted: Boolean,
    val profileImageSeq: Long?,
    val profileImageUrl: String?,
)

/** 산책(DB 40). 부르는 쪽이 세션 행을 잠근 상태에서 쓴다 */
interface WalkRepository {
    fun save(walk: SimulationWalk): SimulationWalk

    /** 세션의 진행 중 산책(부분 UNIQUE라 최대 1건). 없으면 null */
    fun findActive(sessionSeq: Long): SimulationWalk?

    fun findByWalkSeq(walkSeq: Long): SimulationWalk?

    /** 리포트 walkCount(진행 중 포함 전체) */
    fun countWalks(sessionSeq: Long): Int
}

/** 돌발 이벤트 시나리오 마스터(DB 41). 시드로만 넣고 읽기만 한다 */
interface EventScenarioRepository {
    fun findByScenarioSeq(scenarioSeq: Long): EventScenario?

    fun findByScenarioSeqs(scenarioSeqs: Collection<Long>): Map<Long, EventScenario>

    /** 판매 중(is_active)인 그 장소·유형의 시나리오. 종·성격 거르기는 부르는 쪽에서 */
    fun findActive(context: TriggerContext, type: TriggerType): List<EventScenario>
}

/** 발생 이벤트(DB 43). 부르는 쪽이 세션 행을 잠근 상태에서 쓴다 */
interface EventOccurrenceRepository {
    fun save(occurrence: EventOccurrence): EventOccurrence

    /** 세션의 PENDING 이벤트(부분 UNIQUE라 최대 1건). 없으면 null */
    fun findPending(sessionSeq: Long): EventOccurrence?

    /** 그 산책에서 발생한 이벤트 수(산책당 최대 횟수) */
    fun countByWalk(walkSeq: Long): Int

    /** since 이후 발생한 그 장소·유형 이벤트 수(RANDOM 일차당 횟수 제한) */
    fun countOccurredSince(sessionSeq: Long, context: TriggerContext, type: TriggerType, since: OffsetDateTime): Int

    /** 그 세션에서 그 시나리오가 마지막으로 발생한 시각(CONDITION 쿨다운). 없으면 null */
    fun findLastOccurredAt(sessionSeq: Long, scenarioSeq: Long): OffsetDateTime?

    /** 18.12 세션 이벤트 목록. status가 null이면 전체. 정렬은 occurredDttm만 허용(기본 desc, 동률 PK desc) */
    fun findPage(sessionSeq: Long, status: OccurrenceStatus?, pageable: Pageable): Page<EventOccurrence>

    /** 리포트 eventCount(해결 + 만료)·eventResolvedCount */
    fun countEvents(sessionSeq: Long): EventCounts

    /** 리포트 일차별 돌봄 집계용(Q517). 세션의 발생 이벤트 전체(대기 중 포함), 발생 순 */
    fun findBySession(sessionSeq: Long): List<EventOccurrence>
}

/** 발생 이벤트 수(해결 + 만료)와 해결 수 */
data class EventCounts(
    val total: Int,
    val resolved: Int,
)

/** 행동 로그 + 쓴 물품(물품을 쓰지 않은 행동이면 null) */
data class ActionLogEntry(
    val log: SimulationActionLog,
    val item: SimulationShopItem?,
)

/** 저장소 키 → 공개 URL(품종 대표 이미지·물품 사진) */
interface ImageUrlResolver {
    fun publicUrl(key: String): String
}

/** 펫샵 물품 마스터 조회(18.8) */
interface ShopItemRepository {
    /** 판매 중(is_active)인 물품. speciesType이 있으면 그 종과 ALL 물품만. display_order, item_seq 순 */
    fun findActive(speciesType: SpeciesType?): List<SimulationShopItem>

    /** 판매 중단 물품도 포함해 단건 조회. 없으면 null */
    fun findByItemSeq(itemSeq: Long): SimulationShopItem?
}

/** 지출 내역(DB 38). 한 번 쓰고 바꾸지 않는다 */
interface TransactionRepository {
    fun save(transaction: SimulationTransaction): SimulationTransaction

    /** 18.11 세션 지출 내역. type이 null이면 전체. 정렬은 regDttm만 허용(기본 desc, 동률 PK desc) */
    fun findPage(sessionSeq: Long, type: TransactionType?, pageable: Pageable): Page<TransactionEntry>

    /** 리포트 집계용 세션 지출 전체(분류 키는 물품 분류, 진료비는 EVENT) */
    fun findSpends(sessionSeq: Long): List<SpendRecord>
}

/** 지출 + 구매 물품(EVENT 지출이면 null) */
data class TransactionEntry(
    val transaction: SimulationTransaction,
    val item: SimulationShopItem?,
)

/** 세션별 보유 물품. 호출하는 쪽이 세션 행을 잠근 상태(FOR UPDATE)에서 쓴다 */
interface InventoryRepository {
    fun find(sessionSeq: Long, itemSeq: Long): SimulationInventoryItem?

    fun save(inventoryItem: SimulationInventoryItem): SimulationInventoryItem

    /** 18.10 보유 물품 전체(수량 0 포함)와 물품 정보. 물품 display_order, item_seq 순 */
    fun findAllWithItems(sessionSeq: Long): List<InventoryEntry>

    /** 세션 상세의 보유 기구(수량 1 이상인 기구 분류) */
    fun findEquipment(sessionSeq: Long): Equipment

    /** 그 분류의 물품 중 수량 1 이상 보유한 것(돌봄 행동의 필요 기구 확인·기구 효과). 물품 display_order 순 */
    fun findOwnedByCategory(sessionSeq: Long, category: ItemCategory): List<InventoryEntry>
}

data class InventoryEntry(
    val inventoryItem: SimulationInventoryItem,
    val shopItem: SimulationShopItem,
)

/** core 스키마 품종 기준정보 읽기 전용 조회(Q420·Q482). 잠그지 않는다 */
interface BreedReader {
    fun findBreed(breedSeq: Int): BreedInfo?

    /** 목록용 일괄 조회. 없는 품종은 결과 맵에서 빠진다 */
    fun findBreeds(breedSeqs: Collection<Int>): Map<Int, BreedInfo>
}

/** 세션 상세·목록에 붙는 파생 정보. 산책·리포트 엔티티가 생기기 전까지 읽기 전용 조회로 둔다 */
interface SessionDetailReader {
    /** 종료 리포트가 있는 세션 번호(18.3 hasReport) */
    fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>): Set<Long>
}

/** core.animal_species.species_code. ETC는 모의 입양을 시작할 수 없다(M8) */
enum class CatalogSpeciesCode {
    DOG, CAT, ETC;

    fun toSpeciesType(): SpeciesType? = when (this) {
        DOG -> SpeciesType.DOG
        CAT -> SpeciesType.CAT
        ETC -> null
    }
}

/** 품종 + 종 + 대표 이미지(URL로 변환된 값, 없으면 null) */
data class BreedInfo(
    val breedSeq: Int,
    val breedName: String,
    val speciesCode: CatalogSpeciesCode,
    val personality: Personality,
    val representativeImageUrl: String?,
)
