plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun Project.readConfig(key: String): String? {
    val envValue = System.getenv(key)?.takeIf { it.isNotBlank() }
    val propValue = providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
    return envValue ?: propValue
}

val releaseVersionName = readConfig("RELEASE_VERSION_NAME") ?: "1.0"
val releaseVersionCode = readConfig("RELEASE_VERSION_CODE")?.toIntOrNull() ?: 1

val keystorePath = readConfig("ANDROID_KEYSTORE_PATH")
val keystorePassword = readConfig("ANDROID_KEYSTORE_PASSWORD")
val signingKeyAlias = readConfig("ANDROID_KEY_ALIAS")
val signingKeyPassword = readConfig("ANDROID_KEY_PASSWORD")

val hasSigningInputs = listOf(
    keystorePath,
    keystorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

val isCi = ((System.getenv("CI") ?: providers.gradleProperty("CI").orNull) ?: "false")
    .equals("true", ignoreCase = true)

if (isCi && !hasSigningInputs) {
    throw GradleException(
        "Missing Android release signing config in CI. " +
            "Required keys: ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
            "ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD"
    )
}

android {
    namespace = "io.github.c1921.namingdict"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.github.c1921.namingdict"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningInputs) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningInputs) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
