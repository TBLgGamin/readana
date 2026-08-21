plugins {
    alias(libs.plugins.readana.android.library)
    alias(libs.plugins.readana.android.room)
    alias(libs.plugins.readana.hilt)
    // The conversion report is stored as JSON in a column, so @Serializable needs the plugin.
    alias(libs.plugins.kotlin.serialization)
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
    // PDF → EPUB conversion; the dependency runs data → engine, never the other way.
    api(projects.engine.pdf2epub)

    // Readium parses publications for the importer (metadata + cover) and for the reader.
    api(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.adapter.pdfium)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    // Downloads run as a WorkManager job so HyperOS keeps them alive (see docs/DEVICE.md).
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
