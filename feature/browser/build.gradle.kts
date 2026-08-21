plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.browser"
}

dependencies {
    // BackHandler, so hardware back walks the WebView history before leaving the screen.
    implementation(libs.androidx.activity.compose)
}
