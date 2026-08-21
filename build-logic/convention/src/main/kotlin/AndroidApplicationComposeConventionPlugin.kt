import com.android.build.api.dsl.ApplicationExtension
import dev.readana.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

/** `readana.android.application.compose`: Compose setup for the app module. */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            configureAndroidCompose(extensions.getByType<ApplicationExtension>())
        }
    }
}
