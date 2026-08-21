plugins {
    alias(libs.plugins.readana.android.library.compose)
}

android {
    namespace = "dev.readana.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.adaptive)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)

    // Covers are files that the importer extracted, so Coil's core is enough (no network fetcher).
    implementation(libs.coil.compose)
}
