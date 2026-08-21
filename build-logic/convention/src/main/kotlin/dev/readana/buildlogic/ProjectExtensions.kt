package dev.readana.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog declared in `gradle/libs.versions.toml`. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Resolves a `[versions]` entry, failing loudly if the alias is missing. */
fun VersionCatalog.versionInt(alias: String): Int = findVersion(alias).orElseThrow {
    IllegalStateException("Missing version alias '$alias' in libs.versions.toml")
}
    .requiredVersion
    .toInt()
