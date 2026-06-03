plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.langxiancheng.kiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.langxiancheng.kiosk"
        minSdk = 21
        targetSdk = 35
        versionCode = 12
        versionName = "3.3.0"
    }

    signingConfigs {
        getByName("debug") {
            val keystoreFile = file("/tmp/android-keystore/debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            // If custom keystore doesn't exist (e.g. CI), Android default debug keystore is used
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")

    // OkHttp — for iFlytek IAT WebSocket ASR
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
