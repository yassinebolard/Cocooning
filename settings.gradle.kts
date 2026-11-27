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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Cocooning"
include(":app")
include(":core:designsystem")
include(":core:cycle")
include(":core:securite")
include(":feature:cycle")
include(":feature:accueil")
 
// Prototype S.O.S jetable du jalon J3 (voir docs/prototype-sos.md).
include(":prototype:sos")
