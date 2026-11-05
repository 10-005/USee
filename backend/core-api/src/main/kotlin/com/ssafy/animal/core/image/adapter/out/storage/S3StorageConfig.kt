package com.ssafy.animal.core.image.adapter.out.storage

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * 자격 증명은 환경 변수(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)로만 받는다.
 * 기본 체인을 쓰면 키가 빠졌을 때 노드 IMDS 역할 등 다른 경로로 동작할 수 있어 고정한다.
 * 자격 증명은 호출 시점에 읽으므로 키가 없어도 앱은 뜬다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties::class)
class S3StorageConfig {

    @Bean(destroyMethod = "close")
    fun s3Client(properties: StorageProperties): S3Client = S3Client.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .build()

    @Bean(destroyMethod = "close")
    fun s3Presigner(properties: StorageProperties): S3Presigner = S3Presigner.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .build()
}
