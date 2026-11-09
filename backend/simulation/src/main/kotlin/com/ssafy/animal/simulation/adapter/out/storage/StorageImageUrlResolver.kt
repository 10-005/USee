package com.ssafy.animal.simulation.adapter.out.storage

import com.ssafy.animal.simulation.application.port.out.ImageUrlResolver
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** 이미지 공개 URL 기준 주소. core-api의 storage.public-base-url과 같은 값을 쓴다 */
@ConfigurationProperties("storage")
data class StorageUrlProperties(
    val publicBaseUrl: String = "",
)

/** imageUrl = {publicBaseUrl}/{imageKey}(core-api S3ImageStorage.publicUrl과 같은 규칙) */
@Component
class StorageImageUrlResolver(
    private val properties: StorageUrlProperties,
) : ImageUrlResolver {
    override fun publicUrl(key: String) = "${properties.publicBaseUrl.trimEnd('/')}/$key"
}
