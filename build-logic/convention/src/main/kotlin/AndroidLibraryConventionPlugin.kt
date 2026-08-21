import com.android.build.api.dsl.LibraryExtension
import dev.readana.buildlogic.configureKotlinAndroid
import dev.readana.buildlogic.configureLint
import dev.readana.buildlogic.libs
import dev.readana.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `readana.android.library`: Android library modules (the core modules and engine:pdf2epub). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = libs.versionInt("targetSdk")
                lint.targetSdk = libs.versionInt("targetSdk")
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.animationsDisabled = true
                testOptions.unitTests.isIncludeAndroidResources = true
                configureLint(lint)

                // Resources inside ":feature:library" must be prefixed "feature_library_" to avoid clashes.
                resourcePrefix = path.split("""\W""".toRegex()).drop(1).distinct()
                    .joinToString(separator = "_").lowercase() + "_"
            }

            dependencies {
                "testImplementation"(libs.findLibrary("kotlin-test-junit").get())
                "testImplementation"(libs.findLibrary("junit4").get())
                "androidTestImplementation"(libs.findLibrary("kotlin-test-junit").get())
            }
        }
    }
}
