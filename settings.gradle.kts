rootProject.name = "MyApplication"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Contract library (DTOs, paths, credential rules) shared with the backend.
        // Credentials come from the kmpDemoBackendUsername / kmpDemoBackendPassword
        // Gradle properties (see docs/backend.md); no other dependency is asked here.
        maven("https://maven.pkg.github.com/alianikaydin/KmpDemoBackend") {
            name = "kmpDemoBackend"
            credentials(PasswordCredentials::class)
            content { includeGroup("com.anksoft.kmpdemo") }
        }
    }
}

include(":androidApp")
include(":shared")
include(":webApp")