// Module :core:protocole : ce que l'appli et le serveur échangent, en Kotlin pur (JVM), partagé
// par les deux côtés pour qu'ils ne divergent jamais (docs/adr/0003 et 0004).
// - api : requêtes et réponses JSON du serveur ;
// - contenu : le contenu en clair d'un message, en Protocol Buffers, chiffré ensuite par Signal.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.protobuf)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
