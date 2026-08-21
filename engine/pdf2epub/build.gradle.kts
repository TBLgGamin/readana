plugins {
    alias(libs.plugins.readana.android.library)
}

android {
    namespace = "dev.readana.engine.pdf2epub"
}

dependencies {
    api(projects.engine.reflow)
    api(projects.engine.epub)
    implementation(projects.core.common)
    implementation(libs.pdfbox.android)

    // PDFBox needs a Context, so extraction is exercised under Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

// Golden files are rewritten with:
//   ./gradlew :engine:pdf2epub:testDebugUnitTest --tests "*CorpusGolden*" -PupdateGoldens=true
tasks.withType<Test>().configureEach {
    systemProperty("readana.updateGoldens", providers.gradleProperty("updateGoldens").getOrElse("false"))
    systemProperty("readana.moduleDir", projectDir.absolutePath)
}
