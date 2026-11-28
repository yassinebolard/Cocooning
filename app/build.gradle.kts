import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Configuration de l'appli (ENF-CONF-01) : config/cocooning.properties s'il existe, sinon le modèle.
val configCocooning = Properties().apply {
    val dossier = rootProject.file("config")
    val fichier = dossier.resolve("cocooning.properties").takeIf { it.isFile } ?: dossier.resolve("cocooning.properties.example")
    fichier.inputStream().use { load(it) }
}

android {
    namespace = "com.agdc.cocooning"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.agdc.cocooning"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SERVEUR", "\"${configCocooning.getProperty("serveur.adresse")}\"")

        // libsignal : seulement les téléphones réels (arm64) et l'émulateur (x86_64), ADR 0003.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Exigé par libsignal-android.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // Variante de test de libsignal et bibliothèques de bureau du jar libsignal-client : jamais dans l'APK.
        jniLibs { excludes += "**/libsignal_jni_testing.so" }
        resources { excludes += listOf("libsignal_jni*.dylib", "signal_jni*.dll", "libsignal_jni*.so") }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(project(":core:designsystem"))
    implementation(project(":feature:accueil"))
    implementation(project(":feature:cycle"))
    implementation(project(":feature:sauvegarde"))
    implementation(project(":feature:messages"))
    implementation(project(":core:reseau"))
    implementation(project(":core:messagerie"))
    implementation(project(":core:chiffrement"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:cycle"))
    implementation(project(":core:securite"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}