package com.ssafy.animal.simulation.application.port.out

import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.SpendRecord
import com.ssafy.animal.simulation.domain.TransactionType
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
}

/** 종료 리포트(DB 44). 세션당 1건 */
interface ReportRepository {
    fun save(report: SimulationReport): SimulationReport

    fun findBySessionSeq(sessionSeq: Long): SimulationReport?
}

/**
 * 세션 종료 시 산책·이벤트 정리와 리포트 카운트. 산책·이벤트 엔티티는 각 기능 작업(252·256)에서 만들므로
 * 그 전까지 테이블을 직접 다룬다.
 */
interface SessionClosingRepository {
    /** 진행 중 산책을 ENDED로 닫는다(산책 효과 없음). 닫은 수 */
    fun endActiveWalks(sessionSeq: Long, endedAt: OffsetDateTime): Int

    /** PENDING 이벤트를 방치 효과 없이 EXPIRED로 닫는다(Q486). 닫은 수 */
    fun expirePendingEvents(sessionSeq: Long): Int

    fun countEvents(sessionSeq: Long): EventCounts

    fun countWalks(sessionSeq: Long): Int
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
    /** 진행 중 산책(세션당 최대 1건, 부분 UNIQUE). 없으면 null */
    fun findActiveWalkSeq(sessionSeq: Long): Long?

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
