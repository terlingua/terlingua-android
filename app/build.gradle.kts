plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    id("terlingua.content")
}

val keystoreFile = providers.environmentVariable("TERLINGUA_KEYSTORE_FILE")
val keystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD")
val signingKeyAlias = providers.environmentVariable("KEY_ALIAS")
val signingKeyPassword = providers.environmentVariable("KEY_PASSWORD")

android {
    namespace = "io.github.terlingua.books"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.terlingua.books"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("terlingua.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("terlingua.versionName").getOrElse("0.0.0-dev")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val file = keystoreFile.orNull
        val storePass = keystorePassword.orNull
        val alias = signingKeyAlias.orNull
        val keyPass = signingKeyPassword.orNull
        if (file != null && storePass != null && alias != null && keyPass != null) {
            create("release") {
                storeFile = file(file)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // The dependency-metadata blob is encrypted for Google Play only; omit it for reproducible, transparent APKs.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.compose.ui.core)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.core)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material3.adaptive.core)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    ksp(libs.androidx.room3.compiler)

    debugImplementation(libs.compose.ui.tooling.core)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.sqlite.jdbc)
}
