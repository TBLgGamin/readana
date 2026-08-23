plugins {
    alias(libs.plugins.readana.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.source.api)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    // OPDS 1.2 feeds are Atom; jsoup parses them in XML mode without pulling in an Android parser.
    implementation(libs.jsoup)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
