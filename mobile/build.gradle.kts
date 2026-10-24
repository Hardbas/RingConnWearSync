import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// The version lives in one place: version.properties at the repository root.
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// Release signing. The key is a file outside the repository (and outside OneDrive) and
// keystore.properties is its local, never-committed pointer: storeFile, storePassword,
// keyAlias, keyPassword. When that file is absent the release falls back to the debug key,
// which is what CI does on purpose: every runner generates its own, publishes nothing, and its
// APK is a throwaway candidate rather than a release.
//
// A published APK must carry the certificate in signing-identity.properties. That claim is
// checked by scripts/check-release-signing-identity.sh, not by this build.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "io.github.hardbas.wearsync.bridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hardbas.wearsync"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
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
            isMinifyEnabled = false
            signingConfig = if (keystorePropertiesFile.isFile) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "warning: :mobile release is signed with a throwaway debug key; " +
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
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // play-services-basement 18.4.0, pulled in by play-services-wearable, asks for
    // androidx.fragment 1.0.0, which resolves to 1.1.0. A FragmentActivity older than 1.3.0
    // overrides onActivityResult and consumes the result ComponentActivity's
    // ActivityResultRegistry is waiting for. Nothing here extends FragmentActivity today, so
    // this is a floor rather than a usage: it keeps that trap out of the graph at the cost of
    // one unused artifact.
    constraints {
        implementation(libs.androidx.fragment)
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.health.connect)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
}
