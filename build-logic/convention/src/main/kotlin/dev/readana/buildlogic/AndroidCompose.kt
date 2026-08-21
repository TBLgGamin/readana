package dev.readana.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Enables Jetpack Compose on an Android module and wires the BOM + tooling dependencies. */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    apply(plugin = "org.jetbrains.kotlin.plugin.compose")

    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }
    }

    // The whole UI is Material 3 Expressive; opting in once here keeps the annotation out of
    // every screen. Expressive APIs are still experimental in material3 1.4, so the Compose BOM
    // must be bumped deliberately.
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        "implementation"(platform(bom))
        "androidTestImplementation"(platform(bom))
        "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        fun Provider<String>.onlyIfTrue() = flatMap { provider { it.takeIf(String::toBoolean) } }
        fun Provider<*>.relativeToRootProject(dir: String) = map {
            isolated.rootProject.projectDirectory
                .dir("build")
                .dir(projectDir.toRelativeString(rootDir))
        }.map { it.dir(dir) }

        // ./gradlew assembleDebug -PenableComposeCompilerMetrics=true -PenableComposeCompilerReports=true
        providers.gradleProperty("enableComposeCompilerMetrics").onlyIfTrue()
            .relativeToRootProject("compose-metrics")
            .let(metricsDestination::set)
        providers.gradleProperty("enableComposeCompilerReports").onlyIfTrue()
            .relativeToRootProject("compose-reports")
            .let(reportsDestination::set)

        stabilityConfigurationFiles.add(
            isolated.rootProject.projectDirectory.file("config/compose/stability.conf")
        )
    }
}
