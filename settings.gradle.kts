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
        // klinker/android-smsmms (사진 첨부 MMS 직접 발송용). jitpack 호스팅.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CallFollowCRM"
include(":app")