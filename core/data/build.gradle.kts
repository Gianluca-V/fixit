import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// Cloudinary keys (D22): read from the git-ignored local.properties; empty when missing so the build never breaks.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun localProperty(key: String): String = localProperties.getProperty(key, "").trim()

android {
    namespace = "com.vespelabs.fixit.core.data"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${localProperty("CLOUDINARY_CLOUD_NAME")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${localProperty("CLOUDINARY_UPLOAD_PRESET")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        // Firebase exception constructors touch android.text.TextUtils / android.util.SparseArray; the android.jar
        // stubs would throw "not mocked" in JVM tests (FirebaseCallsTest). Default values keep them constructible.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    // Phase 2: Firebase (Auth + Firestore, no Storage) and Cloudinary uploads over OkHttp (D21).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.geofire.android.common)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.exifinterface)

    // Phase 3: real location (Fused provider; the Geocoder is part of the framework).
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:domain")))
}
