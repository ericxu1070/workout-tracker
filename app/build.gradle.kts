plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "dev.gzclp.tracker"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.gzclp.tracker"
        minSdk = 31
        targetSdk = 35
        // The release workflow sets these from the tag and the commit count; local builds keep the defaults.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 2
        versionName = System.getenv("VERSION_NAME") ?: "2.0"
    }
    // Only when RELEASE_KEYSTORE is set (CI, or a local signed build); otherwise release builds stay unsigned.
    System.getenv("RELEASE_KEYSTORE")?.let { store ->
        signingConfigs.create("release") {
            storeFile = file(store)
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    // shortcut: last release that builds with compileSdk 35; move to 1.1.0 stable once android-36 is installed and compileSdk is 36.
    implementation("androidx.health.connect:connect-client:1.1.0-beta01")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.78.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.78.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.withType<Test>().configureEach {
    systemProperty("gzclp.shots", providers.gradleProperty("gzclpShots").getOrElse(layout.buildDirectory.dir("shots").get().asFile.path))
}
