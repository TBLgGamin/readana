plugins {
    alias(libs.plugins.readana.android.library.compose)
    alias(libs.plugins.readana.android.screenshot)
}

android {
    namespace = "dev.readana.core.designsystem"
}

dependencies {
    // Shared components speak in domain terms where a primitive would lose meaning — a highlight
    // tint, for instance, has to look the same in the reader and in the library.
    api(projects.core.model)

    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.adaptive)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)

    // Covers come from imported files *and* from search results, so Coil needs a network fetcher.
    // The loader itself is built in the app module, which owns the shared HttpClient.
    implementation(libs.coil.compose)
}
