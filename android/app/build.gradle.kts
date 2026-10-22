// AI-generated with ChatGPT Codex and Claude Code, 2026-10-22, reviewed by Joonhyung Han
import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

// The Pix server (Design 2.8, Hosting and Configuration). Release builds use Server A, which runs main.
// Debug builds use `pix.serverUrl` from local.properties (never committed) or -Ppix.serverUrl=...: Server B, which
// runs dev (`http://swpp26t10.147-46-242-174.sslip.io/`), or a laptop on the test Wi-Fi
// (`http://192.168.0.10:8000/`). Their default, 10.0.2.2, is the host machine as seen from the emulator.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val debugServerUrl: String = providers.gradleProperty("pix.serverUrl").orNull
    ?: localProperties.getProperty("pix.serverUrl")
    ?: "http://10.0.2.2:8000/"
val releaseServerUrl = "http://swpp26t10.147-46-242-169.sslip.io/"

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
        debug {
            buildConfigField("String", "SERVER_URL", "\"$debugServerUrl\"")
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"$releaseServerUrl\"")
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
        // Android framework stubs (Log, SystemClock) return defaults in JVM tests instead of throwing.
        unitTests.isReturnDefaultValues = true
        // Robolectric tests (View, Canvas, touch, Material Slider) need the app's themes and resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Print why a test failed, so a CI failure can be read from its log.
            it.testLogging.exceptionFormat = TestExceptionFormat.FULL
            // Robolectric reads FileDescriptor internals, which Java 17+ modules hide by default.
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            // Coverage (Testing Documentation 2.1.4): Robolectric loads classes without a source location,
            // and the JDK's internals must stay out of the agent, or the report is empty or the run fails.
            it.extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
    lint {
        // Pix runs on phones only. This check wants the camera marked optional for ChromeOS, but Pix needs it.
        disable += "PermissionImpliesUnsupportedChromeOsHardware"
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Line and branch coverage of the JVM and Robolectric tests (Testing Documentation 2.1.2–2.1.4):
// `./gradlew :app:jacocoTestReport`, then app/build/reports/jacoco/jacocoTestReport/html/index.html.
// Excluded (2.1.3): generated code (BuildConfig, R, ViewBinding), and the hand-written fakes that stand in for the
// module interfaces in tests. The debug source set is not compiled into the debug app classes used here.
val coverageExclusions = listOf(
    "**/BuildConfig.*",
    "**/R.class",
    "**/R$*.class",
    "**/databinding/**",
    "**/*Binding.class",
    "**/*Binding$*.class",
    "**/*Fake*.*",
)
tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Line and branch coverage of the debug unit tests."
    dependsOn("testDebugUnitTest")
    val buildDir = layout.buildDirectory
    classDirectories.setFrom(
        files(
            buildDir.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
            buildDir.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        ).map { dir -> fileTree(dir) { exclude(coverageExclusions) } },
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(buildDir.file("jacoco/testDebugUnitTest.exec"))
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Needed by the shared contracts (interfaces, models, messages, API).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    // App shell: one activity, Navigation, Fragments with ViewModels (#2).
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.retrofit.kotlinx.serialization)

    // Real-time session (#8): WebRTC, and lifecycle-aware flow collection in Fragments.
    implementation(libs.webrtc)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.process)

    // Reference guide (#5): on-device person segmentation, model from Google Play services.
    implementation(libs.mlkit.subject.segmentation)

    // Pose generation (#7): the remembered consent (FR-4.2), and candidate cells that keep the frame's 3:4 ratio.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.constraintlayout)

    // Other libraries from Design 1.2 are declared in gradle/libs.versions.toml.
    // Add them here when your module starts using them.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    // Screen tests: FragmentScenario launches a Fragment in a debug-only activity under Robolectric.
    testImplementation(libs.androidx.fragment.testing)
    debugImplementation(libs.androidx.fragment.testing.manifest)
}
