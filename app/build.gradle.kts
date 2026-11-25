plugins {
    id("com.android.application")
}

android {
    namespace = "id.nyieuncv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.nyieuncv.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Diisi dari environment variable bila keystore rilis tersedia (lihat README).
        val ksPath = System.getenv("KEYSTORE_FILE")
        if (ksPath != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Tanpa keystore rilis, APK ditandatangani dengan debug key agar tetap bisa dipasang.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
