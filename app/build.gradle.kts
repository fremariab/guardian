plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.safemonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.safemonitor"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        val mapsKey: String = (project.properties["MAPS_KEY"] as? String).orEmpty()
        buildConfigField(
            "String",          // the generated type
            "MAPS_API_KEY",    // the generated constant’s name
            "\"$mapsKey\""     // must be a *quoted* literal
        )
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding =true
        // If you want to keep the <layout> wrapper for data-binding features,
        // you can also enable dataBinding:
        //noinspection DataBindingWithoutKapt
        dataBinding= true
        buildConfig  = true
    }
}
dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation (libs.tensorflow.lite)
    implementation ("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation ("com.github.wendykierp:JTransforms:3.1" )
    implementation ("com.google.mlkit:face-detection:16.1.7")
    implementation ("androidx.core:core-ktx:1.9.0")
    implementation( "androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.maps.android:maps-compose:2.11.4")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // Google Places Autocomplete SDK
    implementation("com.google.android.libraries.places:places:3.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation ("com.google.maps.android:android-maps-utils:2.2.3")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.json:json:20231013")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
    // Location Services
    implementation("com.google.android.gms:play-services-location:21.2.0")
}
