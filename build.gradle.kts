import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.shadowjar) apply false
}

allprojects {
    group = "aniyomi"
    version = "1.0"
}

val projects = listOf(
    project(":AndroidCompat"),
    project(":AndroidCompat:Config"),
    project(":inspector"),
)

configure(projects) {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    val javaVersion = JavaVersion.VERSION_17

    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }

    dependencies {
        implementation(rootProject.libs.bundles.common)
    }
}
