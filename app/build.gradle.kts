import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val sharedSigningConfig = if (signingPropertiesFile.isFile) {
    val signingProperties = Properties().apply {
        signingPropertiesFile.inputStream().use(::load)
    }
    fun signingProperty(name: String): String =
        signingProperties.getProperty(name)
            ?: error("Missing signing property: $name")

    signingConfigs.create("shared") {
        storeFile = rootProject.file(signingProperty("storeFile"))
        storePassword = signingProperty("storePassword")
        keyAlias = signingProperty("keyAlias")
        keyPassword = signingProperty("keyPassword")
    }
} else {
    null
}

android {
    namespace = "cz.misa.quakedeck"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.misa.quakedeck"
        minSdk = 26
        targetSdk = 36
        versionCode = 111
        versionName = "0.9.75b"
    }

    buildTypes {
        getByName("debug") {
            sharedSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            sharedSigningConfig?.let { signingConfig = it }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}