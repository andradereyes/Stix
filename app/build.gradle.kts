plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "cl.andradereyes.stix"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.andradereyes.stix"
        minSdk = 30
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Llave fija para que cada APK nuevo se instale encima del anterior. No está en el repositorio:
    // GitHub Actions la crea desde los secretos KEYSTORE_BASE64 y KEYSTORE_PASSWORD. Sin ella se usa
    // la llave de depuración.
    val releaseKeystore = rootProject.file("keystore/release.jks")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        if (releaseKeystore.exists() && !keystorePassword.isNullOrEmpty()) create("stix") {
            storeFile = releaseKeystore
            storePassword = keystorePassword
            keyAlias = "stix"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("stix") ?: signingConfigs.getByName("debug")
        }
        debug {
            signingConfigs.findByName("stix")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
