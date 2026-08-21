plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.library"
}

dependencies {
    // The system file picker for importing books.
    implementation(libs.androidx.activity.compose)
}
