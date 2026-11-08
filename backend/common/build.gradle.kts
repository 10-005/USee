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
    // 서비스 간 이벤트(API 명세 21.6). events.transport=rabbit일 때만 실제로 연결한다
    api(libs.spring.boot.starter.amqp)
    api(libs.springdoc.openapi.webmvc.ui)
    api(libs.querydsl.jpa)
    api(libs.jackson.module.kotlin)
    api(libs.kotlin.reflect)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.orgjson)
    // STOMP 인터셉터는 spring-messaging이 있는 서비스(chat)에서만 활성화
    compileOnly(libs.spring.messaging)
    // 자동 설정 조건 테스트(ApplicationContextRunner)
    testImplementation(libs.spring.boot.starter.webmvc.test)
}
