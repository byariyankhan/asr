plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Firebase is applied only when its config is here.
 *
 * google-services.json comes out of the Firebase console and is not in this
 * repository yet. The plugin fails the build outright when the file is
 * missing, which would stop CI from compiling anything at all over a feature
 * that is one file away from working — so it is applied conditionally, and
 * the app checks at runtime whether Firebase actually came up. Drop the file
 * in at app/google-services.json and push: nothing else has to change.
 */
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    // Crash reports, through the same Firebase project as push. Nothing in
    // the app said anything when it died: the block screen "not appearing on
    // some devices" was a rumour for weeks because no phone could report it.
    // The plugin uploads R8 mapping files for release builds so a stack
    // trace from a shrunk APK still names the line.
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "io.joinasr.app"
    // 36 because Play requires it of a new app since 31 August 2026, and
    // this app reaches Play after that. What Android 16 changes for apps
    // that target it is already the case here: both activities draw
    // edge-to-edge, back goes through the dispatcher everywhere (the block
    // overlay included, see enforcement/BlockOverlay.kt), and no screen
    // locks its orientation. minSdk is untouched: the target level changes
    // how new Androids treat the app, never which phones can install it.
    compileSdk = 36

    defaultConfig {
        // Fixed for the life of the app: Play will not let a published
        // package name change, and Play Billing verification on the server
        // checks purchases against exactly this string (PLAY_PACKAGE_NAME).
        applicationId = "io.joinasr.app"
        minSdk = 26
        targetSdk = 36
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
            buildConfigField("Boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.joinasr.io\"")
            buildConfigField("Boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
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
    // Carries the invitation across a Play install. A witness is by
    // definition somebody without the app, so the link they are sent lands
    // on a phone that has to install it first, and without this the code is
    // lost in that gap: they arrive at a welcome screen with no idea what
    // they were doing and have to go and find the message again.
    implementation(libs.installreferrer)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Push, which is how every witness alert arrives. Inert without
    // google-services.json: FirebaseApp does not initialise, the token is
    // never fetched, and Push.kt reports that rather than crashing.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Inert without google-services.json, like messaging: FirebaseApp never
    // initialises and diagnostics/Crash.kt checks before touching it.
    implementation(libs.firebase.crashlytics)
    // Ten product events and nothing else (analytics/Analytics.kt): whether
    // people sign up, start challenges, invite witnesses, and finish or
    // break them. Never an app name, a minute, a name or an address; the
    // advertising id is switched off in the manifest. Inert without
    // google-services.json, like the two above.
    implementation(libs.firebase.analytics)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
