pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Maven Central first: everything :core needs lives there, and only the
        // Android modules have to reach dl.google.com.
        mavenCentral()
        google()
    }
}

rootProject.name = "b1gplayer"

include(":core")
include(":app")
