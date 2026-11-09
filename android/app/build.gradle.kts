plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.chesshelper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chesshelper.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            abiFilters += listOf("arm64-v8a") // S26 Ultra Snapdragon 8 Elite / Exynos 2600
        }
    }

    // ——— Signing ———
    // release.keystore is a PKCS12 keystore generated via:
    //   openssl req -x509 -newkey rsa:2048 -nodes -subj "/CN=Chess Helper/C=SA" -keyout k -out c && openssl pkcs12 -export -out release.keystore -inkey k -in c -name chesshelper -passout pass:chesshelper
    // Passwords are intentionally in-repo for sideload convenience (not for Play Store). Replace with your own for production.
    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "chesshelper"
            keyAlias = "chesshelper"
            keyPassword = "chesshelper"
            // gradle will auto-detect PKCS12 type (openssl). If you re-generate as JKS, add: storeType = "PKCS12"
        }
        // debug uses default ~/.android/debug.keystore (auto-created)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            // S26 Ultra: R8 fullMode already enabled in gradle.properties
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // debug is auto-signed via debug keystore — installs instantly on S26 Ultra
        }
    }

    // ——— Flavors: helper (transparent) vs chess (exact clone) ———
    // User wants "same like chess app excact" → install the `chess` variant.
    // It uses the official Chess.com green icon + app name "Chess" + package "com.chess"
    // so it looks identical in launcher / recent apps / Settings until shake/3-finger reveals overlay.
    flavorDimensions += "mode"
    productFlavors {
        create("helper") {
            dimension = "mode"
            applicationId = "com.chesshelper.app"
            // uses @drawable/ic_launcher (green pawn) + "Chess Helper"
            buildConfigField("boolean", "IS_STEALTH", "false")
            resValue("string", "flavor_name", "Chess Helper")
        }
        create("chess") {
            dimension = "mode"
            // Exact clone: same package as official Chess.com → appears as "Chess" in launcher, replaces/looks like official.
            // If you already have Chess.com installed, use `helper` or change this to "com.chesshelper.stealth".
            applicationId = "com.chess"
            versionNameSuffix = "-chess"
            buildConfigField("boolean", "IS_STEALTH", "true")
            resValue("string", "flavor_name", "Chess")
            // Note: icon override lives in src/chess/res/drawable/ic_launcher.png + src/chess/res/values/strings.xml
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.github.bhlangonijr:chesslib:1.3.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}
