// Module :core:messagerie : règles des messages (pures, sans Android) et historique local Room
// chiffré par SQLCipher. Le chiffrement de bout en bout et le transport viendront ensuite
// (docs/adr/0003-architecture-messagerie-chiffree.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "fr.cocooning.core.messagerie"
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
    }
}

room {
    // Schéma exporté et versionné : il servira à écrire et tester les migrations.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(project(":core:securite"))
    ksp(libs.androidx.room.compiler)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Stockage Room réel, sur téléphone ou émulateur : :core:messagerie:connectedDebugAndroidTest.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
