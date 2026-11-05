plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common"))
    runtimeOnly(libs.postgresql)
    implementation(platform(libs.awssdk.bom))
    // 동기 S3Client는 apache 클라이언트를 쓰므로 비동기용 netty는 뺀다
    implementation(libs.awssdk.s3) {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.jjwt.api)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.orgjson)
}

tasks.jar {
    enabled = false
}
