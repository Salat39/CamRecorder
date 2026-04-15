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
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CamRecorder"
include(":app")
include(":core")
include(":core:base")
include(":core:commonConst")
include(":core:coroutines")
include(":core:resources")
include(":core:navigation")
include(":core:uikit")
include(":core:ui")
include(":core:ecarxFw")
include(":core:ecarxCar")
include(":core:adaptapi")
include(":core:carApi")
include(":core:recorder")
include(":core:driveStorage")
include(":core:preferences")
include(":core:sharedEvents")
include(":feature")
include(":feature:preview")
include(":feature:settings")
include(":feature:archive")
include(":baselineprofile")
