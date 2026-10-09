// AI-generated with Claude Code, 2026-10-01, reviewed by Dongje Park
// Top-level build file. Module settings live in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// Formatting and style for every module. Rules are in .editorconfig.
// Check with `./gradlew spotlessCheck`, fix with `./gradlew spotlessApply`.
spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
