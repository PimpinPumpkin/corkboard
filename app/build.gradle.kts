import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Cronet is Chromium's own network stack: requests made through it have Chrome's TLS and HTTP/2
// handshake, not OkHttp's. Maven's cronet-embedded stopped at 143, so this is Chromium's prebuilt
// Release build for the pinned Chrome for Android version, packed into one AAR by Vela's
// cronet-runtime workflow. At 15 MB it does not belong in git: fetch it once, at configuration
// time, so a fresh clone builds with a plain `./gradlew assembleDebug`.
val cronetVersion: String = providers.gradleProperty("corkboard.cronetVersion").get()
val cronetAar = layout.projectDirectory.file("libs/cronet-$cronetVersion.aar").asFile
if (!cronetAar.exists()) {
    cronetAar.parentFile.mkdirs()
    val url = "https://github.com/PimpinPumpkin/Vela/releases/download/cronet-runtime/cronet-$cronetVersion.aar"
    logger.lifecycle("Downloading $url")
    val tmp = File(cronetAar.parentFile, cronetAar.name + ".part")
    URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
    tmp.renameTo(cronetAar)
}

android {
    namespace = "app.corkboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.corkboard"
        minSdk = 29
        targetSdk = 35
        // CI passes -PappVersionCode / -PappVersionName, derived from the workflow run number so
        // every channel sits on one rising line. Local builds stay at 1 on purpose: lower than any
        // published build, so a phone that had a dev build can always take a real one.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.1.0-dev"
        // Every phone this targets is 64-bit ARM; one ABI keeps Cronet at ~6 MB instead of ~22 MB.
        // Pass -Pabis=arm64-v8a,x86_64 to also run on an x86 emulator.
        ndk {
            abiFilters += ((project.findProperty("abis") as String?) ?: "arm64-v8a").split(",")
        }
        // The user agent claims exactly the Chrome whose network stack is in the APK.
        buildConfigField("String", "CRONET_VERSION", "\"$cronetVersion\"")
    }

    // Release signing comes from the environment (CI secrets, or exported by hand). Without it the
    // release build falls back to the debug key, which still installs for testing but cannot
    // update a properly signed copy.
    //   CORKBOARD_KEYSTORE_PATH / CORKBOARD_KEYSTORE_PASSWORD / CORKBOARD_KEY_ALIAS
    signingConfigs {
        create("releaseFromEnv") {
            val path = System.getenv("CORKBOARD_KEYSTORE_PATH")
            if (!path.isNullOrBlank() && File(path).exists()) {
                storeFile = File(path)
                storePassword = System.getenv("CORKBOARD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CORKBOARD_KEY_ALIAS") ?: "corkboard"
                keyPassword = System.getenv("CORKBOARD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val fromEnv = signingConfigs.getByName("releaseFromEnv")
            signingConfig = if (fromEnv.storeFile?.exists() == true) fromEnv else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(files(cronetAar))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Runs the saved-search checks in the background. No Google services involved.
    implementation(libs.androidx.work.runtime)
    // Hands replying, posting and account pages to the browser as a Custom Tab.
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Decodes and caches images; the bytes themselves are fetched through Cronet (net/Images.kt).
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
