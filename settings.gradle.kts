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
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://packages.matrix.org/maven/") }
        maven { url = uri("https://repo.repsy.io/mvn/chachako/r8") }
    }
}

rootProject.name = "MatrixMessenger"
include(":app")
