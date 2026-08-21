package dev.readana.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Shared Android + Kotlin configuration. AGP 9 compiles Kotlin itself ("built-in Kotlin"),
 * so no `org.jetbrains.kotlin.android` plugin is applied anywhere in this project.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig.minSdk = libs.versionInt("minSdk")

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            // Readium's pdfium adapter is built with desugaring and its AAR metadata demands it.
            isCoreLibraryDesugaringEnabled = true
        }
    }
    configureKotlin<KotlinAndroidProjectExtension>()
    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("desugar-jdk-libs").get())
    }
    // Hilt/KSP generate test sources even in modules without their own tests, which would
    // otherwise trip Gradle's "no tests discovered" failure.
    tasks.withType(Test::class.java).configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}

/** Shared configuration for pure-JVM Kotlin modules (domain, engines, sources). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin<KotlinJvmProjectExtension>()
}

private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val warningsAsErrors = providers.gradleProperty(
        "warningsAsErrors"
    ).map(String::toBoolean).orElse(false)
    val options = when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> error("Unsupported Kotlin extension ${T::class}")
    }
    options.apply {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(warningsAsErrors)
        freeCompilerArgs.addAll(
            "-Xconsistent-data-class-copy-visibility",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}
