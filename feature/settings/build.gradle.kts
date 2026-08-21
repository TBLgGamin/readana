plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.settings"
}

dependencies {
    // Folder picker for the optional SAF library folder.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
