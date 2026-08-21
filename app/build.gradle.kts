import java.util.Properties

plugins {
    alias(libs.plugins.readana.android.application)
    alias(libs.plugins.readana.android.application.compose)
    alias(libs.plugins.readana.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is optional: drop a keystore.properties (git-ignored) next to settings.gradle.kts
// with storeFile/storePassword/keyAlias/keyPassword. Without it, release builds are debug-signed,
// which is fine for side-loading onto your own tablet.
val keystoreProperties = Properties().apply {
    val file = isolated.rootProject.projectDirectory.file("keystore.properties").asFile
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "dev.readana"

    defaultConfig {
        applicationId = "dev.readana"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile =
                    isolated.rootProject.projectDirectory.file(
                        keystoreProperties.getProperty("storeFile")
                    ).asFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "DebugProbesKt.bin"
        )
    }
}

dependencies {
    implementation(projects.feature.browser)
    implementation(projects.feature.library)
    implementation(projects.feature.discover)
    implementation(projects.feature.reader)
    implementation(projects.feature.settings)

    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelNavigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigationSuite)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    // On-demand WorkManager init: the app supplies the Hilt worker factory (see ReadanaApplication).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
}
