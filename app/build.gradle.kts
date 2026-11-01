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

    // Two editions share the package name, so the paid Pro build installs over the free one
    // and keeps the saves. Pro's versionCode is offset by 100000: a free RuStore update can
    // never downgrade (and silently de-Premium) an installed Pro build. Bump both numbers
    // together: 1.0.0 -> free 1 / pro 100001, 1.1.0 -> free 2 / pro 100002, and so on.
    flavorDimensions += "edition"
    productFlavors {
        create("free") {
            dimension = "edition"
            buildConfigField("boolean", "PREMIUM", "false")
            buildConfigField("String", "PURCHASE_URL", "\"\"")
        }
        create("pro") {
            dimension = "edition"
            versionCode = 100_001
            buildConfigField("boolean", "PREMIUM", "true")
            buildConfigField(
                "String",
                "PURCHASE_URL",
                "\"https://github.com/Granit1846/DeepSweep/blob/main/docs/PRO.md\"",
            )
        }
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
            buildConfigField("boolean", "CHEATS", "true")
        }
        // store build (RuStore, free) and the Pro build: no cheats, R8 strips their code and texts
        release {
            resValue("string", "app_name", "Глубинный сапёр")
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
