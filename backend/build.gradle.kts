import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spring.boot) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "com.google.devtools.ksp")

    group = "com.ssafy.animal"

    repositories {
        mavenCentral()
    }

    // Hibernate 지연 로딩 프록시를 위해 엔티티 클래스를 open으로
    configure<AllOpenExtension> {
        annotations("jakarta.persistence.Entity", "jakarta.persistence.MappedSuperclass", "jakarta.persistence.Embeddable")
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    dependencies {
        val bom = platform(rootProject.libs.spring.boot.dependencies)
        "implementation"(bom)
        "ksp"(bom)
        "testImplementation"(bom)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
        "testImplementation"(rootProject.libs.kotlin.test.junit5)
        // 엔티티가 있는 모듈마다 Q클래스 생성
        "ksp"(rootProject.libs.querydsl.ksp.codegen)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
