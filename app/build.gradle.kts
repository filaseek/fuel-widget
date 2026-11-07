plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.fuelwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.fuelwidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // A fixed key kept in the repo, so each new build installs as an update over the previous one.
    // Fine for a personal sideloaded app; don't reuse it for anything published.
    signingConfigs {
        create("personal") {
            storeFile = file("fuelwidget.keystore")
            storePassword = "fuelwidget"
            keyAlias = "fuelwidget"
            keyPassword = "fuelwidget"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// No libraries needed — just the Android framework and the Kotlin standard library.
dependencies { }
