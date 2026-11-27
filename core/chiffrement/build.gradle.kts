// Module :core:chiffrement : chiffrement de bout en bout des messages avec le protocole Signal
// (libsignal, AGPL-3.0) et stockage des clés dans une base SQLCipher dédiée, exclue de la
// sauvegarde (docs/adr/0003-architecture-messagerie-chiffree.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "fr.cocooning.core.chiffrement"
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
        // Exigé par libsignal-android (API java.time et autres sur les anciens Android).
        isCoreLibraryDesugaringEnabled = true
    }
}

room {
    // Schéma exporté et versionné : il servira à écrire et tester les migrations.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Les tests JVM utilisent les bibliothèques natives de bureau livrées dans libsignal-client
    // (dépendance de libsignal-android), Windows et Linux compris.
    // Aucun type libsignal ne sort du module : l'API publique n'expose que des octets.
    implementation(libs.libsignal.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.room.runtime)
    implementation(project(":core:securite"))
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
