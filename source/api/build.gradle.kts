plugins {
    alias(libs.plugins.readana.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
}
