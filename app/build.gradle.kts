import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the run number so every release installs over the previous one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Release signing comes from repository secrets (see "Release signing" in the
// README). Without them the release is signed with the debug key.
val signingKeystore = System.getenv("SIGNING_KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }?.let {
    val file = layout.buildDirectory.file("signing/release.jks").get().asFile
    file.parentFile.mkdirs()
    file.writeBytes(Base64.getMimeDecoder().decode(it))
    file
}

android {
    namespace = "ca.danielpower.jstudy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crispyumbrella.jiwajiwa"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"

        // The story server and the Google OAuth web client it accepts sign-ins for
        // (see server/README.md). Neither is secret. Blank leaves stories unavailable.
        buildConfigField("String", "STORY_SERVER_URL", "\"${providers.gradleProperty("storyServerUrl").getOrElse("")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("googleWebClientId").getOrElse("")}\"")
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = signingKeystore
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "jstudy"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        // Pull request builds install next to the release app, so trying one doesn't touch your progress.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Both Kuromoji jars carry the same license and contributor notes.
            pickFirsts += setOf("META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md")
        }
    }

}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Sign in with Google, for the story server.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Unit tests run on the JVM; Robolectric stands in for Android (SQLite, Context).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
