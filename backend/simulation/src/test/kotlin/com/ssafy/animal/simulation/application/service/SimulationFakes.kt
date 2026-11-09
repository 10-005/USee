package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.application.port.out.EventCounts
import com.ssafy.animal.simulation.application.port.out.ReportListEntry
import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.application.port.out.UserReader
import com.ssafy.animal.simulation.application.port.out.SessionClosingRepository
import com.ssafy.animal.simulation.application.port.out.InventoryEntry
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SpendRecord
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
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId

/** 서비스 테스트용 메모리 저장소. 포트 계약만 흉내 낸다 */
class FakeSimulationStore :
    SimulationSessionRepository,
    SimulationActionLogRepository,
    BreedReader,
    SessionDetailReader,
    InventoryRepository,
    ShopItemRepository,
    TransactionRepository,
    ReportRepository,
    UserReader,
    SessionClosingRepository {

    val reports = mutableListOf<SimulationReport>()
    val pendingEvents = mutableSetOf<Long>()
    val eventCounts = mutableMapOf<Long, EventCounts>()
    val walkCounts = mutableMapOf<Long, Int>()

    override fun save(report: SimulationReport) = report.also {
        ReflectionTestUtils.setField(it, "reportSeq", 9000L + reports.size + 1)
        now?.let { at -> ReflectionTestUtils.setField(it, "regDttm", at) }
        reports += it
    }

    override fun updateSummaryIfNull(sessionSeq: Long, summary: String): Boolean {
        val report = findBySessionSeq(sessionSeq)?.takeIf { it.summary == null } ?: return false
        ReflectionTestUtils.setField(report, "summary", summary)
        return true
    }

    override fun findSessionSeqsWithoutSummary(createdBefore: OffsetDateTime) = reports
        .filter { it.summary == null && it.regDttm?.isAfter(createdBefore) == false }
        .sortedBy { it.reportSeq }
        .map { it.sessionSeq }

    override fun findBySessionSeq(sessionSeq: Long) = reports.firstOrNull { it.sessionSeq == sessionSeq }

    /** 정렬은 기본값(endedDttm desc, 세션 번호 desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPage(userSeq: Int?, pageable: Pageable): Page<ReportListEntry> {
        val all = reports.map { report -> ReportListEntry(sessions.first { it.sessionSeq == report.sessionSeq }, report) }
            .filter { userSeq == null || it.session.userSeq == userSeq }
            .sortedWith(compareByDescending<ReportListEntry> { it.session.endedDttm }.thenByDescending { it.session.sessionSeq })
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

    val users = mutableMapOf<Int, UserInfo>()

    override fun findUsers(userSeqs: Collection<Int>) = userSeqs.mapNotNull { users[it] }.associateBy { it.userSeq }

    override fun endActiveWalks(sessionSeq: Long, endedAt: OffsetDateTime) = if (activeWalks.remove(sessionSeq) != null) 1 else 0

    override fun expirePendingEvents(sessionSeq: Long) = if (pendingEvents.remove(sessionSeq)) 1 else 0

    override fun countEvents(sessionSeq: Long) = eventCounts[sessionSeq] ?: EventCounts(0, 0)

    override fun countWalks(sessionSeq: Long) = walkCounts[sessionSeq] ?: 0

    override fun countByTypes(sessionSeq: Long, types: Collection<ActionType>) =
        logs.count { it.sessionSeq == sessionSeq && it.actionType in types }

    override fun findSpends(sessionSeq: Long) = transactions.filter { it.sessionSeq == sessionSeq }.map {
        val key = if (it.transactionType == TransactionType.EVENT) ReportAggregator.EVENT_KEY else shopItems.getValue(it.itemSeq!!).category.name
        SpendRecord(key, it.amount, checkNotNull(it.regDttm) { "테스트 지출에 regDttm이 없습니다" })
    }

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
        now?.let { at -> ReflectionTestUtils.setField(it, "regDttm", at) }
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

    override fun findInProgressSessionSeqs() = sessions.filter { it.status == SessionStatus.IN_PROGRESS }.mapNotNull { it.sessionSeq }

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

    /** 저장할 로그에 찍을 시각(실제로는 @CreationTimestamp). 테스트가 행동 시각에 맞춰 바꾼다 */
    var now: OffsetDateTime? = null

    override fun save(log: SimulationActionLog) = log.also {
        ReflectionTestUtils.setField(it, "actionLogSeq", 50000L + logs.size + 1)
        now?.let { at -> ReflectionTestUtils.setField(it, "regDttm", at) }
        logs += it
    }

    override fun findLastActionDttm(sessionSeq: Long, actionCode: ActionCode) =
        logs.filter { it.sessionSeq == sessionSeq && it.actionCode == actionCode }.mapNotNull { it.regDttm }.maxOrNull()

    /** 정렬은 기본값(최근 먼저 = PK desc)만 흉내 낸다 */
    override fun findPage(sessionSeq: Long, type: ActionType?, pageable: Pageable): Page<ActionLogEntry> {
        val all = logs.filter { it.sessionSeq == sessionSeq && (type == null || it.actionType == type) }
            .sortedByDescending { it.actionLogSeq }
            .map { ActionLogEntry(it, it.itemSeq?.let(shopItems::getValue)) }
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

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
            SimulationShopItem(12, "통통 놀이공", ItemCategory.TOY, 8_900, SpeciesScope.ALL, effects = mapOf("bond" to 4, "calmness" to 3), displayOrder = 12),
            SimulationShopItem(20, "꼬마 쥐 장난감", ItemCategory.TOY, 2_000, SpeciesScope.CAT, effects = mapOf("bond" to 3, "calmness" to 2), displayOrder = 20),
            SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, effects = mapOf("satiety" to 30), displayOrder = 22),
            SimulationShopItem(
                23, "바삭한 미니 비스킷", ItemCategory.SNACK, 4_500, SpeciesScope.ALL,
                effects = mapOf("satiety" to 5, "bond" to 3, "calmness" to 3), displayOrder = 23,
            ),
            SimulationShopItem(
                27, "동결건조 흰살생선", ItemCategory.SNACK, 13_000, SpeciesScope.CAT,
                effects = mapOf("satiety" to 10, "bond" to 4, "calmness" to 5), displayOrder = 27,
            ),
            SimulationShopItem(30, "배변패드", ItemCategory.LITTER, 2_600, SpeciesScope.DOG, effects = mapOf("cleanliness" to 30), displayOrder = 30),
            SimulationShopItem(
                33, "향긋한 캣닢", ItemCategory.CATNIP, 9_900, SpeciesScope.CAT,
                effects = mapOf("energy" to 15, "bond" to 3, "calmness" to -5), displayOrder = 33,
            ),
            SimulationShopItem(35, "기본 상비약", ItemCategory.MEDICINE, 15_000, SpeciesScope.ALL, effects = mapOf("health" to 20), displayOrder = 35),
            SimulationShopItem(90, "단종된 간식", ItemCategory.SNACK, 1_000, SpeciesScope.ALL, displayOrder = 90, isActive = false),
            SimulationShopItem(
                31, "화장실 모래", ItemCategory.LITTER, 7_200, SpeciesScope.CAT,
                effects = mapOf("cleanliness" to 30), imageKey = "simulation/items/litter.png", displayOrder = 31,
            ),
        ).forEach { shopItems[it.itemSeq] = it }
    }

    fun assembler() = SimulationSessionDetailAssembler(this, this, this)

    /** 종료 시 발행된 내부 이벤트 */
    val published = mutableListOf<Any>()

    fun reportCalculator(properties: SimulationProperties) = ReportCalculator(this, this, this, properties)

    fun terminator(properties: SimulationProperties) =
        SessionTerminator(this, this, this, reportCalculator(properties), ApplicationEventPublisher { published += it })

    /** 고정 시각 now 기준 진행(지연 계산·종료 판정) */
    fun progress(properties: SimulationProperties, now: OffsetDateTime) = SessionProgress(
        SessionStatRefresher(StatCalculator(properties.stats, properties.tick), Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul"))),
        terminator(properties),
    )

    companion object {
        val MALTESE = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")
        val KOREAN_SHORTHAIR = BreedInfo(31, "코리안 숏헤어", CatalogSpeciesCode.CAT, Personality.TIMID, null)
        val ETC = BreedInfo(90, "기타", CatalogSpeciesCode.ETC, Personality.TIMID, null)
    }
}
