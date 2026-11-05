import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.useconatus.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.useconatus.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        // Launcher names. Test builds get short ones that say what they are.
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // Two keys, both from keystore.properties beside this project (never committed). The upload
    // key signs the bundle for Play. The release key signs the APKs attached to GitHub releases,
    // which are built with -PgithubApk. Without a key, release builds come out unsigned.
    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    signingConfigs {
        if (keys != null) {
            create("upload") {
                storeFile = file(keys.getProperty("storeFile"))
                storePassword = keys.getProperty("storePassword")
                keyAlias = keys.getProperty("keyAlias")
                keyPassword = keys.getProperty("keyPassword")
            }
            if (keys.containsKey("releaseStoreFile")) {
                create("release") {
                    storeFile = file(keys.getProperty("releaseStoreFile"))
                    storePassword = keys.getProperty("releaseStorePassword")
                    keyAlias = keys.getProperty("releaseKeyAlias")
                    keyPassword = keys.getProperty("releaseKeyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (providers.gradleProperty("githubApk").isPresent) signingConfigs.getByName("release")
                else signingConfigs.findByName("upload")
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Debug"
        }
        // Debug builds are debuggable, so ART skips most optimisations and Compose stutters.
        // Preview is the debug app built like a release (R8, not debuggable) for judging how
        // the UI feels on a phone. It installs next to debug and release.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["appLabel"] = "Preview"
        }
    }

    // Shared code lives in src/main. A flavor's own source set holds only what differs, which so
    // far is how instant updates arrive.
    flavorDimensions += "distribution"
    productFlavors {
        // Google Play: instant updates through Firebase Cloud Messaging and push-relay/.
        create("play") { dimension = "distribution" }
        // No Google code, for F-Droid. It syncs on open, after each change and every hour.
        create("foss") { dimension = "distribution" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// google-services.json sits in src/play, so foss builds have none, and neither do play builds
// without the Firebase project's file. Those run without instant updates (see push/Push.kt).
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
}

room {
    // Exported so every schema change gets a reviewed migration.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
