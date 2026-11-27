import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The Play upload key lives outside the repo (docs/releases.md). Without it, release builds stay unsigned.
val uploadKeyProperties =
    file(
        providers
            .environmentVariable("TALETIME_UPLOAD_KEY_PROPERTIES")
            .getOrElse("${System.getProperty("user.home")}/.googleplay/taletime-upload.properties"),
    ).takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "com.nicolasguillen.taletime"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.nicolasguillen.taletime"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        // fastlane passes both: versionCode = highest on Google Play + 1, versionName = iOS MARKETING_VERSION.
        versionCode = providers.gradleProperty("taletime.versionCode").getOrElse("1").toInt()
        versionName = providers.gradleProperty("taletime.versionName").getOrElse("1.0")
    }
    buildFeatures {
        compose = true
    }
    signingConfigs {
        if (uploadKeyProperties != null) {
            create("upload") {
                storeFile = file(uploadKeyProperties.getProperty("storeFile"))
                storePassword = uploadKeyProperties.getProperty("storePassword")
                keyAlias = uploadKeyProperties.getProperty("keyAlias")
                keyPassword = uploadKeyProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
