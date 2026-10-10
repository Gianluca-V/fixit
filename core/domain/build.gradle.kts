import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: no Android, Hilt, Compose or Firebase.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject) // only the @Inject annotation

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.junit)
}
