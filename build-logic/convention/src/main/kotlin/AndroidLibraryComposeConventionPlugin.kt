import com.android.build.api.dsl.LibraryExtension
import dev.readana.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

/** `readana.android.library.compose`: Compose setup for Android library modules. */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "readana.android.library")
            configureAndroidCompose(extensions.getByType<LibraryExtension>())
        }
    }
}
