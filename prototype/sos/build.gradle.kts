// Prototype S.O.S jetable (jalon J3) : prouve la réception d'une alerte en arrière-plan,
// la sonnerie malgré le silencieux et Ne pas déranger, la position GPS et le repli SMS.
// Application séparée (identifiant distinct) : ce module se supprime d'un bloc après le J3.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Configuration Firebase facultative, lue dans local.properties (jamais versionnée).
// Sans elle, le prototype compile et fonctionne avec la seule voie ntfy.
val proprietesLocales = Properties().apply {
    val fichier = rootProject.file("local.properties")
    if (fichier.exists()) fichier.inputStream().use { load(it) }
}
fun firebase(cle: String): String = "\"" + proprietesLocales.getProperty("prototypeSos.firebase.$cle", "") + "\""

android {
    namespace = "fr.cocooning.prototype.sos"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.cocooning.prototype.sos"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-prototype"

        buildConfigField("String", "FIREBASE_API_KEY", firebase("apiKey"))
        buildConfigField("String", "FIREBASE_APP_ID", firebase("appId"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebase("projectId"))
        buildConfigField("String", "FIREBASE_SENDER_ID", firebase("senderId"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
