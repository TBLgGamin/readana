plugins {
    alias(libs.plugins.readana.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.common)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
