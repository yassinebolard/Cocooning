// Module :core:designsystem de Cocooning.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.cocooning.designsystem"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Material 3 1.3 ou plus récent est nécessaire (OutlinedTextFieldDefaults.Container,
    // rôles surfaceContainer*).
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    api(composeBom)
    api("androidx.compose.material3:material3")
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Carte de l'alerte S.O.S reçue : tuiles OpenStreetMap, sans services Google (compatible /e/OS).
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
