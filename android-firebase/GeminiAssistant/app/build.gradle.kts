plugins {
    alias(libs.plugins.android.application) // Apply Android App plugin
    alias(libs.plugins.kotlin.android)     // Apply Kotlin Android plugin
    alias(libs.plugins.kotlin.serialization) // Apply Serialization plugin
    alias(libs.plugins.kotlin.compose) // Apply Compose plugin if needed
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.example.geminiassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.geminiassistant"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging { // Find this block (or add it if missing)
        resources {
            // Add or modify the excludes line like this:
            excludes += setOf(
                "META-INF/AL2.0",      // Keep previous exclusions if they were there
                "META-INF/LGPL2.1",    // Keep previous exclusions if they were there
                "META-INF/INDEX.LIST",  // Add the specific file causing the error
                 "META-INF/DEPENDENCIES",
                 "META-INF/LICENSE.txt",
                 "META-INF/NOTICE.txt"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}
dependencies {
    // Core Android & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended) // Optional, but recommended

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Use latest version

    // Gemini API (Vertex AI SDK) - Make sure you have the BOM
    // Check for the latest versions: https://cloud.google.com/vertex-ai/docs/generative-ai/start/quickstarts/quickstart-multimodal#install_the_sdk
//    implementation(platform("com.google.cloud:libraries-bom:26.32.0")) // Use the latest BOM version
//    implementation("com.google.cloud:google-cloud-vertexai")

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing (Optional but recommended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Add gRPC OkHttp channel provider
    implementation(libs.grpc.okhttp)

    implementation(libs.protobuf.java.util)

    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-vertexai")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-auth")
}