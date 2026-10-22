import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Firma de release fuera del repositorio. Sin este archivo el release sale sin firmar.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val updateUrl: String = (findProperty("entretv.updateUrl") as String?).orEmpty()
// Servidor Xtream de las cuentas nuevas. update.json puede cambiarlo sin reinstalar.
val serverUrl: String = (findProperty("entretv.serverUrl") as String?).orEmpty()
// Clave de TMDB (opcional): Top 10 por tendencias, fotos del reparto y fichas ampliadas.
// Se guarda en keystore.properties (fuera del repositorio) como tmdbApiKey=...
val tmdbApiKey: String = (findProperty("entretv.tmdbApiKey") as String?).orEmpty()
    .ifBlank { keystoreProps.getProperty("tmdbApiKey").orEmpty() }
val minVersionCode: Int = (findProperty("entretv.minVersionCode") as String?)?.toIntOrNull() ?: 0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.entretv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.entretv.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.5"
        resourceConfigurations += listOf("es", "en")
        // Solo el release busca actualizaciones: el APK publicado es otro paquete que el de depuración.
        buildConfigField("String", "UPDATE_URL", "\"\"")
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // APK ligero: una variante por arquitectura, sin librerías nativas innecesarias
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            // El universal es el que se publica: instala en cualquier TV, tablet o celular.
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json"
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Igual que release (R8, sin depuración) pero con el paquete y la firma de depuración:
        // se instala encima de la app de pruebas del emulador sin perder la sesión iniciada, y
        // ahí se genera el perfil de arranque y se miden arranque y fluidez (:baselineprofile).
        create("benchmark") {
            initWith(getByName("release"))
            proguardFiles("benchmark-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            matchingFallbacks += listOf("release")
            buildConfigField("String", "UPDATE_URL", "\"\"")
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (out.getFilter("ABI") == null) {
                out.outputFileName = "EntreTV-${variant.versionName}.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Lint de AGP 8.7 revienta en el JDK 25 de Android Studio (el error es "25.0.3").
    lint {
        checkReleaseBuilds = false
    }
}

// update.json para GitHub Releases: se sube en el mismo release que el APK universal.
val writeUpdateManifest by tasks.registering {
    description = "Escribe update.json junto al APK de release."
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val notes = (findProperty("entretv.releaseNotes") as String?).orEmpty()
    val manifestUrl = updateUrl
    val minCode = minVersionCode
    val server = serverUrl
    val tmdbKey = tmdbApiKey
    val output = layout.buildDirectory.file("outputs/apk/release/update.json")
    outputs.file(output)
    doLast {
        if (manifestUrl.isBlank()) {
            logger.warn("entretv.updateUrl está vacío: no se genera update.json.")
            return@doLast
        }
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val apkUrl = manifestUrl.substringBeforeLast('/') + "/EntreTV-$name.apk"
        val optional = buildString {
            if (server.isNotBlank()) append(",\n  \"serverUrl\": \"${esc(server)}\"")
            if (tmdbKey.isNotBlank()) append(",\n  \"tmdbApiKey\": \"${esc(tmdbKey)}\"")
        }
        output.get().asFile.writeText(
            "{\n" +
                "  \"versionCode\": $code,\n" +
                "  \"versionName\": \"${esc(name.orEmpty())}\",\n" +
                "  \"apkUrl\": \"${esc(apkUrl)}\",\n" +
                "  \"notes\": \"${esc(notes)}\",\n" +
                "  \"minVersionCode\": $minCode,\n" +
                "  \"mandatory\": ${minCode >= (code ?: 0)}" +
                optional + "\n}\n"
        )
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(writeUpdateManifest) }

// Android Studio 2026 trae JDK 25. Kotlin 2.1 no tiene target 25 y cae a JVM 23,
// mientras javac queda en 17. Hay que fijar el bytecode a 17 en Kotlin y en KSP
// sin pedir un JDK 17 aparte (el plugin foojay 1.0.0 revienta en Gradle 9.6).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Informes de estabilidad de Compose: ./gradlew :app:assembleRelease -PcomposeReports
composeCompiler {
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Idioma de la app (setApplicationLocales) también antes de Android 13.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    // "Seguir viendo" y el canal de EntreTV en la pantalla de inicio de Android TV.
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.datasource.rtmp)
    // Audio AC3/E-AC3/DTS/TrueHD en equipos sin decodificador propio (GPL v3).
    implementation(libs.media3.ffmpeg.decoder)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // Aplica el perfil de arranque al instalar el APK desde GitHub (sin Play Store).
    implementation(libs.androidx.profileinstaller)
}

