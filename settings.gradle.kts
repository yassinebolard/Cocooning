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
        // libsignal n'est publiée que sur le dépôt de Signal (ADR 0003). Ce dépôt ne sert qu'au
        // groupe org.signal, et ce groupe ne vient que de lui.
        exclusiveContent {
            forRepository { maven("https://build-artifacts.signal.org/libraries/maven/") }
            filter { includeGroup("org.signal") }
        }
    }
}

rootProject.name = "Cocooning"
include(":app")
include(":core:designsystem")
include(":core:cycle")
include(":core:securite")
include(":core:messagerie")
include(":core:chiffrement")
include(":core:protocole")
include(":core:reseau")
include(":feature:cycle")
include(":feature:accueil")
include(":feature:sauvegarde")
include(":feature:messages")

// Serveur Cocooning (Ktor sur la JVM), déployé sur le Proxmox (cahier des charges section 9.3).
include(":backend")
 
// Prototype S.O.S jetable du jalon J3 (voir docs/prototype-sos.md).
include(":prototype:sos")
