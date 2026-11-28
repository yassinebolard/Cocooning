// Module :core:reseau : transport entre l'appli et le serveur Cocooning (ADR 0004).
// Clé d'appareil dans l'Android Keystore, client HTTP Ktor, inscription, publication des clés
// Signal, envoi de la file hors ligne et relève chiffrée.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.cocooning.core.reseau"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Exigé par libsignal-android, que :core:chiffrement apporte.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:protocole"))
    api(project(":core:messagerie"))
    api(project(":core:chiffrement"))
    implementation(project(":core:securite"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
