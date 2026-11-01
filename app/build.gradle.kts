import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// release key: keystore.properties next to the project root (not shipped, back it up)
val keyProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.skeorz.lab.deepsweep"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.skeorz.lab.deepsweep"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // test build: Premium and cheats, installs next to the store version
        debug {
            applicationIdSuffix = ".test"
            resValue("string", "app_name", "Глубинный сапёр (тест)")
            buildConfigField("boolean", "PREMIUM", "true")
            buildConfigField("boolean", "CHEATS", "true")
        }
        // store build (RuStore): no Premium, no cheats — R8 strips their code and texts
        release {
            resValue("string", "app_name", "Глубинный сапёр")
            buildConfigField("boolean", "PREMIUM", "false")
            buildConfigField("boolean", "CHEATS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
