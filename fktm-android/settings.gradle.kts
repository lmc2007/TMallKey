pluginManagement {
    repositories {
        // Use upstream repositories on GitHub-hosted CI to avoid regional mirror outages.
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven { url = uri("https://jitpack.io") }
            }
            filter { includeModule("com.github.aptabase", "aptabase-kotlin") }
        }
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "fkTM"
include(":app")
