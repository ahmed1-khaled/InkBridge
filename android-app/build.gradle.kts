plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val inkbridgeCode = providers.gradleProperty("inkbridgeVersionCode").orNull?.toIntOrNull() ?: 2
val inkbridgeName = providers.gradleProperty("inkbridgeVersionName").orNull ?: "0.2.0"

android {
    namespace = "com.inkbridge.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.inkbridge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = inkbridgeCode
        versionName = inkbridgeName
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("INKBRIDGE_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("INKBRIDGE_STORE_PASSWORD")
                keyAlias = System.getenv("INKBRIDGE_KEY_ALIAS")
                keyPassword = System.getenv("INKBRIDGE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.19.1")
}
