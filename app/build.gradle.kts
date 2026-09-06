plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.music.spotui"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.music.spotui"
        minSdk = 26
        targetSdk = 36
        versionCode = 202610105
        versionName = "2.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val geminiKey = (project.findProperty("GEMINI_API_KEY") as? String)
            ?: System.getenv("GEMINI_API_KEY")
            ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    signingConfigs {
        create("sharedDebug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign release with the shared debug key so the APK is installable via sideload
            // and upgrades the existing (debug-signed) install in place.
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Spotify metadata + YouTube streaming, ported from Meld (replaces Firebase data layer)
    implementation(project(":spotify"))
    implementation(project(":innertube"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    // Hilt + Room persistence
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    //coroutines
    implementation(libs.kotlinx.coroutines.android)

    //await
    implementation(libs.kotlinx.coroutines.play.services)

    //glide
    implementation(libs.compose)
    implementation(libs.glide)

    //splashScreen
    implementation(libs.androidx.core.splashscreen)

    //palette
    implementation(libs.androidx.palette)

    //exoplayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    // PlayerView for the Spotify Canvas looping video on the now-playing screen.
    implementation(libs.androidx.media3.ui)
    // media session + system media notification (lock screen / notification center)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.workmanager)
    implementation(libs.androidx.work.runtime.ktx)

    //okhttp + timber (used by the ported YouTube streaming flow)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)

    // ML Kit on-device translation & language identification (free, no API key, works offline after model download)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    //core library desugaring (required by :innertube)
    coreLibraryDesugaring(libs.desugaring)
}
