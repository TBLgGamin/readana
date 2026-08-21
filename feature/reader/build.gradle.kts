plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.reader"
}

dependencies {
    // Readium Kotlin Toolkit: parsing (streamer) now, navigator UI in milestone M2.
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.adapter.pdfium)
    implementation(libs.androidx.fragment.ktx)
}
