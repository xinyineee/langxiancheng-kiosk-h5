plugins {
    id("com.android.application")
}

android {
    namespace = "com.langxiancheng.kiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.langxiancheng.kiosk"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.2.0"
    }

    buildTypes {
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core:1.15.0")
}
