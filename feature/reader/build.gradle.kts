plugins {
    alias(libs.plugins.readana.android.feature)
}

android {
    namespace = "dev.readana.feature.reader"
}

kotlin {
    compilerOptions {
        // The navigator's preferences and input APIs are all experimental in Readium 3.
        freeCompilerArgs.addAll("-opt-in=org.readium.r2.shared.ExperimentalReadiumApi")
    }
}

dependencies {
    // Publications are opened by core/data; this module owns the navigator (reading) side.
    implementation(libs.readium.shared)
    implementation(libs.readium.navigator)
    implementation(libs.readium.adapter.pdfium)
    // Read-aloud: Readium drives Android's TTS engine over the publication's own text.
    implementation(libs.readium.navigator.media.tts)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Answers may point at a picture: a plate from the book, or one looked up for a subject.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    // Readium's preference and locator types touch android.* even in pure logic, so the reader's
    // unit tests run under Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
