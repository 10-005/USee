import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spring.boot) apply false
}

// KSP2는 여러 모듈의 kspKotlin이 한 JVM에서 동시에 돌면 간헐적으로 Q클래스를 하나도 만들지 않는다(CI에서 QSample 미생성).
// 모든 ksp* 작업이 이 서비스를 쓰게 해 한 번에 하나씩만 실행한다
abstract class KspSerial : BuildService<BuildServiceParameters.None>
val kspSerial = gradle.sharedServices.registerIfAbsent("kspSerial", KspSerial::class) { maxParallelUsages.set(1) }

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

    tasks.matching { it.name.startsWith("ksp") }.configureEach { usesService(kspSerial) }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
