import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

// Genera el perfil de arranque y mide arranque y fluidez sobre la variante "benchmark" de :app.
//   Perfil:    ./gradlew :baselineprofile:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.entretv.baselineprofile.BaselineProfileGenerator
//   Medición:  ./gradlew :baselineprofile:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.entretv.baselineprofile.StartupBenchmark
android {
    namespace = "com.entretv.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Las mediciones se hacen en el emulador de TV: se acepta con aviso.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
