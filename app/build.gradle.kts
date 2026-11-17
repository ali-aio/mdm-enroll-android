plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aioapp.enroll"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aioapp.enroll"
        minSdk = 30 // Wireless debugging (pairing codes) is Android 11+
        targetSdk = 35
        versionCode = 8
        versionName = "0.5.1"
    }

    // Three builds: live (mdm.dev), stage and prod. Different applicationIds, so they can sit
    // on one phone side by side. prod (17 Nov 2026) talks to mdm.aioapp.com, where devices are
    // moving; Lab devices stay on mdm.dev, so a phone may need both for now.
    flavorDimensions += "server"
    productFlavors {
        create("live") {
            dimension = "server"
            buildConfigField("String", "SERVER", "\"https://mdm.dev.aioapp.com\"")
        }
        create("prod") {
            dimension = "server"
            applicationIdSuffix = ".prod"
            versionNameSuffix = "-prod"
            resValue("string", "app_name", "AIO Enroll (prod)")
            buildConfigField("String", "SERVER", "\"https://mdm.aioapp.com\"")
        }
        create("stage") {
            dimension = "server"
            applicationIdSuffix = ".stage"
            versionNameSuffix = "-stage"
            resValue("string", "app_name", "AIO Enroll (stage)")
            buildConfigField("String", "SERVER", "\"https://mdm-stage.dev.aioapp.com\"")
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
    }
}

dependencies {
    val compose = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1") // FileProvider (CSV export), window insets
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // adb client: pairing (SPAKE2), TLS and plain TCP connections to other devices' adbd
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81") // self-signed certificate for the adb TLS key
}
