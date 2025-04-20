plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.haybeat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.haybeat"
        minSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.0.0")) // Check for latest BOM
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

// Android Jetpack - Core
    implementation("androidx.core:core-ktx:1.13.1") // Check latest
    implementation("androidx.appcompat:appcompat:1.6.1") // Check latest
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Check latest
    implementation("com.google.android.material:material:1.12.0") // Check latest

// Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7") // Check latest
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7") // Check latest

// ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0") // Check latest
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0") // Check latest
    implementation("androidx.activity:activity-ktx:1.9.0") // For by viewModels()
    implementation("androidx.fragment:fragment-ktx:1.7.1") // For by viewModels()
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // Check latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Check latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3") // For Firebase tasks
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    implementation("androidx.preference:preference-ktx:1.2.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7") // Check latest
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")     // Check latest

}