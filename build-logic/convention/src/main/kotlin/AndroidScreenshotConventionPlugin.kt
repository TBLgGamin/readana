import dev.readana.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Screenshot tests for a Compose module.
 *
 * Roborazzi renders through Robolectric, so these run inside the ordinary `testDebugUnitTest`
 * task — no device, no emulator, and CI needs nothing it does not already have.
 *
 * ```
 * ./gradlew testDebugUnitTest                          # verify against the committed goldens
 * ./gradlew testDebugUnitTest -PrecordScreenshots=true  # accept what the UI looks like now
 * ```
 *
 * Roborazzi's own Gradle plugin is not applied: it still reaches for AGP's `TestedExtension`,
 * which AGP 9 removed, so it fails to apply. Everything it would do is two system properties,
 * set here — and driving it with a `-P` flag matches the conversion corpus's `-PupdateGoldens`.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                // The Compose BOM is only on the main and androidTest classpaths by default, and
                // the screenshot tests need the UI-test artifacts from it.
                "testImplementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
                "testImplementation"(libs.findLibrary("robolectric").get())
                "testImplementation"(libs.findLibrary("androidx-test-core").get())
                "testImplementation"(libs.findLibrary("androidx-test-ext-junit").get())
                "testImplementation"(libs.findLibrary("roborazzi").get())
                "testImplementation"(libs.findLibrary("roborazzi-compose").get())
                "testImplementation"(libs.findLibrary("roborazzi-junit-rule").get())
            }

            val recording = providers.gradleProperty("recordScreenshots")
                .map(String::toBoolean)
                .orElse(false)

            tasks.withType<Test>().configureEach {
                // Exactly one of the two is on, so a run is never left to Roborazzi's default.
                systemProperty("roborazzi.test.record", recording.get().toString())
                systemProperty("roborazzi.test.verify", (!recording.get()).toString())
                // Goldens live beside the tests that produce them rather than under build/, so a
                // change to one shows up in review the way any other change does.
                systemProperty("roborazzi.output.dir", "$projectDir/src/test/screenshots")
                // Robolectric renders off-screen through its own graphics stack.
                systemProperty("robolectric.graphicsMode", "NATIVE")
            }
        }
    }
}
