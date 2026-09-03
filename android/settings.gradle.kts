// Google's Maven is the only source of AGP and AndroidX. Nothing here can be
// built in the development sandbox — dl.google.com is unreachable from it —
// so the build of record is .github/workflows/android.yml on a GitHub runner,
// which has the SDK preinstalled. See docs/ANDROID.md.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Asr"
include(":app")
