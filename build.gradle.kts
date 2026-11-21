// AGP 9 compiles Kotlin itself and refuses the `org.jetbrains.kotlin.android` plugin.
// It takes the compiler from the kotlin-gradle-plugin jar on this classpath, so this
// is where the Kotlin version is pinned. Code On The Go injects the on-device Maven
// repo here so it resolves offline.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        // Push (Firebase): o plugin só entra no build se você colocou app/google-services.json
        if (file("app/google-services.json").exists()) {
            classpath("com.google.gms:google-services:4.4.3")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false   
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
