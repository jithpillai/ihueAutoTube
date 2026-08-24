import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("app/keystore/keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "dev.local.autotube"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.local.autotube"
        minSdk = 29          // Car App Library requires API 23+; 29 keeps things simple
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.5"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = rootProject.file("app/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false // personal use only, keep behavior identical to what's been tested
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = false
    }
}

dependencies {
    // Android for Cars App Library — this is what gets us hosted on the car screen.
    // Deliberately NOT depending on androidx.car.app:app-automotive: that artifact's own
    // manifest declares <uses-feature android.hardware.type.automotive>, which Play Console
    // rejects alongside the com.google.android.gms.car.application metadata (phone + Android
    // Auto projection and standalone Automotive OS are mutually exclusive Play app types).
    implementation("androidx.car.app:app:1.4.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Local storage for favorites + watch history
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines for the render loop / DB access
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
