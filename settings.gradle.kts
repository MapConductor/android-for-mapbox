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
                username = System.getenv("GPR_USER")
                password = System.getenv("GPR_TOKEN")
            }
            content { includeGroup("com.mapconductor") }
        }
    }
}

rootProject.name = "mapconductor-for-mapbox"
