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
}
