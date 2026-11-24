plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionName: String = (project.findProperty("APP_VERSION_NAME") as String?) ?: "1.0.0"
val appVersionCode: Int = (project.findProperty("APP_VERSION_CODE") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.franzney.omnigrab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.franzney.omnigrab"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: "omnigrab123"
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "omnigrab"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: "omnigrab123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Compose UI + Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Imágenes (miniaturas de vídeos)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Red (GitHub Releases Auto-Updater)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Motor multimedia local: Python + yt-dlp + FFmpeg (ARM64/ARMv7/x86_64)
    val youtubedlAndroid = "0.17.2"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
}
