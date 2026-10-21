import java.util.Base64
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// release.yml sets the version (APP_VERSION_CODE keeps rising past every upload to
// Play); other builds go by their run number.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val appVersionCode = System.getenv("APP_VERSION_CODE")?.takeIf { it.isNotBlank() }?.toInt() ?: buildNumber
val appVersionName = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.$buildNumber"

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
        versionCode = appVersionCode
        versionName = appVersionName
        // Where the stories' recordings are downloaded from (the Spaces bucket's CDN), set by CI.
        // Without it the app reads stories in the phone's voice.
        buildConfigField("String", "AUDIO_URL", "\"${System.getenv("AUDIO_URL").orEmpty()}\"")
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

// The stories (assets' stories/: path.json, needs.json, read/, each
// chapter split into words, and lines.json, where each grammar topic is
// used) are written from tools/stories by
// :core:storyAssets on each build, not committed. Like the app itself, that
// needs the dictionary built.
abstract class StoryAssets : DefaultTask() {
    @get:InputFiles
    abstract val stories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            from(stories)
            into(assets.dir("stories"))
        }
    }
}

val storyAssets = tasks.register<StoryAssets>("storyAssets") {
    stories.from(project(":core").layout.buildDirectory.dir("generated/storyAssets"))
    dependsOn(":core:storyAssets")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(storyAssets, StoryAssets::assets)
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Plays the stories' recordings (Ogg Opus, which MediaPlayer can't before Android 10).
    implementation("androidx.media3:media3-exoplayer:1.6.1")

    // Unit tests run on the JVM; Robolectric stands in for Android (SQLite, Context).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
