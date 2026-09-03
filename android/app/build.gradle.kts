plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
