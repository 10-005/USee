package com.ssafy.animal.core.image.adapter.out.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 값이 비어 있어도 앱은 뜬다. 이미지 API를 호출할 때만 실패한다(로컬은 S3 없이 개발). */
@ConfigurationProperties("storage")
data class StorageProperties(
    val bucket: String = "",
    val region: String = "ap-northeast-2",
    /** 버킷 또는 CloudFront 도메인. imageUrl = {publicBaseUrl}/{imageKey} */
    val publicBaseUrl: String = "",
    val putExpiry: Duration = Duration.ofMinutes(10),
)
