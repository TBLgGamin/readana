plugins {
    alias(libs.plugins.readana.android.feature)
    alias(libs.plugins.readana.android.screenshot)
}

android {
    namespace = "dev.readana.feature.settings"
}

dependencies {
    // Folder picker for the optional SAF library folder.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
