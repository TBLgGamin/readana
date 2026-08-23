// Root build file: declares plugin versions for subprojects and hosts the
// repository-wide code quality tasks (Spotless formatting, detekt analysis).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// ---------------------------------------------------------------------------
// Formatting: `./gradlew spotlessApply` to fix, `spotlessCheck` to verify (runs in `check`).
// ---------------------------------------------------------------------------
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(file(".editorconfig"))
            .editorConfigOverride(mapOf("max_line_length" to "120"))
            .customRuleSets(
                listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}")
            )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(file(".editorconfig"))
            .editorConfigOverride(mapOf("max_line_length" to "120"))
    }
    format("misc") {
        // Explicit roots: a bare "**/*" target makes the configuration cache fingerprint
        // .gradle/ lock files, which fails while the daemon holds them.
        target(
            "*.md",
            ".gitignore",
            ".gitattributes",
            ".editorconfig",
            ".pre-commit-config.yaml",
            "docs/**/*.md",
            ".github/**/*.yml",
            ".claude/**/*.md",
            "config/**/*.yml",
            "config/**/*.xml",
            "gradle/*.toml",
            "app/src/**/*.xml",
            "core/*/src/**/*.xml",
            "feature/*/src/**/*.xml"
        )
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------------
// Static analysis: `./gradlew detekt` (runs in `check`). Config in config/detekt/detekt.yml.
// Runs once from the root over every module's sources (no type resolution) so that
// modules stay free of analysis plumbing.
// ---------------------------------------------------------------------------
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(
        fileTree(projectDir) {
            include("**/src/**/*.kt", "**/src/**/*.kts", "build-logic/**/src/**/*.kt")
            exclude("**/build/**", ".gradle/**")
        }
    )
}

dependencies {
    detektPlugins(libs.compose.rules.detekt)
}
