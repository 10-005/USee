plugins {
    `java-library`
}

dependencies {
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.data.redis)
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.validation)
    api(libs.spring.boot.starter.actuator)
    api(libs.springdoc.openapi.webmvc.ui)
    api(libs.querydsl.jpa)
    api(libs.jackson.module.kotlin)
    api(libs.kotlin.reflect)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.orgjson)
    // STOMP 인터셉터는 spring-messaging이 있는 서비스(chat)에서만 활성화
    compileOnly(libs.spring.messaging)
}
