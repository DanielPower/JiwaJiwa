plugins {
    id("org.jetbrains.kotlin.jvm")
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
    // Japanese tokenizer for reading stories (IPADIC dictionary bundled in the jar).
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    // JSON for Claude API requests. The Anthropic Java SDK pulls in Jackson,
    // Apache HttpClient and JSON-schema tooling, too heavy for the APK.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}
