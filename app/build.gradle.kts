import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "universal" to 3)

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStorePath: String? = signingValue("storeFile", "KEYSTORE_PATH")
val hasReleaseKeystore: Boolean =
    releaseStorePath != null && rootProject.file(releaseStorePath).exists()

val ciKeystoreB64 = rootProject.file(".github/ci-keystore.jks.b64")
val useCiKeystore: Boolean = !hasReleaseKeystore && ciKeystoreB64.exists()
val ciKeystoreFile = rootProject.file("build/ci-release.keystore")
if (useCiKeystore) {
    ciKeystoreFile.parentFile.mkdirs()
    ciKeystoreFile.writeBytes(
        Base64.getMimeDecoder().decode(ciKeystoreB64.readText().trim()),
    )
}

android {
    namespace = "com.najishab.aether"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.najishab.aether"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        ndk {
            // We ship arm64 (primary) and arm builds.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        val githubRepo = System.getenv("GITHUB_REPOSITORY")
            ?: (project.findProperty("githubRepo") as? String)
            // Fallback so local/dev builds (no CI env var) still know the
            // repo slug - needed for the in-app Changelog's GitHub check.
            ?: "najishab/aether"
        val releasesUrl = "https://github.com/$githubRepo/releases/latest"
        buildConfigField("String", "RELEASES_URL", "\"$releasesUrl\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        // In-app announcements: raw JSON array read straight off the default
        // branch (docs/announcements.json), same repo as the changelog uses.
        // No GitHub auth needed - it's a public raw file, not the API.
        val announcementsUrl = "https://raw.githubusercontent.com/$githubRepo/main/docs/announcements.json"
        buildConfigField("String", "ANNOUNCEMENTS_URL", "\"$announcementsUrl\"")

        // Aether engine (core) version compiled into this build. CI keeps this
        // in sync with native/aether/CORE_VERSION via scripts/sync-core.sh.
        val coreVersion = rootProject.file("native/aether/CORE_VERSION")
            .takeIf { it.exists() }?.readText()?.trim().orEmpty().ifBlank { "unknown" }
        buildConfigField("String", "CORE_VERSION", "\"$coreVersion\"")

        val mixpanelToken = localProps.getProperty("mixpanel.token")
            ?: System.getenv("MIXPANEL_TOKEN")
            ?: ""
        buildConfigField("String", "MIXPANEL_TOKEN", "\"$mixpanelToken\"")
    }



    signingConfigs {
        create("release") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            } else if (useCiKeystore) {
                storeFile = ciKeystoreFile
                storePassword = "aether-ci-keystore"
                keyAlias = "aether-ci"
                keyPassword = "aether-ci-keystore"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDefault = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore || useCiKeystore) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
            isDefault = false
        }
    }

    // Produce one APK per ABI + a universal one -> exactly the 3 release files.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
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

    packaging {
        // IMPORTANT: extract native libs on install so the bundled `aether` and
        // `hev` executables live on disk in nativeLibraryDir and can be exec()'d.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
if (!hasReleaseKeystore && !useCiKeystore) {
    tasks.configureEach {
        if (name.contains("Release") &&
            (name.startsWith("assemble") || name.startsWith("package") || name.startsWith("bundle"))
        ) {
            doFirst {
                throw GradleException(
                    "No stable release keystore configured"
                )
            }
        }
    }
}

// Bundles the CURRENT release's notes (English + Persian) into the app so
// the in-app Changelog screen has something to show with zero network
// calls. This file is the single source of truth CI already writes/uses as
// the GitHub release body for every release - copying it here (instead of
// hand-maintaining a second copy) means it can never drift out of sync.
val copyReleaseNotes by tasks.registering(Copy::class) {
    from(rootProject.file(".github/release-notes.md"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(copyReleaseNotes) }

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName = output.filters.find { it.filterType == ABI }?.identifier
            val base = (android.defaultConfig.versionCode ?: 1) * 1000
            val offset = abiCodes[abiName ?: "universal"] ?: 0
            output.versionCode.set(base + offset)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Mixpanel Analytics: user stats screen in the More panel.
    implementation("com.mixpanel.android:mixpanel-android:8.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}