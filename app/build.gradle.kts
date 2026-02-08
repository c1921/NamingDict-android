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

private val gitDescribePattern = Regex(
    """^v(\d+\.\d+\.\d+)(?:-(\d+)-g([0-9a-fA-F]+))?(-dirty)?$"""
)

fun parseGitDescribeToVersionName(raw: String): String? {
    val describe = raw.trim()
    val match = gitDescribePattern.matchEntire(describe) ?: return null
    val baseVersion = match.groupValues[1]
    val commitDistance = match.groupValues[2].takeIf { it.isNotBlank() }
    val shortSha = match.groupValues[3].takeIf { it.isNotBlank() }?.lowercase()
    val isDirty = match.groupValues[4].isNotBlank()

    return if (commitDistance != null && shortSha != null) {
        "$baseVersion-dev.$commitDistance+$shortSha" + if (isDirty) ".dirty" else ""
    } else if (isDirty) {
        "$baseVersion-dirty"
    } else {
        baseVersion
    }
}

fun Project.deriveVersionNameFromGit(): String? {
    val process = runCatching {
        ProcessBuilder(
            "git",
            "describe",
            "--tags",
            "--match",
            "v[0-9]*",
            "--dirty",
            "--abbrev=7"
        )
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
    }.getOrNull() ?: return null

    val output = runCatching {
        process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    }.getOrElse {
        process.destroyForcibly()
        return null
    }

    val exitCode = runCatching { process.waitFor() }.getOrNull() ?: return null
    if (exitCode != 0) return null

    return parseGitDescribeToVersionName(output)
}

val releaseVersionName = readConfig("RELEASE_VERSION_NAME")
    ?: deriveVersionNameFromGit()
    ?: "0.0.0-dev"
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
        buildConfig = true
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
    implementation(libs.androidx.security.crypto)
    implementation(libs.squareup.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("printResolvedVersion") {
    group = "help"
    description = "Print resolved app versionName and versionCode."
    doLast {
        println("releaseVersionName=$releaseVersionName")
        println("releaseVersionCode=$releaseVersionCode")
    }
}
