plugins {
    alias(libs.plugins.readana.jvm.library)
}

dependencies {
    // epubcheck validates every EPUB the writer produces in unit tests (test-only, heavy).
    testImplementation(libs.epubcheck)
}
