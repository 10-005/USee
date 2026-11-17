plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common"))
    runtimeOnly(libs.postgresql)
    // FCM 발송(Jira 211). 키가 없으면 LoggingFcmSender로 대체
    implementation(libs.firebase.admin)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
}

tasks.jar {
    enabled = false
}
