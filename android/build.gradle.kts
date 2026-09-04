plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Applied by :app only when google-services.json is present. Declared
    // here so the plugin is on the build's classpath either way.
    alias(libs.plugins.google.services) apply false
}
