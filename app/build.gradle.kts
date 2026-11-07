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
        // Each GitHub build gets a higher number, so it installs as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
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

// The app itself needs no libraries; JUnit is only for the build-server check.
dependencies {
    testImplementation("junit:junit:4.13.2")
}

android.testOptions.unitTests.all {
    it.environment("PARSE_REPORT", rootProject.file("parse-report.txt").absolutePath)
    it.testLogging { showStandardStreams = true; events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
