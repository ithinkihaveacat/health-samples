plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.healthservicesclient"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    // Use the official proto definitions to satisfy the serialization requirements.
    // We'll use 1.0.0-rc02 or 1.1.0-alpha02. Let's use 1.1.0-alpha02 as we are pulling from androidx-main.
    implementation("androidx.health:health-services-client-proto:1.1.0-rc01")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    
    // Futures
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // Annotations
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("org.jspecify:jspecify:1.0.0")

    // Guava
    implementation("com.google.guava:guava:33.3.1-android")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
}
