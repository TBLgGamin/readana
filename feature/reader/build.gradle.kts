plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.reader"
}

dependencies {
    // Publications are opened by core/data; this module owns the navigator (reading) side.
    implementation(libs.readium.shared)
    implementation(libs.readium.navigator)
    implementation(libs.androidx.fragment.ktx)
}
