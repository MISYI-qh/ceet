plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.ceet.browser"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.ceet.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    // Kunci debug tetap (sengaja publik) supaya pembaruan APK debug bisa dipasang menimpa versi sebelumnya.
    signingConfigs {
        getByName("debug") {
            storeFile = file("ceet-debug.keystore")
            storePassword = "android"
            keyAlias = "ceetdebug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    sourceSets {
        // Daftar pelacak dipakai bersama mesin Rust dan desktop. Satu sumber.
        getByName("main").assets.srcDir("../../data")
        // Vektor emas dari mesin Rust dipakai sebagai tes.
        getByName("test").resources.srcDir("../../shared/vectors")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.webkit:webkit:1.12.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
