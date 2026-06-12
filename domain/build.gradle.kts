// The domain layer as its own Gradle module: pure Kotlin/JVM, no Android.
// The module boundary *enforces* what was previously just a convention —
// nothing in here can touch Room, Compose, or the framework.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // JSR-330 annotations only (@Inject); Hilt wiring stays in :app.
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
