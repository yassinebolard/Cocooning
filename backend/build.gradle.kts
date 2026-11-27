// Serveur Cocooning : Ktor sur la JVM (cahier des charges section 9.3, ADR 0004).
// Lancer en local : ./gradlew :backend:run (configuration : config/server.conf, voir l'exemple).
// Paquet à déployer : ./gradlew :backend:installDist (build/install/backend).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
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

application {
    mainClass.set("fr.cocooning.serveur.ServeurKt")
    applicationName = "cocooning-serveur"
    // 1 Go pour la machine : le tas est borné pour laisser de la place au système (section 9.3).
    applicationDefaultJvmArgs = listOf("-Xmx384m", "-XX:+UseSerialGC")
}

tasks.named<JavaExec>("run") {
    // Lancé depuis la racine du dépôt pour trouver config/server.conf.
    workingDir = rootDir
}

dependencies {
    implementation(project(":core:protocole"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
