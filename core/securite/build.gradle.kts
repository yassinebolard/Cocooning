// Module :core:securite : clés protégées par l'Android Keystore et bases locales chiffrées
// avec SQLCipher, préférences chiffrées (ENF-SEC-02, ENF-SEC-04). Partagé par tous les modules qui stockent des données.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.cocooning.core.securite"
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

dependencies {
    api(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    // Argon2id seulement (dérivation de la clé de sauvegarde depuis la phrase de récupération).
    implementation(libs.bouncycastle.bcprov)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
