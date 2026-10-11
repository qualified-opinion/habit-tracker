plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.janreins.habitude"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.janreins.habitude"
        minSdk = 26
        targetSdk = 35
        // The release workflow passes HABIT_VERSION_CODE (100 + run number, so always above the
        // old hard-coded 11) and HABIT_VERSION_NAME (the tag without its "v", or 1.1.<run>).
        // Local builds keep 11 / 1.1.0.
        versionCode = providers.environmentVariable("HABIT_VERSION_CODE").orNull?.let {
            it.toIntOrNull() ?: throw GradleException("HABIT_VERSION_CODE must be a whole number, got '$it'.")
        } ?: 11
        versionName = providers.environmentVariable("HABIT_VERSION_NAME").orNull?.takeIf { it.isNotBlank() } ?: "1.1.0"
    }

    signingConfigs {
        // A fixed debug key kept in the repo, so every build is signed the same way and a new
        // APK installs over the old one (keeping your data) instead of needing an uninstall.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release key from environment variables (set by .github/workflows/release.yml from the
        // repository secrets, or by hand). There is deliberately no fallback to the debug key:
        // a release APK signed with anything else can't be installed over the previous release.
        // If the variables are missing, checkReleaseSigning (below) stops any release packaging
        // task with a clear message. Debug builds, tests and lint don't need them.
        create("release") {
            storeFile = providers.environmentVariable("HABIT_KEYSTORE_FILE").orNull
                ?.takeIf { it.isNotBlank() }?.let { file(it) }
            storePassword = providers.environmentVariable("HABIT_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("HABIT_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("HABIT_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // MigrationTestHelper reads the exported schemas from assets. Under Robolectric, unit tests
    // see the debug variant's merged assets rather than the test source set's, so the schemas
    // are added to debug assets (a few KB in debug APKs only; release builds don't include them).
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Fails release packaging (assembleRelease, bundleRelease, ...) when the release key isn't
// configured. The check runs at execution time, so configuring the project, debug builds,
// unit tests and lint all work without the variables.
val releaseSigningVars = listOf(
    "HABIT_KEYSTORE_FILE",
    "HABIT_KEYSTORE_PASSWORD",
    "HABIT_KEY_ALIAS",
    "HABIT_KEY_PASSWORD",
)
val releaseSigningEnv = releaseSigningVars.associateWith { providers.environmentVariable(it) }
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    group = "verification"
    description = "Fails unless the HABIT_* release signing environment variables are set."
    doLast {
        val missing = releaseSigningEnv.filterValues { it.orNull.isNullOrBlank() }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured: missing ${missing.joinToString()}. " +
                    "Set ${releaseSigningVars.joinToString()} to sign the release build " +
                    "(release builds are never signed with the debug key)."
            )
        }
        val keystore = File(releaseSigningEnv.getValue("HABIT_KEYSTORE_FILE").get())
        if (!keystore.isFile) {
            throw GradleException("Release signing is not configured: HABIT_KEYSTORE_FILE points to '$keystore', which doesn't exist.")
        }
    }
}
tasks.matching {
    it.name in setOf("validateSigningRelease", "packageRelease", "signReleaseBundle", "packageReleaseBundle")
}.configureEach {
    dependsOn(checkReleaseSigning)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
