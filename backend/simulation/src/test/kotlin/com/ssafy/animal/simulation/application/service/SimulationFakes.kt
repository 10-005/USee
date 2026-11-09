package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.application.port.out.InventoryEntry
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils

/** 서비스 테스트용 메모리 저장소. 포트 계약만 흉내 낸다 */
class FakeSimulationStore :
    SimulationSessionRepository,
    SimulationActionLogRepository,
    BreedReader,
    SessionDetailReader,
    InventoryRepository,
    ShopItemRepository,
    TransactionRepository {

    val sessions = mutableListOf<SimulationSession>()
    val logs = mutableListOf<SimulationActionLog>()
    val breeds = mutableMapOf<Int, BreedInfo>()
    val breedLookups = mutableListOf<Int>()
    val activeWalks = mutableMapOf<Long, Long>()
    val reported = mutableSetOf<Long>()
    val shopItems = mutableMapOf<Long, SimulationShopItem>()
    val inventory = mutableListOf<SimulationInventoryItem>()
    val transactions = mutableListOf<SimulationTransaction>()

    override fun findActive(speciesType: SpeciesType?) = shopItems.values
        .filter { it.isActive && (speciesType == null || it.speciesScope.includes(speciesType)) }
        .sortedWith(compareBy<SimulationShopItem> { it.displayOrder }.thenBy { it.itemSeq })

    override fun findByItemSeq(itemSeq: Long) = shopItems[itemSeq]

    override fun save(transaction: SimulationTransaction) = transaction.also {
        ReflectionTestUtils.setField(it, "transactionSeq", 7000L + transactions.size + 1)
        transactions += it
    }

    /** 정렬은 기본값(최근 먼저 = PK desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPage(sessionSeq: Long, type: TransactionType?, pageable: Pageable): Page<TransactionEntry> {
        val all = transactions.filter { it.sessionSeq == sessionSeq && (type == null || it.transactionType == type) }
            .sortedByDescending { it.transactionSeq }
            .map { TransactionEntry(it, it.itemSeq?.let(shopItems::getValue)) }
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

    override fun findActiveForUpdate(userSeq: Int) =
        sessions.firstOrNull { it.userSeq == userSeq && it.status == SessionStatus.IN_PROGRESS }

    override fun findByIdForUpdate(sessionSeq: Long) = sessions.firstOrNull { it.sessionSeq == sessionSeq }

    override fun findById(sessionSeq: Long) = findByIdForUpdate(sessionSeq)

    /** 정렬은 기본값(startedDttm desc, PK desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPageByUser(userSeq: Int, status: SessionStatus?, pageable: Pageable): Page<SimulationSession> {
        val all = sessions.filter { it.userSeq == userSeq && (status == null || it.status == status) }
            .sortedWith(compareByDescending<SimulationSession> { it.startedDttm }.thenByDescending { it.sessionSeq })
        val content = all.drop(pageable.offset.toInt()).take(pageable.pageSize)
        return PageImpl(content, pageable, all.size.toLong())
    }

    override fun save(session: SimulationSession) = session.also {
        if (it.sessionSeq == null) {
            ReflectionTestUtils.setField(it, "sessionSeq", 3000L + sessions.size + 1)
            sessions += it
        }
    }

    override fun save(log: SimulationActionLog) = log.also { logs += it }

    override fun findBreed(breedSeq: Int): BreedInfo? {
        breedLookups += breedSeq
        return breeds[breedSeq]
    }

    override fun findBreeds(breedSeqs: Collection<Int>) = breedSeqs.mapNotNull { breeds[it] }.associateBy { it.breedSeq }

    override fun findActiveWalkSeq(sessionSeq: Long) = activeWalks[sessionSeq]

    override fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>) = sessionSeqs.filterTo(mutableSetOf()) { it in reported }

    override fun find(sessionSeq: Long, itemSeq: Long) = inventory.firstOrNull { it.sessionSeq == sessionSeq && it.itemSeq == itemSeq }

    override fun save(inventoryItem: SimulationInventoryItem) = inventoryItem.also {
        if (it.inventoryItemSeq == null) {
            ReflectionTestUtils.setField(it, "inventoryItemSeq", 5000L + inventory.size + 1)
            inventory += it
        }
    }

    override fun findAllWithItems(sessionSeq: Long) = inventory.filter { it.sessionSeq == sessionSeq }
        .map { InventoryEntry(it, shopItems.getValue(it.itemSeq)) }
        .sortedWith(compareBy<InventoryEntry> { it.shopItem.displayOrder }.thenBy { it.shopItem.itemSeq })

    override fun findOwnedByCategory(sessionSeq: Long, category: ItemCategory) =
        findAllWithItems(sessionSeq).filter { it.inventoryItem.quantity > 0 && it.shopItem.category == category }

    override fun findEquipment(sessionSeq: Long) = Equipment.of(
        findAllWithItems(sessionSeq).filter { it.inventoryItem.quantity > 0 && !it.shopItem.isConsumable }.mapTo(mutableSetOf()) { it.shopItem.category },
    )

    /** 시드와 같은 번호·분류의 물품 몇 개 */
    fun stockShop() {
        listOf(
            SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL, displayOrder = 1),
            SimulationShopItem(2, "물그릇", ItemCategory.WATER_BOWL, 10_000, SpeciesScope.ALL, displayOrder = 2),
            SimulationShopItem(4, "강아지 배변판", ItemCategory.LITTER_BOX, 15_000, SpeciesScope.DOG, displayOrder = 4),
            SimulationShopItem(
                10, "위생 관리 키트", ItemCategory.GROOMING_KIT, 13_800, SpeciesScope.ALL,
                effects = mapOf("cleanliness" to 40, "calmness" to -5), displayOrder = 10,
            ),
            SimulationShopItem(11, "탐색 장난감", ItemCategory.TOY, 4_100, SpeciesScope.ALL, effects = mapOf("bond" to 3), displayOrder = 11),
            SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, effects = mapOf("satiety" to 30), displayOrder = 22),
            SimulationShopItem(30, "배변패드", ItemCategory.LITTER, 2_600, SpeciesScope.DOG, effects = mapOf("cleanliness" to 30), displayOrder = 30),
            SimulationShopItem(90, "단종된 간식", ItemCategory.SNACK, 1_000, SpeciesScope.ALL, displayOrder = 90, isActive = false),
            SimulationShopItem(
                31, "화장실 모래", ItemCategory.LITTER, 7_200, SpeciesScope.CAT,
                effects = mapOf("cleanliness" to 30), imageKey = "simulation/items/litter.png", displayOrder = 31,
            ),
        ).forEach { shopItems[it.itemSeq] = it }
    }

    fun assembler() = SimulationSessionDetailAssembler(this, this, this)

    companion object {
        val MALTESE = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")
        val KOREAN_SHORTHAIR = BreedInfo(31, "코리안 숏헤어", CatalogSpeciesCode.CAT, Personality.TIMID, null)
        val ETC = BreedInfo(90, "기타", CatalogSpeciesCode.ETC, Personality.TIMID, null)
    }
}
