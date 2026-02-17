plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.warrantykeeper"
    compileSdk = 35  // ✅ ИЗМЕНЕНО: 34 → 35

    defaultConfig {
        applicationId = "com.warrantykeeper"
        minSdk = 26
        targetSdk = 35  // ✅ ИЗМЕНЕНО: 34 → 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")  // ✅ ИЗМЕНЕНО
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")  // ✅ ИЗМЕНЕНО
    implementation("androidx.activity:activity-compose:1.9.3")  // ✅ ИЗМЕНЕНО

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))  // ✅ ИЗМЕНЕНО
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")  // ✅ ИЗМЕНЕНО

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.1")  // ✅ ИЗМЕНЕНО
    implementation("androidx.camera:camera-lifecycle:1.4.1")  // ✅ ИЗМЕНЕНО
    implementation("androidx.camera:camera-view:1.4.1")  // ✅ ИЗМЕНЕНО

    // Room
    implementation("androidx.room:room-runtime:2.6.1")  // ✅ ИЗМЕНЕНО
    implementation("androidx.room:room-ktx:2.6.1")  // ✅ ИЗМЕНЕНО
    ksp("androidx.room:room-compiler:2.6.1")  // ✅ ИЗМЕНЕНО

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")  // ✅ ИЗМЕНЕНО

    // Google ML Kit - OCR (БЕСПЛАТНО!)
    implementation("com.google.mlkit:text-recognition:16.0.1")  // ✅ ИЗМЕНЕНО

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")  // ✅ ИЗМЕНЕНО

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))  // ✅ ИЗМЕНЕНО
    implementation("com.google.firebase:firebase-auth-ktx")

    // Hilt - ✅ КРИТИЧЕСКИ ВАЖНО!
    implementation("com.google.dagger:hilt-android:2.52")  // ✅ ИЗМЕНЕНО
    ksp("com.google.dagger:hilt-android-compiler:2.52")  // ✅ ИЗМЕНЕНО
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")  // ✅ ИЗМЕНЕНО
    implementation("androidx.hilt:hilt-work:1.2.0")  // ✅ ИЗМЕНЕНО
    ksp("androidx.hilt:hilt-compiler:1.2.0")  // ✅ ИЗМЕНЕНО

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")  // ✅ ИЗМЕНЕНО

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")  // ✅ ИЗМЕНЕНО

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")  // ✅ ИЗМЕНЕНО

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")  // ✅ ИЗМЕНЕНО

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")  // ✅ ИЗМЕНЕНО
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")  // ✅ ИЗМЕНЕНО
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))  // ✅ ИЗМЕНЕНО
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}