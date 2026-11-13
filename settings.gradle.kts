pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "VeritasReaderAndroid"
include(":app")
include(":menu_translation")
project(":menu_translation").projectDir = file("menu-translation")
include(":veritas-pc")

