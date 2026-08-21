plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.discover"
}

dependencies {
    // Runtime notification permission for download progress.
    implementation(libs.androidx.activity.compose)
}
