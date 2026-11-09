plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common"))
    runtimeOnly(libs.postgresql)
    // simulation 스키마 DDL·시드는 simulation이 Flyway로 관리한다(DB 명세 Q412)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.database.postgresql)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    // 컨트롤러 테스트에서 실제 JWT 필터를 거치도록 테스트용 토큰을 서명한다(impl·orgjson은 common 런타임으로 들어옴)
    testImplementation(libs.jjwt.api)
}

tasks.jar {
    enabled = false
}
