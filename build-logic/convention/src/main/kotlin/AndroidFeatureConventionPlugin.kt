import dev.readana.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `readana.android.feature`: a user-facing screen module. Features depend on core modules,
 * never on each other; the app module stitches them together through Navigation 3 keys.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "readana.android.library.compose")
            apply(plugin = "readana.hilt")
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:common"))
                "implementation"(project(":core:model"))
                "implementation"(project(":core:data"))
                "implementation"(project(":core:designsystem"))

                "implementation"(libs.findLibrary("androidx-compose-foundation").get())
                "implementation"(libs.findLibrary("androidx-compose-material3").get())
                "implementation"(libs.findLibrary("androidx-compose-material-iconsExtended").get())
                "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx-navigation3-runtime").get())
                "implementation"(libs.findLibrary("androidx-navigation3-ui").get())
                "implementation"(libs.findLibrary("kotlinx-serialization-json").get())

                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
                "testImplementation"(libs.findLibrary("turbine").get())
            }
        }
    }
}
