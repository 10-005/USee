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

/** 물품 적용 종(DB 36 species_scope) */
enum class SpeciesScope {
    DOG, CAT, ALL;

    fun includes(speciesType: SpeciesType) = this == ALL || name == speciesType.name
}

/**
 * DB 명세 36. simulation_shop_items. Flyway 시드로만 넣고 관리 API가 없어(Q412) 앱은 읽기만 한다.
 * 사진은 images FK가 아니라 저장소 키 문자열(Q420)이며 응답에서 URL로 바꾼다.
 */
@Entity
@Immutable
@Table(name = "simulation_shop_items")
class SimulationShopItem(
    itemSeq: Long,
    itemName: String,
    category: ItemCategory,
    price: Int,
    speciesScope: SpeciesScope,
    effects: Map<String, Int>? = null,
    description: String? = null,
    imageKey: String? = null,
    displayOrder: Int = 0,
    isActive: Boolean = true,
) : BaseTimeEntity() {

    @Id
    var itemSeq: Long = itemSeq
        protected set

    @Column(nullable = false, length = 100)
    var itemName: String = itemName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: ItemCategory = category
        protected set

    /** 기구는 FALSE(DB CHECK와 같은 규칙, M9). 분류에서 정해지므로 따로 받지 않는다 */
    @Column(nullable = false)
    var isConsumable: Boolean = !category.isEquipment
        protected set

    @Column(nullable = false)
    var price: Int = price
        protected set

    @Column(columnDefinition = "text")
    var description: String? = description
        protected set

    @Column(length = 512)
    var imageKey: String? = imageKey
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var speciesScope: SpeciesScope = speciesScope
        protected set

    /** 사용 시 스탯 변화. 키는 스탯 컬럼명, 양수가 좋은 방향(Q485). 기구는 null */
    @JdbcTypeCode(SqlTypes.JSON)
    var effectsJsonb: Map<String, Int>? = effects
        protected set

    @Column(nullable = false)
    var isActive: Boolean = isActive
        protected set

    @Column(nullable = false)
    var displayOrder: Int = displayOrder
        protected set
}
