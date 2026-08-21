import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.LibraryExtension
import dev.readana.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `readana.android.room`: Room database with KSP codegen and exported schemas for migrations. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "androidx.room")
            apply(plugin = "com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                // Schemas are committed so migrations can be diffed and tested.
                schemaDirectory("$projectDir/schemas")
            }

            // MigrationTestHelper loads the exported schemas from the test assets.
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("test") { assets.srcDir("$projectDir/schemas") }
            }

            dependencies {
                "implementation"(libs.findLibrary("androidx-room-runtime").get())
                "ksp"(libs.findLibrary("androidx-room-compiler").get())
                "testImplementation"(libs.findLibrary("androidx-room-testing").get())
            }
        }
    }
}
