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
        maven {
            // Mapbox Maven repository
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
        }
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/mapconductor/android-for-mapbox")
            credentials {
                username = System.getenv("GPR_USER") ?: ""
                password = System.getenv("GPR_TOKEN") ?: ""
            }
        }
        maven {
            name = "GithubPackages-core"
            url = uri("https://maven.pkg.github.com/MapConductor/android-sdk-core")
            credentials {
                username = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "android-for-mapbox"
include(":sample-app")
