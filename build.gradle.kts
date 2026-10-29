buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP's built-in Kotlin pulls in an older KGP; this pins the one in the catalog.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
