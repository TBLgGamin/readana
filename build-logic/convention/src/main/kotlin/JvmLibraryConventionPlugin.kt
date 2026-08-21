import com.android.build.api.dsl.Lint
import dev.readana.buildlogic.configureKotlinJvm
import dev.readana.buildlogic.configureLint
import dev.readana.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `readana.jvm.library`: Android-free Kotlin modules (models, engines, book sources).
 * Keeping these pure JVM means fast unit tests and no Robolectric.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            // Android Lint also runs on JVM modules (e.g. catches kotlinx.coroutines misuse).
            apply(plugin = "com.android.lint")

            configureKotlinJvm()
            extensions.configure<Lint> { configureLint(this) }

            dependencies {
                "testImplementation"(libs.findLibrary("kotlin-test").get())
                "testImplementation"(libs.findLibrary("junit4").get())
            }
        }
    }
}
