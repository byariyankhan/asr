plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.joinasr.app"
    compileSdk = 35

    defaultConfig {
        // Fixed for the life of the app: Play will not let a published
        // package name change, and Play Billing verification on the server
        // checks purchases against exactly this string (PLAY_PACKAGE_NAME).
        applicationId = "io.joinasr.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A fixed debug key, tracked in the repository. Without it every CI run
    // generates its own, every APK is signed by a different certificate, and
    // installing a new build over the last one fails with
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE -- which means uninstalling the app,
    // and losing the pact, before every single test.
    //
    // Committing it is safe and is what the well-known Android debug
    // password is for: it signs debug builds only, Play refuses an APK
    // signed with it, and it grants nothing to anybody who takes it. The
    // release key is a different key that is not in this repository and never
    // will be.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // The API base URL is a BuildConfig field, never a literal in Kotlin, so
    // a debug build can be pointed at a laptop without editing source.
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://api.joinasr.io\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.joinasr.io\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Both only for the block-screen overlay: a ComposeView in a window the
    // system owns has to be given the lifecycle, ViewModel store and
    // saved-state registry it would otherwise inherit from an activity.
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
