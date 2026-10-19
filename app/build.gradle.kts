import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "fr.luteal.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.thomasdelorge.endoluteal"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.11.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        androidResources.localeFilters += listOf("fr")
    }

    signingConfigs {
        create("release") {
            val keystoreProps = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) load(f.inputStream())
            }
            storeFile = keystoreProps.getProperty("storeFile")?.let { file(it) }
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign only when real credentials are present. Without
            // keystore.properties the release APK is left unsigned rather than
            // falling back to the debug key: F-Droid signs its own builds, and
            // a silently debug-signed release is a footgun worth failing on.
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // Room schema JSONs must reach Robolectric through the merged app
        // assets (android_merged_assets), so they ride on the debug source
        // set: MigrationTestHelper finds them in local unit tests, and
        // release builds ship no schemas.
        getByName("debug") {
            assets.directories.add("$projectDir/schemas")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        // Disables Google Play dependency metadata in the APK signing block.
        // F-Droid scanner rejects APKs containing this extra block.
        includeInApk = false
        includeInBundle = false
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Crash reporting (offline-friendly, user-consented dialog + mail).
    // acra-core 5.13.1 depends on the AutoService processor at runtime. R8 then
    // fails the release build on javax.annotation.processing.*, which Android
    // does not ship. The generated service files are already in the ACRA jars.
    implementation(libs.acra.dialog) {
        exclude(group = "com.google.auto.service", module = "auto-service")
    }
    implementation(libs.acra.mail) {
        exclude(group = "com.google.auto.service", module = "auto-service")
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Export Room schemas to app/schemas so migrations are testable and
// reviewable (see test assets wiring in android.sourceSets above).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Pass the shared folicular conformance fixtures directory to unit tests
// (ConformanceFixturesTest decodes the same golden responses the backend's
// internal/contract fixtures test validates against the spec). Vendored
// alongside the spec so a plain clone runs the full suite; override with
// -Pfolicular.conformance=/abs/path to test against an upstream working copy.
tasks.withType<Test> {
    systemProperty(
        "folicular.conformance",
        providers.gradleProperty("folicular.conformance")
            .getOrElse("$rootDir/contract/conformance")
    )

    // Opt-in end-to-end trial against a running folicular instance
    // (E2eRoundTripTest). Skipped when unset so ordinary runs stay hermetic:
    //   ./gradlew testDebugUnitTest --tests '*E2eRoundTripTest' \
    //       -Pfolicular.e2e.url=http://127.0.0.1:8099
    providers.gradleProperty("folicular.e2e.url").orNull?.let {
        systemProperty("folicular.e2e.url", it)
    }
}
