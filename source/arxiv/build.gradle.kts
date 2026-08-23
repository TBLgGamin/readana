plugins {
    alias(libs.plugins.readana.jvm.library)
}

dependencies {
    api(projects.source.api)
    // The arXiv API answers in Atom, so it reuses the OPDS module's XML helper rather than
    // carrying a second copy of it.
    implementation(projects.source.opds)
    implementation(libs.ktor.client.core)
    // AtomXml hands back a jsoup Document, so the parser is a direct dependency here too.
    implementation(libs.jsoup)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
