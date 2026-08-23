plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "dev.readana.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmarks drive the app from outside, so they need a real device: API 29 is the
        // app's own floor and the lowest the benchmark library supports for profiles.
        minSdk = 29
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Measurements only mean something against release code, so the benchmark build type is
    // release minus the release keystore (see app/build.gradle.kts).
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

baselineProfile {
    // Generated on the tablet over adb rather than on a managed emulator: the profile should
    // reflect the device the app is actually read on (docs/DEVICE.md). CI has no device at all,
    // so the result is committed into :app rather than rebuilt — see docs/RELEASING.md.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit4)
}
