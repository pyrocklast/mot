import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing: on GitHub from the MOT_* environment variables set by the Release workflow,
// on a PC from keystore.properties in the project root (never committed). With neither, the
// release APK is built unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signing(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotEmpty() } ?: keystoreProperties.getProperty(property)

val releaseStoreFile = signing("MOT_STORE_FILE", "storeFile")

android {
    namespace = "io.github.pyrocklast.mot"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.pyrocklast.mot"
        // Android 15: Private Space, per-app languages and edge-to-edge are all standard
        minSdk = 35
        targetSdk = 36
        // The Release workflow numbers each build, so every release installs as an update
        versionCode = (project.findProperty("motVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("motVersionName") as String?) ?: "0.1-dev"
    }

    // English and French only, for the app and for the libraries' own strings
    androidResources {
        localeFilters += listOf("en", "fr")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signing("MOT_STORE_PASSWORD", "storePassword")
                keyAlias = signing("MOT_KEY_ALIAS", "keyAlias")
                keyPassword = signing("MOT_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // No Google-only dependency metadata in the APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkDependencies = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
}
