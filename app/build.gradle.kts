// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so there is no
// org.jetbrains.kotlin.android plugin here — only the Compose compiler plugin.
// KSP runs Room's code generator (like a Swift macro, but as a build step).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hayat.lumen"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hayat.lumen"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.0"
    }

    signingConfigs {
        // A shared debug key checked into the repo, so an APK built in a cloud
        // session and one built on your laptop can be installed over each other.
        // (Android refuses an update signed with a different key, which would
        // force an uninstall and wipe your playlists/favourites.)
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal sideload build: signed with the same key until we set up a real one.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Lets plain JVM unit tests call android.util.Log etc. without crashing.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // TV banners conventionally ship as a single xhdpi bitmap.
        disable += "IconMissingDensityFolder"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}

ksp {
    // Room writes its schema JSON here so future migrations can be checked against it.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
