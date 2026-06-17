import java.util.Base64

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.oak.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.oak.app"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode =
            libs.versions.android.versionCode
                .get()
                .toInt()
        versionName = libs.versions.appVersion.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            val releaseKeystore = rootProject.file("release.keystore")
            if (!releaseKeystore.exists()) {
                val b64 = System.getenv("KEYSTORE_B64")
                if (b64 != null) {
                    releaseKeystore.writeBytes(Base64.getDecoder().decode(b64))
                }
            }
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            }
        }

        getByName("debug") {
            val debugKeystore = rootProject.file("debug.keystore")
            if (!debugKeystore.exists()) {
                val b64 = System.getenv("DEBUG_KEYSTORE_BASE64")
                if (b64 != null) {
                    debugKeystore.writeBytes(Base64.getDecoder().decode(b64))
                }
            }
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "../composeApp/proguard-rules.pro",
                "proguard-android.pro"
            )
            signingConfig =
                if (rootProject.file("release.keystore").exists()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.foundation.android)
    implementation(libs.compose.material3)
    implementation(libs.koin.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.filekit.core)
    implementation(libs.filekit.compose)
    implementation(libs.tts)
    implementation(libs.tts.compose)
    implementation(libs.compose.components.uiToolingPreview)
    debugImplementation(libs.compose.ui.tooling)
}
