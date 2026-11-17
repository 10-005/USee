plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common"))
    runtimeOnly(libs.postgresql)
    // core 스키마 DDL·시드는 core-api가 Flyway로 관리한다(DB 명세 Q412)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.database.postgresql)
    // access·refresh 발급(RS256 서명)은 core-api만 한다
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.orgjson)
    implementation(platform(libs.awssdk.bom))
    // 동기 S3Client는 apache 클라이언트를 쓰므로 비동기용 netty는 뺀다
    implementation(libs.awssdk.s3) {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
}

tasks.jar {
    enabled = false
}
