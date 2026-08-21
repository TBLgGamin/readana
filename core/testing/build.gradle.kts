plugins {
    alias(libs.plugins.readana.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(projects.source.api)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit4)
}
