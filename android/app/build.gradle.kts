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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    // Other libraries from Design 1.2 are declared in gradle/libs.versions.toml.
    // Add them here when your module starts using them.

    testImplementation(libs.junit)
}
