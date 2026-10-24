import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// The version lives in one place: version.properties at the repository root.
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// Release signing. The key is a file outside the repository, and the properties file that points
// at it is never committed: storeFile, storePassword, keyAlias, keyPassword.
//
// Two locations are accepted, in this order:
//   1. keystore.properties at the repository root
//   2. ~/.android/wearsync-keystore.properties
//
// The second is what scripts/rewrap-release-key.sh writes, and it is the one to prefer: this
// repository usually lives inside a synced folder, and a password written inside it would be
// uploaded by the sync client. Git ignores the file; a sync client does not.
//
// With neither file present the release falls back to the debug key, which is what CI does on
// purpose: every runner generates its own, publishes nothing, and its APK is a throwaway
// candidate rather than a release. A published APK must carry the certificate in
// signing-identity.properties; scripts/check-release-signing-identity.sh is what checks it.
val keystorePropertiesFile = sequenceOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), ".android/wearsync-keystore.properties")
).firstOrNull { it.isFile }
val keystoreProperties = Properties().apply {
    keystorePropertiesFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.hardbas.wearsync.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hardbas.wearsync"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
    }

    signingConfigs {
        if (keystorePropertiesFile != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 25.3 MB of the 26.4 MB release APK was DEX: Compose, Wear Compose, ProtoLayout and
            // the Tiles runtime, unshrunk. A watch has little storage and the user sideloads every
            // update by hand over ADB, so the size is paid on the wrist, not on a build machine.
            // Nothing here needs a keep rule that the libraries do not already ship; if a build
            // ever does, wear/proguard-rules.pro says what to write and what to check.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystorePropertiesFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "warning: :wear release is signed with a throwaway debug key; " +
                        "keystore.properties was not found, so this APK is a candidate, not a release"
                )
                signingConfigs.getByName("debug")
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
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.wear)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.wear.tiles.tooling.preview)
    debugImplementation(libs.wear.tiles.tooling)
    debugImplementation(libs.wear.tiles.renderer)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material)
    implementation(libs.protolayout.expression)
    implementation(libs.guava)
    implementation(libs.concurrent.futures)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.wear.watchface.complications.data.source)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
}
