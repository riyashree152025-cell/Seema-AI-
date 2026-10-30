import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.raju.aiassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.raju.aiassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Offline voices (sherpa-onnx) ship native code: 64-bit phones, plus x86_64 for the emulator test.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release signing is configured locally (see README) — never commit keystores or passwords.
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * sherpa-onnx Android library (k2-fsa/sherpa-onnx release). 1.13.8 is the last release that still
 * includes espeak-ng, which Piper and Kokoro need to read Hindi (2.x removed it). The 50 MB AAR is
 * downloaded once (Android Studio sync or CI) and checked against its SHA-256; it is not in git.
 */
val sherpaOnnxVersion = "1.13.8"
val sherpaOnnxSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaOnnxAar: File = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar").also { aar ->
    fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    if (!aar.isFile || sha256(aar) != sherpaOnnxSha256) {
        aar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
        logger.lifecycle("Downloading $url")
        val tmp = File(aar.path + ".part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(sha256(tmp) == sherpaOnnxSha256) { "sherpa-onnx AAR checksum mismatch — download it again" }
        tmp.renameTo(aar)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox.android)
    implementation(libs.commons.compress)
    // sherpa-onnx: on-device runtime for the offline voices (Piper, Kokoro). See sherpaOnnxAar below.
    implementation(files(sherpaOnnxAar))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
