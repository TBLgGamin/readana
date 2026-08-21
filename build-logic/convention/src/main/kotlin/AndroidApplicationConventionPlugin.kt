import com.android.build.api.dsl.ApplicationExtension
import dev.readana.buildlogic.configureKotlinAndroid
import dev.readana.buildlogic.configureLint
import dev.readana.buildlogic.libs
import dev.readana.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `readana.android.application`: the single `:app` module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.versionInt("targetSdk")
                buildFeatures.buildConfig = true
                testOptions.animationsDisabled = true
                // Lint the whole dependency graph from the app so one task covers every module.
                configureLint(lint, checkDependencies = true)
            }

            dependencies {
                "testImplementation"(libs.findLibrary("kotlin-test-junit").get())
                "testImplementation"(libs.findLibrary("junit4").get())
            }
        }
    }
}
