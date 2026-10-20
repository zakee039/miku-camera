plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mikucamera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zakee.mikucamera"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.7.0"
    }

    // Keep the v2 signing key stable so locally installed updates can replace
    // previous APKs. The keystore is stored in OneDrive and copied locally (ignored by Git).
    signingConfigs {
        create("stableLocal") {
            storeFile = file("miku-camera-signkey-v2.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            if (file("miku-camera-signkey-v2.keystore").exists()) {
                signingConfig = signingConfigs.getByName("stableLocal")
            } else {
                // A clean checkout has no release key. Keep this install separate
                // so testing never requires uninstalling the user's signed app.
                applicationIdSuffix = ".preview"
                resValue("string", "app_name", "miku camera · 预览")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stableLocal")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

android.applicationVariants.all {
    val outputName = "miku-camera-${versionName}.apk"
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = outputName
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
