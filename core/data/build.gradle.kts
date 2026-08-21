plugins {
    alias(libs.plugins.readana.android.library)
    alias(libs.plugins.readana.android.room)
    alias(libs.plugins.readana.hilt)
}

android {
    namespace = "dev.readana.core.data"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(projects.source.api)
    implementation(projects.source.gutenberg)
    implementation(projects.source.annas)

    // Readium parses publications for the importer (metadata + cover) and for the reader.
    api(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.adapter.pdfium)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
