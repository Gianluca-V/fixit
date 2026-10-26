plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp) // Hilt for MapPickerViewModel (icon-map-address D4)
}

android {
    namespace = "com.vespelabs.fixit.core.ui"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose) // Photo Picker and camera launchers
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp) // loads Cloudinary https URLs
    implementation(libs.maplibre.android.sdk) // map picker with OpenStreetMap data (icon-map-address D1)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // The map picker has its own ViewModel (icon-map-address D4); still no dependency on :core:data.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:domain")))
}
