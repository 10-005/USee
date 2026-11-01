plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common"))
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.jjwt.api)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.orgjson)
}

tasks.jar {
    enabled = false
}
