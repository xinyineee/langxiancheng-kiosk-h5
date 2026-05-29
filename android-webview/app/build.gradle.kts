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
        versionCode = 7
        versionName = "2.6.2"
    }

    buildTypes {
        debug { isDebuggable = true; isMinifyEnabled = false }
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

    // SUNMI AI Base SDK
    implementation(files("libs/sm_base_sdk.aar"))
    implementation(files("libs/sm_main_framework_aidl.aar"))
    implementation(files("libs/sm_main_framework_sdk.aar"))

    // SUNMI Voice SDK
    implementation(files("libs/sm_asr_aidl.aar"))
    implementation(files("libs/sm_asr_sdk.aar"))
}
