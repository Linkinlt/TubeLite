plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val extractorVersion: String = (project.findProperty("extractorVersion") as String?) ?: "v0.27.0"
val appVersionCode: Int = ((project.findProperty("appVersionCode") as String?) ?: "1").toInt()

android {
    namespace = "com.linkinlt.tubelite"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.google.android.youtube.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"
        // Chỉ ARM (TV box/máy chiếu), bỏ x86 cho nhẹ
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        resourceConfigurations += listOf("vi", "en")
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("tubelite.jks")
            storePassword = "tubelite123"
            keyAlias = "tubelite"
            keyPassword = "tubelite123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += listOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.version")
        }
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:$extractorVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.annotation:annotation:1.9.1")
    // ExoPlayer 2.19 (chạy được Android 5)
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-dash:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
}
