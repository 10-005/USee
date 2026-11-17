package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.`in`.ShopItemView
import com.ssafy.animal.simulation.application.port.`in`.ShopQueryUseCase
import com.ssafy.animal.simulation.application.port.out.ImageUrlResolver
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.SpeciesType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShopQueryService(
    private val shopItemRepository: ShopItemRepository,
    private val imageUrlResolver: ImageUrlResolver,
    private val properties: SimulationProperties,
) : ShopQueryUseCase {

    @Transactional(readOnly = true)
    override fun getItems(speciesType: SpeciesType?): List<ShopItemView> =
        shopItemRepository.findActive(speciesType).map { item ->
            ShopItemView(
                item = item,
                imageUrl = item.imageKey?.let(imageUrlResolver::publicUrl),
                usesPerPurchase = properties.shop.usesPerPurchase(item.itemSeq),
            )
        }
}
