plugins {
    alias(libs.plugins.readana.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.source.api)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)

    testImplementation(projects.core.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
