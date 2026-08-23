package dev.readana

import androidx.lifecycle.ViewModel
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAllParentsOf
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Architecture rules enforced with Konsist. They run as ordinary unit tests so a violation
 * fails `./gradlew test` locally and in CI.
 */
class ArchitectureTest {
    @Test
    fun `ViewModels are named consistently`() {
        Konsist.scopeFromProject()
            .classes()
            .withAllParentsOf(ViewModel::class)
            .assertTrue { it.name.endsWith("ViewModel") }
    }

    @Test
    fun `pure JVM modules never import Android`() {
        Konsist.scopeFromProject()
            .files
            .filter { file ->
                JVM_MODULES.any { module -> file.normalizedPath.contains("/$module/src/") }
            }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("android.") || it.name.startsWith("androidx.")
                }
            }
    }

    @Test
    fun `feature modules do not depend on each other`() {
        Konsist.scopeFromProject()
            .files
            .withPackage("dev.readana.feature..")
            .assertFalse { file ->
                val ownFeature = file.packagee?.name?.removePrefix(
                    "dev.readana.feature."
                )?.substringBefore('.')
                file.imports.any { import ->
                    import.name.startsWith("dev.readana.feature.") &&
                        ownFeature != null &&
                        !import.name.startsWith("dev.readana.feature.$ownFeature.")
                }
            }
    }

    @Test
    fun `sources, engines and the AI client stay independent of the data layer`() {
        Konsist.scopeFromProject()
            .files
            .filter { file ->
                file.normalizedPath.contains("/source/") ||
                    file.normalizedPath.contains("/engine/") ||
                    file.normalizedPath.contains("/ai/client/src/")
            }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("dev.readana.core.data") }
            }
    }

    private val com.lemonappdev.konsist.api.declaration.KoFileDeclaration.normalizedPath: String
        get() = path.replace('\\', '/')

    private companion object {
        val JVM_MODULES = listOf(
            "core/common",
            "core/model",
            "core/testing",
            "source/api",
            "source/gutenberg",
            "source/annas",
            "source/arxiv",
            "source/openlibrary",
            "source/opds",
            "source/trending",
            "engine/epub",
            "engine/reflow",
            "ai/client"
        )
    }
}
