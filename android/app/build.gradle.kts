import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lastpenguin.pix"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.lastpenguin.pix"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Iteration 1: the laptop's address on the test Wi-Fi (#4). 10.0.2.2 is the host machine from the emulator.
        buildConfigField("String", "SERVER_URL", "\"http://10.0.2.2:8000/\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all {
            // Print why a test failed, so a CI failure can be read from its log.
            it.testLogging.exceptionFormat = TestExceptionFormat.FULL
        }
    }
    lint {
        // Pix runs on phones only. This check wants the camera marked optional for ChromeOS, but Pix needs it.
        disable += "PermissionImpliesUnsupportedChromeOsHardware"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Needed by the shared contracts (interfaces, models, messages, API).
    implementation(libs.androidx.camera.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    // App shell: one activity, Navigation, Fragments with ViewModels (#2).
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.retrofit.kotlinx.serialization)

    // Other libraries from Design 1.2 are declared in gradle/libs.versions.toml.
    // Add them here when your module starts using them.

    testImplementation(libs.junit)
}
